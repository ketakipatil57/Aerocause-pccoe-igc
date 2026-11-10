package com.aerocause.service.replay;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.ingestion.IngestionPipeline;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Episode replay service.
 *
 * <p>Reads synthetic CSV data from /data/episodes/ and feeds it through the
 * SAME pipeline as live data: ingestion -> QC -> anomaly detection -> fingerprinting.
 *
 * <p>The speedMultiplier controls simulated time compression:
 * speedMultiplier=60 means 1 real second = 60 simulated seconds.
 *
 * <p>All replayed data is marked synthetic=true and labelled "SAMPLE DATA (synthetic)".
 * Re-running a replay is idempotent (upsert on station+pollutant+observed_at).
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final EpisodeRepository episodeRepo;
    private final StationRepository stationRepo;
    private final IngestionPipeline pipeline;
    private final AuditEventRepository auditRepo;
    private final ObjectMapper objectMapper;

    // Replay state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Long currentEpisodeId = null;
    private final AtomicInteger readingsProcessed = new AtomicInteger(0);
    private final AtomicInteger incidentsCreated  = new AtomicInteger(0);
    private volatile double progress = 0.0;
    private volatile Thread replayThread = null;
    private volatile String lastError = null;

    public ReplayService(EpisodeRepository episodeRepo,
                         StationRepository stationRepo,
                         IngestionPipeline pipeline,
                         AuditEventRepository auditRepo,
                         ObjectMapper objectMapper) {
        this.episodeRepo = episodeRepo;
        this.stationRepo = stationRepo;
        this.pipeline = pipeline;
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    public record ReplayStatus(
            boolean running,
            Long episodeId,
            double progress,
            int readingsProcessed,
            int incidentsCreated,
            String message,
            String error
    ) {}

    public ReplayStatus getStatus() {
        return new ReplayStatus(
                running.get(), currentEpisodeId, progress,
                readingsProcessed.get(),
                incidentsCreated.get(),
                running.get() ? "Replay in progress" : "No replay running",
                lastError
        );
    }

    /**
     * Start replaying an episode. Runs in a background thread.
     * Returns immediately — use getStatus() to poll progress.
     */
    public ReplayStatus start(Long episodeId, int speed) {
        if (episodeId == null) {
            throw new IllegalArgumentException("episodeId is required");
        }
        if (speed < 1) {
            throw new IllegalArgumentException("speedMultiplier must be at least 1");
        }
        if (!running.compareAndSet(false, true)) {
            return new ReplayStatus(true, currentEpisodeId, progress,
                    readingsProcessed.get(), incidentsCreated.get(),
                    "Replay already running. Stop it first.", lastError);
        }

        Episode episode;
        try {
            episode = episodeRepo.findById(episodeId)
                    .orElseThrow(() -> new IllegalArgumentException("Episode not found: " + episodeId));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        currentEpisodeId = episodeId;
        readingsProcessed.set(0);
        incidentsCreated.set(0);
        progress = 0.0;
        lastError = null;

        try {
            saveAudit("REPLAY_STARTED", Map.of(
                    "episodeId", episodeId,
                    "speedMultiplier", speed));
            replayThread = new Thread(() -> doReplay(episode, speed), "replay-" + episodeId);
            replayThread.setDaemon(true);
            replayThread.start();
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        return getStatus();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (replayThread != null) replayThread.interrupt();
        saveAudit("REPLAY_STOPPED", Collections.singletonMap("episodeId", currentEpisodeId));
    }

    // -------------------------------------------------------------------------
    // Core replay logic
    // -------------------------------------------------------------------------

    private void doReplay(Episode episode, int speed) {
        try {
            String readingsFile = episode.getReadingsFile();
            String weatherFile  = episode.getWeatherFile();

            if (readingsFile == null || readingsFile.isBlank()) {
                throw new IOException("Episode " + episode.getId() + " has no readings_file configured");
            }

            List<Map<String, String>> readingRows = readCsv(resolveDataPath(readingsFile));
            if (readingRows.isEmpty()) {
                throw new IOException("Episode " + episode.getId() + " readings file contains no data");
            }
            List<Map<String, String>> weatherRows = weatherFile != null
                    ? readCsv(resolveDataPath(weatherFile))
                    : Collections.emptyList();

            // Group readings by station
            Map<String, List<Map<String, String>>> byStation = readingRows.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getOrDefault("station_external_id", ""),
                            TreeMap::new, Collectors.toList()));

            // Group weather by station
            Map<String, List<Map<String, String>>> weatherByStation = weatherRows.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getOrDefault("station_external_id", ""),
                            TreeMap::new, Collectors.toList()));

            // Collect all unique timestamps across all stations (sorted)
            List<Instant> allTimestamps = readingRows.stream()
                    .map(r -> r.getOrDefault("observed_at", ""))
                    .filter(s -> !s.isBlank())
                    .map(this::parseTimestamp)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            int total = allTimestamps.size();
            if (total == 0) {
                throw new IOException("No valid reading timestamps found for episode " + episode.getId());
            }

            // Replay one timestamp slice at a time
            for (int i = 0; i < allTimestamps.size() && running.get(); i++) {
                Instant ts = allTimestamps.get(i);
                progress = (double) i / total;

                // For each station, ingest all readings at this timestamp
                for (Map.Entry<String, List<Map<String, String>>> stationEntry : byStation.entrySet()) {
                    String extId = stationEntry.getKey();
                    Station station = stationRepo.findByExternalId(extId).orElse(null);
                    if (station == null) {
                        log.warn("Station not found for externalId={}", extId);
                        continue;
                    }

                    // Readings at this timestamp
                    List<Reading> readingsBatch = stationEntry.getValue().stream()
                            .filter(r -> ts.equals(parseTimestamp(r.getOrDefault("observed_at", ""))))
                            .map(r -> parseReading(r, station))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    // All readings up to this timestamp for this station (for QC window)
                    List<Reading> readingsWindow = stationEntry.getValue().stream()
                            .filter(r -> {
                                try {
                                    Instant rts = parseTimestamp(r.getOrDefault("observed_at", ""));
                                    if (rts == null) return false;
                                    return !rts.isAfter(ts);
                                } catch (DateTimeParseException e) { return false; }
                            })
                            .map(r -> parseReading(r, station))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    List<WeatherObs> weatherBatch = weatherByStation
                            .getOrDefault(extId, Collections.emptyList()).stream()
                            .filter(r -> ts.equals(parseTimestamp(r.getOrDefault("observed_at", ""))))
                            .map(r -> parseWeather(r, station))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!readingsBatch.isEmpty() && !readingsWindow.isEmpty()) {
                        String rawJson = toJson(Map.of(
                                "source", "SYNTHETIC_REPLAY",
                                "episodeId", episode.getId(),
                                "timestamp", ts.toString(),
                                "station", extId,
                                "readingCount", readingsWindow.size()
                        ));
                        int created = pipeline.ingest(station, readingsWindow, weatherBatch,
                                episode.getId(), rawJson);
                        incidentsCreated.addAndGet(created);
                        readingsProcessed.addAndGet(readingsBatch.size());
                    }
                }
                progress = (double) (i + 1) / total;

                // Simulate time compression
                boolean warmup = episode.getStartTime() != null
                        && ts.isBefore(episode.getStartTime());
                if (!warmup && speed > 0 && i < allTimestamps.size() - 1) {
                    Instant next = allTimestamps.get(i + 1);
                    long realGapMs = (next.toEpochMilli() - ts.toEpochMilli()) / speed;
                    if (realGapMs > 50) {
                        try { Thread.sleep(Math.min(realGapMs, 2000)); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }

            if (running.get()) {
                progress = 1.0;
                log.info("Replay complete: episode={}, readings={}, incidents={}",
                        episode.getId(), readingsProcessed.get(), incidentsCreated.get());
                saveAudit("REPLAY_COMPLETE", Map.of(
                        "episodeId", episode.getId(),
                        "readingsProcessed", readingsProcessed.get(),
                        "incidentsCreated", incidentsCreated.get()));
            }

        } catch (Exception e) {
            log.error("Replay failed for episode {}", currentEpisodeId, e);
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("episodeId", currentEpisodeId);
            failure.put("error", lastError);
            try {
                saveAudit("REPLAY_FAILED", failure);
            } catch (RuntimeException auditError) {
                log.error("Could not save replay failure audit event", auditError);
            }
        } finally {
            running.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // CSV parsing helpers
    // -------------------------------------------------------------------------

    private List<Map<String, String>> readCsv(String filePath) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("CSV file not found: " + filePath);
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine == null) return rows;
            String[] headers = headerLine.replace("\uFEFF", "").split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private Reading parseReading(Map<String, String> row, Station station) {
        try {
            return Reading.builder()
                    .station(station)
                    .pollutant(row.get("pollutant"))
                    .value(Double.parseDouble(row.get("value")))
                    .unit(row.getOrDefault("unit", "µg/m³"))
                    .observedAt(Instant.parse(row.get("observed_at")))
                    .fetchedAt(Instant.now())
                    .source(row.getOrDefault("source", "SYNTHETIC"))
                    .qualityStatus(Reading.QualityStatus.GOOD)
                    .build();
        } catch (Exception e) {
            log.warn("Skipping malformed reading row: {}", row, e);
            return null;
        }
    }

    private WeatherObs parseWeather(Map<String, String> row, Station station) {
        try {
            String blhStr = row.getOrDefault("boundary_layer_height", "").trim();
            Double blh = blhStr.isBlank() ? null : Double.parseDouble(blhStr);
            String hotspot = row.getOrDefault("fire_hotspot_upwind", "").trim();
            return WeatherObs.builder()
                    .station(station)
                    .observedAt(Instant.parse(row.get("observed_at")))
                    .windSpeed(parseDouble(row.get("wind_speed")))
                    .windDirDeg(parseDouble(row.get("wind_dir_deg")))
                    .boundaryLayerHeight(blh)
                    .fireHotspotUpwind(hotspot.isBlank() ? null : Boolean.parseBoolean(hotspot))
                    .source(row.getOrDefault("source", "SYNTHETIC"))
                    .build();
        } catch (Exception e) {
            log.warn("Skipping malformed weather row: {}", row, e);
            return null;
        }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) {
            log.warn("Skipping malformed numeric weather value: {}", s, e);
            return null;
        }
    }

    private String resolveDataPath(String relativePath) {
        // Try relative to working directory, then relative to project root
        String[] candidates = {
                relativePath,
                "../data/" + relativePath.replaceFirst("^episodes/", "episodes/"),
                "data/" + relativePath
        };
        for (String candidate : candidates) {
            if (new File(candidate).exists()) return candidate;
        }
        return relativePath;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize replay payload", e);
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("Skipping malformed replay timestamp: {}", value, e);
            return null;
        }
    }

    private void saveAudit(String eventType, Map<String, Object> payload) {
        auditRepo.save(AuditEvent.builder()
                .eventType(eventType)
                .incidentId(null)
                .payloadJson(toJson(payload))
                .createdAt(Instant.now())
                .build());
    }
}
