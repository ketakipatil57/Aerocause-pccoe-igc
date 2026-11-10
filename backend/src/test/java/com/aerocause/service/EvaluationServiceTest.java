package com.aerocause.service;

import com.aerocause.domain.*;
import com.aerocause.domain.Hypothesis.ConfidenceLabel;
import com.aerocause.domain.Hypothesis.HypothesisType;
import com.aerocause.repository.*;
import com.aerocause.service.replay.ReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {
    @Mock EpisodeRepository episodeRepo;
    @Mock IncidentRepository incidentRepo;
    @Mock HypothesisRepository hypothesisRepo;
    @Mock EvaluationRunRepository evaluationRunRepo;
    @Mock ReplayService replayService;
    @Mock AiServiceClient aiServiceClient;
    @Mock AuditService auditService;

    @TempDir Path tempDir;

    @Test
    void evaluationStoresVerifiedMetricsAndExportsReports() throws Exception {
        EvaluationService service = service();
        Episode episode = Episode.builder()
                .id(1L).name("Dust sample").synthetic(true)
                .expectedTopHypothesis("DUST").expectedFault(false).build();
        Incident incident = incident();
        Brief brief = brief("""
                {"brief":{"recommended_actions":[{"citation_ids":[]}]},"first_attempt_violations":["injected"],"final_violations":[],"retrieved_passages":[]}
                """);
        when(episodeRepo.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(episode));
        when(replayService.getStatus()).thenReturn(idle());
        when(replayService.start(1L, 1_000_000)).thenReturn(running(1L));
        when(incidentRepo.findByEpisodeIdOrderByDetectedAtAsc(1L)).thenReturn(List.of(incident));
        when(hypothesisRepo.findByIncidentIdOrderByRankAsc(4L)).thenReturn(List.of(hypothesis()));
        when(aiServiceClient.investigate(incident)).thenReturn(brief);
        when(evaluationRunRepo.save(any(EvaluationRun.class)))
                .thenAnswer(call -> call.getArgument(0));

        var result = service.runAll();

        assertThat(result.get("buildPass")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows =
                (List<java.util.Map<String, Object>>) result.get("episodes");
        assertThat(rows.get(0)).containsEntry("firstAttemptValidatorViolations", 1)
                .containsEntry("unsupportedClaimsFinal", 0)
                .containsEntry("citationsValid", true)
                .containsEntry("actual", "DUST");
        assertThat(Files.readString(tempDir.resolve("evaluation-results.csv")))
                .contains("first_attempt_validator_violations").contains("Dust sample");
        assertThat(Files.readString(tempDir.resolve("evaluation-results.md")))
                .contains("| Synthetic | Dust sample | DUST | DUST |");
    }

    @Test
    void finalValidatorViolationsFailTheEvaluationBuildGate() {
        EvaluationService service = service();
        Episode episode = Episode.builder()
                .id(1L).name("Dust sample").synthetic(true)
                .expectedTopHypothesis("DUST").expectedFault(false).build();
        Incident incident = incident();
        when(episodeRepo.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(episode));
        when(replayService.getStatus()).thenReturn(idle());
        when(replayService.start(1L, 1_000_000)).thenReturn(running(1L));
        when(incidentRepo.findByEpisodeIdOrderByDetectedAtAsc(1L)).thenReturn(List.of(incident));
        when(hypothesisRepo.findByIncidentIdOrderByRankAsc(4L)).thenReturn(List.of(hypothesis()));
        when(aiServiceClient.investigate(incident)).thenReturn(brief("""
                {"brief":{"recommended_actions":[]},"first_attempt_violations":[],"final_violations":["unsupported_number"]}
                """));
        when(evaluationRunRepo.save(any(EvaluationRun.class)))
                .thenAnswer(call -> call.getArgument(0));

        var result = service.runAll();

        assertThat(result.get("buildPass")).isEqualTo(false);
        assertThat(result.get("unsupportedClaimsFinalTotal")).isEqualTo(1L);
    }

    private EvaluationService service() {
        EvaluationReportExporter exporter = new EvaluationReportExporter(
                new ObjectMapper(), tempDir.toString());
        return new EvaluationService(episodeRepo, incidentRepo, hypothesisRepo,
                evaluationRunRepo, replayService, aiServiceClient, auditService,
                exporter, new ObjectMapper());
    }

    private static ReplayService.ReplayStatus idle() {
        return new ReplayService.ReplayStatus(false, 1L, 1.0, 10, 1,
                "No replay running", null);
    }

    private static ReplayService.ReplayStatus running(Long episodeId) {
        return new ReplayService.ReplayStatus(true, episodeId, 0.0, 0, 0,
                "Replay in progress", null);
    }

    private static Incident incident() {
        return Incident.builder().id(4L).detectedAt(Instant.now().minusMillis(50))
                .build();
    }

    private static Hypothesis hypothesis() {
        return Hypothesis.builder().id(10L).type(HypothesisType.DUST)
                .confidenceLabel(ConfidenceLabel.HIGH).rank(1).score(0.8).build();
    }

    private static Brief brief(String content) {
        return Brief.builder().generatedAt(Instant.now()).contentJson(content)
                .validatorStatus(Brief.ValidatorStatus.PASSED)
                .llmMode(Brief.LlmMode.MOCK).build();
    }
}
