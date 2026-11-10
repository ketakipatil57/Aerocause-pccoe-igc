package com.aerocause.service;

import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.aerocause.service.replay.ReplayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class EvaluationService {
    private final EpisodeRepository episodeRepo;
    private final IncidentRepository incidentRepo;
    private final HypothesisRepository hypothesisRepo;
    private final EvaluationRunRepository evaluationRunRepo;
    private final ReplayService replayService;
    private final AiServiceClient aiServiceClient;
    private final AuditService auditService;
    private final EvaluationReportExporter reportExporter;
    private final ObjectMapper objectMapper;

    public EvaluationService(
            EpisodeRepository episodeRepo,
            IncidentRepository incidentRepo,
            HypothesisRepository hypothesisRepo,
            EvaluationRunRepository evaluationRunRepo,
            ReplayService replayService,
            AiServiceClient aiServiceClient,
            AuditService auditService,
            EvaluationReportExporter reportExporter,
            ObjectMapper objectMapper) {
        this.episodeRepo = episodeRepo;
        this.incidentRepo = incidentRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.evaluationRunRepo = evaluationRunRepo;
        this.replayService = replayService;
        this.aiServiceClient = aiServiceClient;
        this.auditService = auditService;
        this.reportExporter = reportExporter;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> runAll() {
        if (replayService.getStatus().running()) {
            throw new IllegalStateException("Stop the active replay before running evaluation.");
        }
        Instant runAt = Instant.now();
        auditService.log("EVALUATION_RUN_STARTED", null, Map.of("runAt", runAt.toString()));

        List<Map<String, Object>> results = new ArrayList<>();
        for (Episode episode : episodeRepo.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            results.add(evaluateEpisode(episode));
        }

        long unsupportedTotal = results.stream()
                .mapToLong(row -> ((Number) row.get("unsupportedClaimsFinal")).longValue())
                .sum();
        boolean validatorAvailable = results.stream()
                .allMatch(row -> Boolean.TRUE.equals(row.get("validatorAvailable")));
        boolean citationsValid = results.stream()
                .allMatch(row -> Boolean.TRUE.equals(row.get("citationsValid")));
        boolean buildPass = !results.isEmpty() && unsupportedTotal == 0
                && validatorAvailable && citationsValid;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runAt", runAt.toString());
        response.put("buildPass", buildPass);
        response.put("unsupportedClaimsFinalTotal", unsupportedTotal);
        response.put("validatorAvailable", validatorAvailable);
        response.put("citationsValid", citationsValid);
        response.put("episodes", results);
        response.put("syntheticEpisodes", results.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("synthetic"))).toList());
        response.put("realEpisodes", results.stream()
                .filter(row -> Boolean.FALSE.equals(row.get("synthetic"))).toList());
        response.put("summary", Map.of(
                "total", results.size(),
                "pass", results.stream().filter(row -> Boolean.TRUE.equals(row.get("pass"))).count(),
                "fail", results.stream().filter(row -> Boolean.FALSE.equals(row.get("pass"))).count()));

        try {
            String json = objectMapper.writeValueAsString(response);
            evaluationRunRepo.save(EvaluationRun.builder()
                    .runAt(runAt)
                    .resultsJson(json)
                    .buildPass(buildPass)
                    .build());
            reportExporter.export(json);
            auditService.log("EVALUATION_RUN_COMPLETE", null, Map.of(
                    "buildPass", buildPass,
                    "unsupportedClaimsFinal", unsupportedTotal));
        } catch (Exception e) {
            auditService.log("EVALUATION_RUN_FAILED", null, Map.of("error", safeMessage(e)));
            throw new IllegalStateException("Evaluation results could not be stored or exported.", e);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLatest() {
        return evaluationRunRepo.findTopByOrderByRunAtDesc()
                .map(run -> readStored(run.getResultsJson()))
                .orElseGet(() -> Map.of(
                        "runAt", "",
                        "buildPass", false,
                        "unsupportedClaimsFinalTotal", 0,
                        "validatorAvailable", false,
                        "citationsValid", false,
                        "episodes", List.of(),
                        "syntheticEpisodes", List.of(),
                        "realEpisodes", List.of(),
                        "summary", Map.of("total", 0, "pass", 0, "fail", 0)));
    }

    private Map<String, Object> evaluateEpisode(Episode episode) {
        ReplayService.ReplayStatus started = replayService.start(episode.getId(), 1_000_000);
        if (!started.running() || !Objects.equals(started.episodeId(), episode.getId())) {
            throw new IllegalStateException("Could not start replay for episode " + episode.getId()
                    + ": " + started.message());
        }
        awaitReplay(episode.getId());

        List<Incident> incidents = incidentRepo.findByEpisodeIdOrderByDetectedAtAsc(episode.getId());
        Incident incident = incidents.isEmpty() ? null : incidents.get(incidents.size() - 1);
        List<Hypothesis> hypotheses = incident == null ? List.of()
                : hypothesisRepo.findByIncidentIdOrderByRankAsc(incident.getId());
        String actual = hypotheses.isEmpty() ? "NONE" : hypotheses.get(0).getType().name();

        Brief brief = incident == null ? null : aiServiceClient.investigate(incident);
        JsonNode content = brief == null ? objectMapper.createObjectNode()
                : readTree(brief.getContentJson());
        boolean validatorAvailable = content.has("first_attempt_violations")
                && content.has("final_violations");
        int firstAttemptViolations = content.path("first_attempt_violations").isArray()
                ? content.path("first_attempt_violations").size() : 0;
        int unsupportedClaims = content.path("final_violations").isArray()
                ? content.path("final_violations").size() : 0;
        boolean citationsValid = validateCitations(content);
        Long timeToBriefMs = incident == null || brief == null ? null
                : Math.max(0, Duration.between(incident.getDetectedAt(), brief.getGeneratedAt()).toMillis());
        boolean expectedFault = Boolean.TRUE.equals(episode.getExpectedFault());
        boolean sensorFaultFlagged = "SENSOR_FAULT".equals(actual);
        String expected = Boolean.TRUE.equals(episode.getSynthetic())
                ? Optional.ofNullable(episode.getExpectedTopHypothesis()).orElse("")
                : "";
        Boolean pass = expected.isBlank() ? null : expected.equalsIgnoreCase(actual);
        boolean rowBuildPass = unsupportedClaims == 0 && validatorAvailable && citationsValid;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("episodeId", episode.getId());
        row.put("name", episode.getName());
        row.put("synthetic", Boolean.TRUE.equals(episode.getSynthetic()));
        row.put("expected", expected);
        row.put("actual", actual);
        row.put("confidence", hypotheses.isEmpty() ? "N/A" : hypotheses.get(0).getConfidenceLabel().name());
        row.put("incidentCount", incidents.size());
        row.put("expectedFault", Boolean.TRUE.equals(episode.getSynthetic()) ? expectedFault : null);
        row.put("sensorFaultFlagged", sensorFaultFlagged);
        row.put("firstAttemptValidatorViolations", firstAttemptViolations);
        row.put("unsupportedClaimsFinal", unsupportedClaims);
        row.put("validatorAvailable", validatorAvailable);
        row.put("citationsValid", citationsValid);
        row.put("timeToBriefMs", timeToBriefMs);
        row.put("pass", pass);
        row.put("buildPass", rowBuildPass);
        if (!rowBuildPass) {
            row.put("buildFailure", !validatorAvailable ? "Final brief was not validated."
                    : !citationsValid ? "A citation did not resolve to a retrieved document passage."
                    : "Final brief contains validator violations.");
        }
        return row;
    }

    private void awaitReplay(Long episodeId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (replayService.getStatus().running() && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Evaluation replay was interrupted.", e);
            }
        }
        ReplayService.ReplayStatus status = replayService.getStatus();
        if (status.running()) {
            replayService.stop();
            throw new IllegalStateException("Replay timed out for episode " + episodeId);
        }
        if (status.error() != null && !status.error().isBlank()) {
            throw new IllegalStateException("Replay failed for episode " + episodeId + ": " + status.error());
        }
    }

    private boolean validateCitations(JsonNode content) {
        Map<String, JsonNode> passages = new HashMap<>();
        JsonNode retrieved = content.path("retrieved_passages");
        if (retrieved.isArray()) {
            retrieved.forEach(passage -> {
                String passageId = passage.path("passage_id").asText("");
                if (!passageId.isBlank()) passages.put(passageId, passage);
            });
        }
        JsonNode actions = content.path("brief").path("recommended_actions");
        if (!actions.isArray()) return true;
        for (JsonNode action : actions) {
            JsonNode citationIds = action.path("citation_ids");
            if (!citationIds.isArray()) return false;
            for (JsonNode citationId : citationIds) {
                JsonNode passage = passages.get(citationId.asText());
                if (passage == null || passage.path("doc_key").asText("").isBlank()
                        || passage.path("title").asText("").isBlank()) return false;
            }
        }
        return true;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("AI brief response is invalid JSON.", e);
        }
    }

    private Map<String, Object> readStored(String json) {
        try {
            return objectMapper.convertValue(objectMapper.readTree(json), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored evaluation results are invalid JSON.", e);
        }
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
