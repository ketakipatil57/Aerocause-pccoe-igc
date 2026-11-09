package com.aerocause.service;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles the structured evidence package JSON for one incident.
 *
 * <p>This package is the ONLY input to the AI service /investigate endpoint.
 * It contains only structured data computed by deterministic code — never LLM output.
 * The LLM only explains this pre-computed evidence; it does not create or modify it.
 */
@Service
public class EvidencePackageBuilder {

    private final ReadingRepository readingRepo;
    private final WeatherObsRepository weatherRepo;
    private final QcFlagRepository qcFlagRepo;
    private final HypothesisRepository hypothesisRepo;
    private final EvidenceItemRepository evidenceItemRepo;
    private final ObjectMapper objectMapper;

    public EvidencePackageBuilder(ReadingRepository readingRepo,
                                  WeatherObsRepository weatherRepo,
                                  QcFlagRepository qcFlagRepo,
                                  HypothesisRepository hypothesisRepo,
                                  EvidenceItemRepository evidenceItemRepo,
                                  ObjectMapper objectMapper) {
        this.readingRepo = readingRepo;
        this.weatherRepo = weatherRepo;
        this.qcFlagRepo = qcFlagRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.evidenceItemRepo = evidenceItemRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildPackage(Incident incident) {
        Objects.requireNonNull(incident, "incident must not be null");
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("packageVersion", "1.0");
        pkg.put("dataNotice", incident.getSynthetic()
                ? "SAMPLE DATA (synthetic) — All readings are artificially generated."
                : "Data from external provider. Verify before acting.");
        pkg.put("decisionSupportNotice",
                "Decision support only. Final verification and enforcement decisions belong to qualified officers.");

        // Incident metadata
        Map<String, Object> incidentMap = new LinkedHashMap<>();
        incidentMap.put("id", incident.getId());
        incidentMap.put("stationExternalId", incident.getStation().getExternalId());
        incidentMap.put("stationName", incident.getStation().getName());
        incidentMap.put("pollutant", incident.getPollutant());
        incidentMap.put("detectedAt", incident.getDetectedAt().toString());
        incidentMap.put("windowStart", incident.getWindowStart().toString());
        incidentMap.put("windowEnd", incident.getWindowEnd().toString());
        incidentMap.put("peakValue", incident.getPeakValue());
        incidentMap.put("baselineValue", incident.getBaselineValue());
        incidentMap.put("status", incident.getStatus().name());
        incidentMap.put("synthetic", incident.getSynthetic());
        pkg.put("incident", incidentMap);

        // Readings (window ± buffer)
        List<Reading> readings = readingRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                incident.getStation(),
                incident.getWindowStart().minus(2, ChronoUnit.HOURS),
                incident.getWindowEnd().plus(1, ChronoUnit.HOURS));
        pkg.put("readings", readings.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pollutant", r.getPollutant());
            m.put("value", r.getValue());
            m.put("unit", r.getUnit());
            m.put("observedAt", r.getObservedAt().toString());
            m.put("qualityStatus", r.getQualityStatus().name());
            return m;
        }).collect(Collectors.toList()));

        // Weather
        List<WeatherObs> weather = weatherRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(
                incident.getStation(), incident.getWindowStart(), incident.getWindowEnd());
        pkg.put("weather", weather.stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("observedAt", w.getObservedAt().toString());
            m.put("windSpeed", w.getWindSpeed());
            m.put("windDirDeg", w.getWindDirDeg());
            m.put("boundaryLayerHeight", w.getBoundaryLayerHeight());
            m.put("fireHotspotUpwind", w.getFireHotspotUpwind());
            return m;
        }).collect(Collectors.toList()));

        // QC flags
        List<QcFlag> flags = qcFlagRepo.findByStationAndWindowStartBetween(
                incident.getStation(),
                incident.getWindowStart().minus(1, ChronoUnit.HOURS),
                incident.getWindowEnd());
        pkg.put("qcFlags", flags.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rule", f.getRule().name());
            m.put("severity", f.getSeverity().name());
            m.put("pollutant", f.getPollutant());
            m.put("detail", f.getDetail());
            return m;
        }).collect(Collectors.toList()));

        // Hypotheses
        List<Hypothesis> hypotheses = hypothesisRepo.findByIncidentIdOrderByRankAsc(incident.getId());
        pkg.put("hypotheses", hypotheses.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", h.getRank());
            m.put("type", h.getType().name());
            m.put("score", h.getScore());
            m.put("confidenceLabel", h.getConfidenceLabel().name());
            m.put("evidenceFor",   parseJson(h.getEvidenceForJson()));
            m.put("evidenceAgainst", parseJson(h.getEvidenceAgainstJson()));
            m.put("missing",       parseJson(h.getMissingJson()));
            return m;
        }).collect(Collectors.toList()));

        // Evidence items
        List<EvidenceItem> items = evidenceItemRepo.findByIncidentId(incident.getId());
        pkg.put("evidenceItems", items.stream().map(ei -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ei.getSourceRef());
            m.put("type", ei.getType());
            m.put("summary", ei.getSummary());
            return m;
        }).collect(Collectors.toList()));

        return pkg;
    }

    public String buildPackageJson(Incident incident) {
        try {
            return objectMapper.writeValueAsString(buildPackage(incident));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize evidence package", e);
        }
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored hypothesis evidence is not valid JSON", e);
        }
    }
}
