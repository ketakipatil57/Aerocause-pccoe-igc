package com.aerocause.service;

import com.aerocause.domain.Episode;
import com.aerocause.repository.EpisodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
public class EpisodeImportService {
    private static final Set<String> REQUIRED_READING_COLUMNS = Set.of(
            "station_external_id", "pollutant", "value", "observed_at");

    private final EpisodeRepository episodeRepo;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Path dataDirectory;

    public EpisodeImportService(
            EpisodeRepository episodeRepo,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${aerocause.data-dir:../data}") String dataDirectory) {
        this.episodeRepo = episodeRepo;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.dataDirectory = Path.of(dataDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public List<Map<String, Object>> importRealEpisodes() {
        Path realDirectory = dataDirectory.resolve("real").normalize();
        Path manifest = realDirectory.resolve("manifest.json");
        if (!manifest.startsWith(realDirectory) || !Files.isRegularFile(manifest)) {
            throw new IllegalStateException("Real-data manifest not found: " + manifest);
        }
        try {
            JsonNode root = objectMapper.readTree(manifest.toFile());
            JsonNode entries = root.isArray() ? root : root.path("episodes");
            if (!entries.isArray()) {
                throw new IllegalArgumentException("manifest.json must contain an episodes array.");
            }
            List<Map<String, Object>> imported = new ArrayList<>();
            for (JsonNode entry : entries) imported.add(importOne(entry, realDirectory));
            auditService.log("REAL_EPISODES_IMPORTED", null, Map.of(
                    "count", imported.size(),
                    "synthetic", false,
                    "manifest", manifest.toString()));
            return imported;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read real-data manifest: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> importOne(JsonNode entry, Path realDirectory) throws IOException {
        String name = requiredText(entry, "name");
        String readings = requiredText(entry, "readings_file");
        Path readingsPath = resolveInside(realDirectory, readings);
        validateReadingsFile(readingsPath);

        String storedReadingPath = "real/" + realDirectory.relativize(readingsPath)
                .toString().replace('\\', '/');
        String weather = optionalText(entry, "weather_file");
        String storedWeatherPath = null;
        if (weather != null) {
            Path weatherPath = resolveInside(realDirectory, weather);
            if (!Files.isRegularFile(weatherPath)) {
                throw new IllegalArgumentException("Weather CSV not found: " + weatherPath);
            }
            storedWeatherPath = "real/" + realDirectory.relativize(weatherPath)
                    .toString().replace('\\', '/');
        }

        Episode episode = episodeRepo.findByReadingsFile(storedReadingPath)
                .orElseGet(Episode::new);
        episode.setName(name);
        episode.setDescription(optionalText(entry, "description"));
        episode.setSynthetic(false);
        episode.setExpectedTopHypothesis(optionalText(entry, "expected_top_hypothesis"));
        episode.setExpectedFault(entry.has("expected_fault") && !entry.get("expected_fault").isNull()
                ? entry.get("expected_fault").asBoolean() : null);
        episode.setDurationHours(entry.has("duration_hours")
                ? entry.get("duration_hours").asInt() : null);
        episode.setStartTime(parseInstant(optionalText(entry, "start_time")));
        episode.setReadingsFile(storedReadingPath);
        episode.setWeatherFile(storedWeatherPath);
        episode = episodeRepo.save(episode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", episode.getId());
        result.put("name", episode.getName());
        result.put("synthetic", false);
        result.put("expectedTopHypothesis", episode.getExpectedTopHypothesis());
        result.put("expectedFault", episode.getExpectedFault());
        return result;
    }

    private Path resolveInside(Path root, String relative) throws IOException {
        Path requested = Path.of(relative);
        if (requested.isAbsolute()) {
            throw new IllegalArgumentException("Manifest file paths must be relative to data/real.");
        }
        Path resolved = root.resolve(requested).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("CSV file must exist under data/real: " + relative);
        }
        Path realRoot = root.toRealPath();
        if (!resolved.toRealPath().startsWith(realRoot)) {
            throw new IllegalArgumentException("Manifest file paths cannot escape data/real.");
        }
        return resolved;
    }

    private void validateReadingsFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) throw new IllegalArgumentException("Readings CSV is empty: " + file);
            Set<String> headers = new HashSet<>(Arrays.asList(
                    header.replace("\uFEFF", "").split(",")));
            if (!headers.containsAll(REQUIRED_READING_COLUMNS)) {
                throw new IllegalArgumentException("Readings CSV requires columns: "
                        + REQUIRED_READING_COLUMNS);
            }
        }
    }

    private static String requiredText(JsonNode entry, String key) {
        String value = optionalText(entry, key);
        if (value == null) throw new IllegalArgumentException("Manifest field is required: " + key);
        return value;
    }

    private static String optionalText(JsonNode entry, String key) {
        JsonNode value = entry.get(key);
        return value == null || value.isNull() || value.asText().isBlank()
                ? null : value.asText().trim();
    }

    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
