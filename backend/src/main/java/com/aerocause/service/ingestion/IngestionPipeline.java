package com.aerocause.service.ingestion;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.AuditService;
import com.aerocause.service.anomaly.AnomalyDetectionService;
import com.aerocause.service.fingerprinting.FingerprintingInput;
import com.aerocause.service.fingerprinting.FingerprintingService;
import com.aerocause.service.qc.QcResult;
import com.aerocause.service.qc.QcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core ingestion pipeline.
 *
 * <p>Flow for each reading batch (live or replay):
 * <ol>
 *   <li>Store raw payload (always, for audit and re-run capability).</li>
 *   <li>Idempotent upsert readings (station + pollutant + observed_at).</li>
 *   <li>Upsert weather observations.</li>
 *   <li>QC rules -> qc_flags, quality_status on readings.</li>
 *   <li>Anomaly detection -> incidents (if threshold exceeded).</li>
 *   <li>Fingerprinting -> hypotheses, evidence_items.</li>
 * </ol>
 *
 * <p>The LLM is NOT called here. It is only called when
 * POST /api/incidents/{id}/investigate is triggered explicitly.
 */
@Service
public class IngestionPipeline {

    private final StationRepository stationRepo;
    private final ReadingRepository readingRepo;
    private final WeatherObsRepository weatherRepo;
    private final RawPayloadRepository rawPayloadRepo;
    private final QcService qcService;
    private final AnomalyDetectionService anomalyService;
    private final FingerprintingService fingerprintingService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AeroCauseProperties props;

    public IngestionPipeline(StationRepository stationRepo,
                             ReadingRepository readingRepo,
                             WeatherObsRepository weatherRepo,
                             RawPayloadRepository rawPayloadRepo,
                             QcService qcService,
                             AnomalyDetectionService anomalyService,
                             FingerprintingService fingerprintingService,
                             AuditService auditService,
                             ObjectMapper objectMapper,
                             AeroCauseProperties props) {
        this.stationRepo = stationRepo;
        this.readingRepo = readingRepo;
        this.weatherRepo = weatherRepo;
        this.rawPayloadRepo = rawPayloadRepo;
        this.qcService = qcService;
        this.anomalyService = anomalyService;
        this.fingerprintingService = fingerprintingService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    /**
     * Process a batch of readings through the full pipeline.
     *
     * @param station       the station
     * @param readings      time-ordered readings to ingest
     * @param weatherList   weather observations for the same window
     * @param episodeId     episode id if from replay; null for live
     * @param rawPayloadJson raw source JSON for audit storage
     */
    @Transactional
    public int ingest(Station station,
                      List<Reading> readings,
                      List<WeatherObs> weatherList,
                      Long episodeId,
                      String rawPayloadJson) {
        Objects.requireNonNull(station, "station must not be null");
        Objects.requireNonNull(readings, "readings must not be null");
        Objects.requireNonNull(weatherList, "weatherList must not be null");

        // 1. Store raw payload
        rawPayloadRepo.save(RawPayload.builder()
                .source(!readings.isEmpty() ? readings.get(0).getSource()
                        : !weatherList.isEmpty() ? weatherList.get(0).getSource() : "UNKNOWN")
                .fetchedAt(Instant.now())
                .payloadJson(rawPayloadJson != null ? rawPayloadJson : "{}")
                .build());

        // 2. Upsert readings
        List<Reading> persisted = new ArrayList<>();
        for (Reading r : readings) {
            Reading toSave = readingRepo
                    .findByStationAndPollutantAndObservedAt(station, r.getPollutant(), r.getObservedAt())
                    .orElseGet(() -> {
                        r.setStation(station);
                        r.setFetchedAt(Instant.now());
                        if (episodeId != null) r.setEpisodeId(episodeId);
                        return readingRepo.save(r);
                    });
            persisted.add(toSave);
        }

        // 3. Upsert weather
        for (WeatherObs w : weatherList) {
            weatherRepo.findByStationAndObservedAt(station, w.getObservedAt())
                    .orElseGet(() -> {
                        w.setStation(station);
                        return weatherRepo.save(w);
                    });
        }

        // 4. Group by pollutant and run QC + anomaly per pollutant
        Map<String, List<Reading>> byPollutant = persisted.stream()
                .collect(Collectors.groupingBy(
                        Reading::getPollutant, TreeMap::new, Collectors.toList()));

        int incidentsCreated = 0;
        Instant latestObservedAt = persisted.stream()
                .map(Reading::getObservedAt)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> weatherList.stream()
                        .map(WeatherObs::getObservedAt)
                        .max(Comparator.naturalOrder())
                        .orElse(Instant.now()));

        for (Map.Entry<String, List<Reading>> entry : byPollutant.entrySet()) {
            String pollutant = entry.getKey();
            List<Reading> pollReadings = entry.getValue().stream()
                    .sorted(Comparator.comparing(Reading::getObservedAt))
                    .collect(Collectors.toList());

            // 4a. QC
            Map<Long, Double> neighbourValues =
                    buildNeighbourPeaks(station, pollutant, latestObservedAt);
            QcResult qcResult = qcService.runQc(station, pollutant, pollReadings, neighbourValues);

            // Mark suspect
            if (qcResult.hasSeriousFlags()) {
                pollReadings.forEach(r -> {
                    r.setQualityStatus(Reading.QualityStatus.SUSPECT);
                    readingRepo.save(r);
                });
            }

            // 4b. Anomaly detection
            Optional<Incident> incidentOpt = anomalyService.detectAnomaly(
                    station, pollutant, pollReadings, qcResult.hasSeriousFlags(), episodeId);

            if (incidentOpt.isEmpty()) continue;
            Incident incident = incidentOpt.get();
            incidentsCreated++;

            // 4c. Build fingerprinting input
            Map<String, Double> peakValues = buildPeakValues(station, incident);
            Map<String, Double> baselineValues = Map.of(pollutant, incident.getBaselineValue());

            List<WeatherObs> weatherWindow = weatherRepo
                    .findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                            station, incident.getWindowStart(), incident.getWindowEnd());

            boolean fireHotspotEnabled = props.getFirms().isEnabled();
            boolean fireHotspotUpwind = weatherWindow.stream()
                    .anyMatch(w -> Boolean.TRUE.equals(w.getFireHotspotUpwind()));

            Map<String, Double> neighbourPm25 = new LinkedHashMap<>();
            buildNeighbourPeaks(station, "PM25", incident.getWindowEnd())
                    .forEach((sid, val) -> neighbourPm25.put(String.valueOf(sid), val));

            FingerprintingInput fpInput = new FingerprintingInput(
                    incident, peakValues, baselineValues, weatherWindow,
                    qcResult.flags(), neighbourPm25, fireHotspotEnabled, fireHotspotUpwind);

            // 4d. Fingerprinting
            fingerprintingService.fingerprint(fpInput);
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("episodeId", episodeId);
        auditPayload.put("readingCount", persisted.size());
        auditPayload.put("weatherCount", weatherList.size());
        auditPayload.put("incidentCount", incidentsCreated);
        auditService.log("INGESTION_COMPLETED", null, auditPayload);
        return incidentsCreated;
    }

    private Map<String, Double> buildPeakValues(Station station, Incident incident) {
        Map<String, Double> peaks = new LinkedHashMap<>();
        for (String p : new String[]{"PM25", "PM10", "NO2", "SO2", "CO", "O3"}) {
            readingRepo.findByStationAndPollutantAndObservedAtBetweenOrderByObservedAtAsc(
                            station, p, incident.getWindowStart(), incident.getWindowEnd())
                    .stream().mapToDouble(Reading::getValue).max()
                    .ifPresent(v -> peaks.put(p, v));
        }
        return peaks;
    }

    private Map<Long, Double> buildNeighbourPeaks(Station station, String pollutant, Instant observedAt) {
        Map<Long, Double> result = new LinkedHashMap<>();
        Instant twoHrsAgo = observedAt.minus(2, ChronoUnit.HOURS);
        for (Station s : stationRepo.findAll()) {
            if (s.getId().equals(station.getId())) continue;
            readingRepo.findByStationAndPollutantAndObservedAtBetweenOrderByObservedAtAsc(
                            s, pollutant, twoHrsAgo, observedAt)
                    .stream().mapToDouble(Reading::getValue).max()
                    .ifPresent(v -> result.put(s.getId(), v));
        }
        return result;
    }
}
