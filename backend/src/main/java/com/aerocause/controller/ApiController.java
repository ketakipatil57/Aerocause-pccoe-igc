package com.aerocause.controller;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.*;
import com.aerocause.service.replay.ReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "AeroCause API", description = "Air quality incident decision support")
@CrossOrigin(origins = "*")
public class ApiController {

    private final StationRepository stationRepo;
    private final ReadingRepository readingRepo;
    private final EpisodeRepository episodeRepo;
    private final IncidentRepository incidentRepo;
    private final HypothesisRepository hypothesisRepo;
    private final EvidenceItemRepository evidenceItemRepo;
    private final QcFlagRepository qcFlagRepo;
    private final CitationRepository citationRepo;
    private final BriefRepository briefRepo;
    private final ReviewRepository reviewRepo;
    private final AuditService auditService;
    private final AiServiceClient aiClient;
    private final ReplayService replayService;
    private final AqiService aqiService;

    public ApiController(StationRepository stationRepo,
                         ReadingRepository readingRepo,
                         EpisodeRepository episodeRepo,
                         IncidentRepository incidentRepo,
                         HypothesisRepository hypothesisRepo,
                         EvidenceItemRepository evidenceItemRepo,
                         QcFlagRepository qcFlagRepo,
                         CitationRepository citationRepo,
                         BriefRepository briefRepo,
                         ReviewRepository reviewRepo,
                         AuditService auditService,
                         AiServiceClient aiClient,
                         ReplayService replayService,
                         AqiService aqiService) {
        this.stationRepo = stationRepo;
        this.readingRepo = readingRepo;
        this.episodeRepo = episodeRepo;
        this.incidentRepo = incidentRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.evidenceItemRepo = evidenceItemRepo;
        this.qcFlagRepo = qcFlagRepo;
        this.citationRepo = citationRepo;
        this.briefRepo = briefRepo;
        this.reviewRepo = reviewRepo;
        this.auditService = auditService;
        this.aiClient = aiClient;
        this.replayService = replayService;
        this.aqiService = aqiService;
    }

    // =========================================================================
    // Stations
    // =========================================================================

    @GetMapping("/stations")
    @Operation(summary = "List all monitoring stations")
    public List<Station> getStations() {
        return stationRepo.findAll();
    }

    @GetMapping("/stations/{id}/readings")
    @Operation(summary = "Get readings for a station")
    public ResponseEntity<?> getReadings(
            @PathVariable Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String pollutant) {
        Station station = stationRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Station not found: " + id));
        Instant fromI = from != null ? Instant.parse(from) : Instant.now().minusSeconds(86400);
        Instant toI   = to   != null ? Instant.parse(to)   : Instant.now();
        List<Reading> readings = pollutant != null
                ? readingRepo.findByStationAndPollutantAndObservedAtBetweenOrderByObservedAtAsc(
                        station, pollutant, fromI, toI)
                : readingRepo.findByStationAndObservedAtBetweenOrderByObservedAtAsc(station, fromI, toI);
        return ResponseEntity.ok(readings);
    }

    // =========================================================================
    // Episodes
    // =========================================================================

    @GetMapping("/episodes")
    @Operation(summary = "List all episodes")
    public List<Episode> getEpisodes() {
        return episodeRepo.findAll();
    }

    @GetMapping("/episodes/{id}")
    @Operation(summary = "Get episode by ID")
    public ResponseEntity<Episode> getEpisode(@PathVariable Long id) {
        return episodeRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // Replay
    // =========================================================================

    @PostMapping("/replay/start")
    @Operation(summary = "Start episode replay")
    public ResponseEntity<?> startReplay(@RequestBody ReplayRequest req) {
        try {
            return ResponseEntity.ok(replayService.start(req.episodeId(), req.speedMultiplier()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/replay/status")
    @Operation(summary = "Get replay status")
    public ReplayService.ReplayStatus replayStatus() {
        return replayService.getStatus();
    }

    @PostMapping("/replay/stop")
    @Operation(summary = "Stop ongoing replay")
    public ResponseEntity<Void> stopReplay() {
        replayService.stop();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/replay/import-all")
    @Operation(summary = "Import all episode CSVs from /data/episodes/ into database")
    public ResponseEntity<?> importAll() {
        // Episodes are seeded via Flyway V5; this endpoint triggers bulk episode loading
        // from the metadata JSON. For prototype, episodes are pre-seeded.
        List<Episode> episodes = episodeRepo.findAll();
        return ResponseEntity.ok(Map.of(
                "message", "Episodes available: " + episodes.size(),
                "episodes", episodes.stream().map(e -> Map.of(
                        "id", e.getId(), "name", e.getName()
                )).toList()
        ));
    }

    // =========================================================================
    // Incidents
    // =========================================================================

    @GetMapping("/incidents")
    @Operation(summary = "List incidents, optionally filtered by status")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getIncidents(
            @RequestParam(required = false) String status) {
        List<Incident> incidents = status != null
                ? incidentRepo.findByStatus(Incident.IncidentStatus.valueOf(status.toUpperCase()))
                : incidentRepo.findAll();
        return incidents.stream().map(this::toIncidentSummary).toList();
    }

    private Map<String, Object> toIncidentSummary(Incident incident) {
        Map<String, Object> summary = toIncidentView(incident);
        Long incidentId = incident.getId();
        List<Hypothesis> hypotheses = hypothesisRepo.findByIncidentIdOrderByRankAsc(incidentId);
        summary.put("topHypothesis", hypotheses.isEmpty() ? null : toHypothesisView(hypotheses.get(0)));
        summary.put("qcFlags", qcFlagRepo.findByStationAndPollutantAndWindowStartBetween(
                incident.getStation(), incident.getPollutant(),
                incident.getWindowStart(), incident.getWindowEnd()).stream()
                .map(this::toQcFlagView).toList());
        return summary;
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Get full incident detail including evidence, hypotheses, brief, reviews")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getIncident(@PathVariable Long id) {
        return incidentRepo.findById(id)
                .map(inc -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("incident", toIncidentView(inc));
                    detail.put("hypotheses", hypothesisRepo.findByIncidentIdOrderByRankAsc(id).stream()
                            .map(this::toHypothesisView).toList());
                    detail.put("evidenceItems", evidenceItemRepo.findByIncidentId(id).stream()
                            .map(this::toEvidenceItemView).toList());
                    detail.put("qcFlags", qcFlagRepo.findByStationAndPollutantAndWindowStartBetween(
                            inc.getStation(), inc.getPollutant(), inc.getWindowStart(), inc.getWindowEnd())
                            .stream().map(this::toQcFlagView).toList());
                    detail.put("brief", briefRepo.findTopByIncidentIdOrderByGeneratedAtDesc(id)
                            .map(this::toBriefView).orElse(null));
                    detail.put("citations", citationRepo.findByIncidentId(id).stream()
                            .map(this::toCitationView).toList());
                    detail.put("reviews", reviewRepo.findByIncidentIdOrderByCreatedAtDesc(id).stream()
                            .map(this::toReviewView).toList());
                    detail.put("dataNotice", inc.getSynthetic() ? "SAMPLE DATA (synthetic)" : null);
                    detail.put("decisionSupportNotice",
                            "Decision support only. Final decisions belong to qualified officers.");
                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toIncidentView(Incident incident) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", incident.getId());
        view.put("station", toStationView(incident.getStation()));
        view.put("pollutant", incident.getPollutant());
        view.put("detectedAt", incident.getDetectedAt());
        view.put("windowStart", incident.getWindowStart());
        view.put("windowEnd", incident.getWindowEnd());
        view.put("peakValue", incident.getPeakValue());
        view.put("baselineValue", incident.getBaselineValue());
        view.put("status", incident.getStatus());
        view.put("episodeId", incident.getEpisodeId());
        view.put("synthetic", incident.getSynthetic());
        return view;
    }

    private Map<String, Object> toStationView(Station station) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", station.getId());
        view.put("externalId", station.getExternalId());
        view.put("name", station.getName());
        view.put("city", station.getCity());
        view.put("lat", station.getLat());
        view.put("lon", station.getLon());
        view.put("provider", station.getProvider());
        return view;
    }

    private Map<String, Object> toHypothesisView(Hypothesis hypothesis) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", hypothesis.getId());
        view.put("type", hypothesis.getType());
        view.put("score", hypothesis.getScore());
        view.put("confidenceLabel", hypothesis.getConfidenceLabel());
        view.put("evidenceForJson", hypothesis.getEvidenceForJson());
        view.put("evidenceAgainstJson", hypothesis.getEvidenceAgainstJson());
        view.put("missingJson", hypothesis.getMissingJson());
        view.put("rank", hypothesis.getRank());
        return view;
    }

    private Map<String, Object> toEvidenceItemView(EvidenceItem evidenceItem) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", evidenceItem.getId());
        view.put("type", evidenceItem.getType());
        view.put("summary", evidenceItem.getSummary());
        view.put("dataJson", evidenceItem.getDataJson());
        view.put("sourceRef", evidenceItem.getSourceRef());
        return view;
    }

    private Map<String, Object> toQcFlagView(QcFlag flag) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", flag.getId());
        view.put("pollutant", flag.getPollutant());
        view.put("windowStart", flag.getWindowStart());
        view.put("windowEnd", flag.getWindowEnd());
        view.put("rule", flag.getRule());
        view.put("severity", flag.getSeverity());
        view.put("detail", flag.getDetail());
        view.put("createdAt", flag.getCreatedAt());
        return view;
    }

    private Map<String, Object> toBriefView(Brief brief) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", brief.getId());
        view.put("generatedAt", brief.getGeneratedAt());
        view.put("contentJson", brief.getContentJson());
        view.put("validatorStatus", brief.getValidatorStatus());
        view.put("llmMode", brief.getLlmMode());
        return view;
    }

    private Map<String, Object> toCitationView(Citation citation) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("docKey", citation.getDocument().getDocKey());
        document.put("title", citation.getDocument().getTitle());
        document.put("publisher", citation.getDocument().getPublisher());
        document.put("version", citation.getDocument().getVersion());
        document.put("url", citation.getDocument().getUrl());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", citation.getId());
        view.put("passageRef", citation.getPassageRef());
        view.put("excerpt", citation.getExcerpt());
        view.put("document", document);
        return view;
    }

    private Map<String, Object> toReviewView(Review review) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", review.getId());
        view.put("reviewer", review.getReviewer());
        view.put("decision", review.getDecision());
        view.put("comment", review.getComment());
        view.put("createdAt", review.getCreatedAt());
        return view;
    }

    @PostMapping("/incidents/{id}/investigate")
    @Operation(summary = "Call AI service to generate a brief for this incident")
    public ResponseEntity<?> investigate(@PathVariable Long id) {
        Incident incident = incidentRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + id));
        Brief brief = aiClient.investigate(incident);
        return ResponseEntity.ok(Map.of(
                "briefId", brief.getId(),
                "validatorStatus", brief.getValidatorStatus().name(),
                "llmMode", brief.getLlmMode().name(),
                "generatedAt", brief.getGeneratedAt().toString()
        ));
    }

    @PostMapping("/incidents/{id}/review")
    @Operation(summary = "Submit officer review decision for an incident")
    public ResponseEntity<?> review(@PathVariable Long id,
                                     @RequestBody @Valid ReviewRequest req) {
        Incident incident = incidentRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + id));

        Review review = Review.builder()
                .incident(incident)
                .reviewer(req.reviewer())
                .decision(Review.ReviewDecision.valueOf(req.decision().toUpperCase()))
                .comment(req.comment())
                .createdAt(Instant.now())
                .build();
        reviewRepo.save(review);

        // Update incident status
        incident.setStatus(switch (review.getDecision()) {
            case ACCEPT -> Incident.IncidentStatus.CONFIRMED;
            case REJECT -> Incident.IncidentStatus.REJECTED;
            case NEEDS_MORE_EVIDENCE -> Incident.IncidentStatus.UNDER_REVIEW;
        });
        incidentRepo.save(incident);

        auditService.log("REVIEW_SUBMITTED", id, Map.of(
                "reviewer", req.reviewer(),
                "decision", req.decision(),
                "comment", req.comment() != null ? req.comment() : ""
        ));

        return ResponseEntity.ok(Map.of(
                "reviewId", review.getId(),
                "decision", review.getDecision().name(),
                "incidentStatus", incident.getStatus().name()
        ));
    }

    @GetMapping("/incidents/{id}/audit")
    @Operation(summary = "Get audit trail for an incident")
    public ResponseEntity<List<AuditEvent>> getAudit(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getForIncident(id));
    }

    // =========================================================================
    // Health / Source Freshness
    // =========================================================================

    @GetMapping("/health/sources")
    @Operation(summary = "Get data source freshness status")
    public Map<String, Object> sourceFreshness() {
        return Map.of(
                "openaq",    Map.of("enabled", false, "note", "Live polling disabled; use replay"),
                "openMeteo", Map.of("enabled", false, "note", "Live polling disabled; use replay"),
                "firms",     Map.of("enabled", false, "note", "Fire hotspot provider disabled by default"),
                "replay",    Map.of("running", replayService.getStatus().running(),
                        "episodeId", replayService.getStatus().episodeId() != null
                                ? replayService.getStatus().episodeId() : "none"),
                "dataNotice", "SAMPLE DATA (synthetic) — no live sources connected"
        );
    }

    // =========================================================================
    // Citizen API
    // =========================================================================

    @GetMapping("/citizen/aqi")
    @Operation(summary = "Safe citizen-facing AQI summary for a station")
    public ResponseEntity<?> citizenAqi(@RequestParam Long stationId) {
        Station station = stationRepo.findById(stationId)
                .orElseThrow(() -> new NoSuchElementException("Station not found: " + stationId));

        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);

        // Get latest readings for all pollutants
        Map<String, Double> concentrations = new LinkedHashMap<>();
        for (String p : new String[]{"PM25", "PM10", "NO2"}) {
            readingRepo.findByStationAndPollutantAndObservedAtBetweenOrderByObservedAtAsc(
                            station, p, oneHourAgo, now)
                    .stream().mapToDouble(Reading::getValue).average()
                    .ifPresent(v -> concentrations.put(p, v));
        }

        AqiService.AqiResult aqi = aqiService.computeAqi(concentrations);

        String advice = switch (aqi.category()) {
            case "Good"         -> "Air quality is satisfactory. Enjoy outdoor activities.";
            case "Satisfactory" -> "Air quality is acceptable for most people.";
            case "Moderate"     -> "Sensitive individuals may experience mild effects. Consider reducing prolonged outdoor activity.";
            case "Poor"         -> "Consider limiting prolonged outdoor activity, especially for sensitive groups.";
            case "Very Poor"    -> "Avoid prolonged outdoor activity. Sensitive groups should stay indoors.";
            case "Severe"       -> "Everyone should avoid outdoor activity. Sensitive groups should stay indoors.";
            default             -> "Air quality data is currently unavailable.";
        };

        return ResponseEntity.ok(Map.of(
                "stationName", station.getName(),
                "stationId", stationId,
                "aqi", aqi.aqi(),
                "category", aqi.category(),
                "dominantPollutant", aqi.dominantPollutant(),
                "advice", advice,
                "observedAt", now.toString(),
                "dataNotice", "SAMPLE DATA (synthetic)",
                "disclaimer",
                "Decision support only. This is not an official government reading. " +
                "Verify with CPCB or your local pollution control board.",
                "subIndexes", aqi.subIndexes()
        ));
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public record ReplayRequest(Long episodeId, int speedMultiplier) {}

    public record ReviewRequest(
            @NotBlank String reviewer,
            @NotBlank String decision,
            String comment
    ) {}
}
