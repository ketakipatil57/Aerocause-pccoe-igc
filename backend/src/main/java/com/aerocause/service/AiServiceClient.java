package com.aerocause.service;

import com.aerocause.config.AeroCauseProperties;
import com.aerocause.domain.*;
import com.aerocause.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

/**
 * Client for the AI service (FastAPI at /investigate).
 *
 * <p>Sends the evidence package JSON to the AI service and stores the resulting
 * brief in the database. Falls back to a template brief if the AI service is
 * unreachable or returns invalid output.
 *
 * <p>The LLM only explains evidence produced by deterministic code.
 * It is NOT the source of truth for readings, thresholds, or regulations.
 */
@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final AeroCauseProperties props;
    private final EvidencePackageBuilder packageBuilder;
    private final BriefRepository briefRepo;
    private final IncidentRepository incidentRepo;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private volatile Instant lastSuccessAt;
    private volatile String lastFailure;

    public AiServiceClient(AeroCauseProperties props,
                           EvidencePackageBuilder packageBuilder,
                           BriefRepository briefRepo,
                           IncidentRepository incidentRepo,
                           AuditService auditService,
                           ObjectMapper objectMapper) {
        this.props = props;
        this.packageBuilder = packageBuilder;
        this.briefRepo = briefRepo;
        this.incidentRepo = incidentRepo;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.toIntExact(Duration.ofSeconds(
                Math.max(1, props.getAiService().getTimeoutSeconds())).toMillis());
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Transactional
    public Brief investigate(Incident incident) {
        Incident managedIncident = incidentRepo.findById(incident.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Incident not found: " + incident.getId()));
        String packageJson = packageBuilder.buildPackageJson(managedIncident);

        // Update incident status to UNDER_REVIEW
        managedIncident.setStatus(Incident.IncidentStatus.UNDER_REVIEW);
        incidentRepo.save(managedIncident);

        Brief brief = null;
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String url = props.getAiService().getBaseUrl() + "/investigate";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> request = new HttpEntity<>(packageJson, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new IllegalStateException("AI service returned an empty or unsuccessful response.");
                }
                Map<String, Object> body = objectMapper.readValue(response.getBody(), Map.class);
                String validatorStatus = (String) body.getOrDefault("validator_status", "PASSED");
                String llmMode = (String) body.getOrDefault("llm_mode", "MOCK");
                brief = Brief.builder()
                        .incident(incident)
                        .generatedAt(Instant.now())
                        .contentJson(response.getBody())
                        .validatorStatus(Brief.ValidatorStatus.valueOf(validatorStatus))
                        .llmMode(Brief.LlmMode.valueOf(llmMode))
                        .build();
                lastSuccessAt = Instant.now();
                lastFailure = null;
                break;
            } catch (Exception e) {
                lastError = e;
                lastFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("AI provider request failed (attempt {}/3): {}", attempt, lastFailure);
                auditService.log("AI_PROVIDER_FAILURE", incident.getId(), Map.of(
                        "attempt", attempt,
                        "maxAttempts", 3,
                        "error", lastFailure));
                if (attempt < 3) {
                    try {
                        Thread.sleep(150L << (attempt - 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        lastError = interrupted;
                        lastFailure = "Retry interrupted";
                        break;
                    }
                }
            }
        }
        if (brief == null) brief = buildTemplateBrief(managedIncident,
                lastError == null ? "AI provider unavailable" : lastError.getMessage());

        brief = briefRepo.save(brief);
        auditService.log("BRIEF_GENERATED", incident.getId(), Map.of(
                "validatorStatus", brief.getValidatorStatus().name(),
                "llmMode", brief.getLlmMode().name()
        ));
        return brief;
    }

    public Map<String, Object> providerStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("stale", lastFailure != null);
        status.put("lastSuccessAt", lastSuccessAt == null ? null : lastSuccessAt.toString());
        status.put("lastFailure", lastFailure);
        return status;
    }

    private Brief buildTemplateBrief(Incident incident, String reason) {
        Map<String, Object> content = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(incident.getSynthetic())) {
            content.put("dataNotice", "SAMPLE DATA (synthetic)");
        }
        content.put("decisionSupportNotice",
                "Decision support only. Final decisions belong to qualified officers.");
        content.put("templateFallback", true);
        content.put("providerFailure", reason == null ? "AI provider unavailable" : reason);
        content.put("validator_status", "TEMPLATE_FALLBACK");
        content.put("llm_mode", "MOCK");
        content.put("violations", List.of());
        content.put("retrieved_passages", List.of());
        content.put("brief", Map.of(
                "headline", "Brief not validated; manual verification required",
                "summary", "The AI provider could not produce a validated brief. Review the evidence package and verify observations independently. No source attribution is made.",
                "hypotheses", List.of(),
                "verification_steps", List.of("Review the evidence package and confirm observations independently."),
                "recommended_actions", List.of(),
                "unknowns", List.of("The AI provider was unavailable; this fallback brief was not validated."),
                "disclaimer", "Decision support only. Final verification and enforcement stay with authorised officials."
        ));

        try {
            return Brief.builder()
                    .incident(incident)
                    .generatedAt(Instant.now())
                    .contentJson(objectMapper.writeValueAsString(content))
                    .validatorStatus(Brief.ValidatorStatus.TEMPLATE_FALLBACK)
                    .llmMode(Brief.LlmMode.MOCK)
                    .build();
        } catch (Exception e) {
            return Brief.builder()
                    .incident(incident)
                    .generatedAt(Instant.now())
                    .contentJson("{\"error\":\"template_build_failed\"}")
                    .validatorStatus(Brief.ValidatorStatus.TEMPLATE_FALLBACK)
                    .llmMode(Brief.LlmMode.MOCK)
                    .build();
        }
    }
}
