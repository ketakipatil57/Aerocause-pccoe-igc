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
    public List<Incident> getIncidents(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return incidentRepo.findByStatus(Incident.IncidentStatus.valueOf(status.toUpperCase()));
        }
        return incidentRepo.findAll();
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Get full incident detail including evidence, hypotheses, brief, reviews")
    public ResponseEntity<?> getIncident(@PathVariable Long id) {
        return incidentRepo.findById(id)
                .map(inc -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("incident", inc);
                    detail.put("hypotheses", hypothesisRepo.findByIncidentIdOrderByRankAsc(id));
                    detail.put("evidenceItems", evidenceItemRepo.findByIncidentId(id));
                    detail.put("brief", briefRepo.findTopByIncidentIdOrderByGeneratedAtDesc(id).orElse(null));
                    detail.put("reviews", reviewRepo.findByIncidentIdOrderByCreatedAtDesc(id));
                    detail.put("dataNotice", inc.getSynthetic() ? "SAMPLE DATA (synthetic)" : null);
                    detail.put("decisionSupportNotice",
                            "Decision support only. Final decisions belong to qualified officers.");
                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
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
