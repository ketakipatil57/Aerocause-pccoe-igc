package com.aerocause.controller;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.replay.ReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

/**
 * Evaluation controller.
 * Runs all 5 synthetic episodes and compares expected vs actual top hypotheses.
 * This endpoint is used for the demo's evaluation page.
 */
@RestController
@RequestMapping("/api/evaluation")
@Tag(name = "Evaluation", description = "Episode evaluation against expected outcomes")
@CrossOrigin(origins = "*")
public class EvaluationController {

    private final EpisodeRepository episodeRepo;
    private final IncidentRepository incidentRepo;
    private final HypothesisRepository hypothesisRepo;
    private final ReplayService replayService;

    public EvaluationController(EpisodeRepository episodeRepo,
                                IncidentRepository incidentRepo,
                                HypothesisRepository hypothesisRepo,
                                ReplayService replayService) {
        this.episodeRepo = episodeRepo;
        this.incidentRepo = incidentRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.replayService = replayService;
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest evaluation results for all episodes")
    public ResponseEntity<?> getLatest() {
        List<Map<String, Object>> results = new ArrayList<>();
        int pass = 0;

        for (Episode episode : episodeRepo.findAll()) {
            List<Incident> incidents = incidentRepo.findByEpisodeIdOrderByDetectedAtAsc(episode.getId());
            String actualHypothesis = "NONE";
            String confidenceLabel = "N/A";

            if (!incidents.isEmpty()) {
                Incident latest = incidents.get(incidents.size() - 1);
                List<Hypothesis> hyps = hypothesisRepo.findByIncidentIdOrderByRankAsc(latest.getId());
                if (!hyps.isEmpty()) {
                    actualHypothesis = hyps.get(0).getType().name();
                    confidenceLabel  = hyps.get(0).getConfidenceLabel().name();
                }
            }

            String expected = episode.getExpectedTopHypothesis();
            boolean passed = expected != null && expected.equalsIgnoreCase(actualHypothesis);
            if (passed) pass++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("episodeId", episode.getId());
            row.put("name", episode.getName());
            row.put("expected", expected != null ? expected : "N/A");
            row.put("actual", actualHypothesis);
            row.put("confidence", confidenceLabel);
            row.put("incidentCount", incidents.size());
            row.put("pass", passed);
            row.put("dataNotice", "SAMPLE DATA (synthetic)");
            results.add(row);
        }

        int total = results.size();
        return ResponseEntity.ok(Map.of(
                "runAt", Instant.now().toString(),
                "dataNotice", "SAMPLE DATA (synthetic)",
                "decisionSupportNotice",
                "These results are from synthetic episodes only. " +
                "Do not use for operational decisions.",
                "episodes", results,
                "summary", Map.of("total", total, "pass", pass, "fail", total - pass)
        ));
    }

    @PostMapping("/run")
    @Operation(summary = "Trigger replay of all episodes for evaluation (runs sequentially)")
    public ResponseEntity<?> runEvaluation() {
        List<Episode> episodes = episodeRepo.findAll();
        return ResponseEntity.accepted().body(Map.of(
                "message", "Evaluation requires replaying episodes. " +
                        "Start each episode replay individually via POST /api/replay/start, " +
                        "then view results at GET /api/evaluation/latest.",
                "episodes", episodes.stream().map(e -> Map.of(
                        "id", e.getId(), "name", e.getName()
                )).toList(),
                "note", "Full automated evaluation run not supported in this prototype version."
        ));
    }
}
