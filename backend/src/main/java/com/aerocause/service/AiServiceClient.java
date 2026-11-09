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
import java.time.Instant;
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
        this.restTemplate = new RestTemplate();
    }

    @Transactional
    public Brief investigate(Incident incident) {
        String packageJson = packageBuilder.buildPackageJson(incident);

        // Update incident status to UNDER_REVIEW
        incident.setStatus(Incident.IncidentStatus.UNDER_REVIEW);
        incidentRepo.save(incident);

        Brief brief;
        try {
            String url = props.getAiService().getBaseUrl() + "/investigate";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(packageJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
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
            } else {
                brief = buildTemplateBrief(incident, "HTTP error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("AI service unreachable: {}. Using template fallback.", e.getMessage());
            brief = buildTemplateBrief(incident, e.getMessage());
        }

        brief = briefRepo.save(brief);
        auditService.log("BRIEF_GENERATED", incident.getId(), Map.of(
                "validatorStatus", brief.getValidatorStatus().name(),
                "llmMode", brief.getLlmMode().name()
        ));
        return brief;
    }

    private Brief buildTemplateBrief(Incident incident, String reason) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dataNotice", "SAMPLE DATA (synthetic)");
        content.put("decisionSupportNotice",
                "Decision support only. Final decisions belong to qualified officers.");
        content.put("templateFallback", true);
        content.put("reason", reason);
        content.put("incidentId", incident.getId());
        content.put("summary",
                "AI service unavailable. The incident has been logged with the following data: " +
                "station=" + incident.getStation().getExternalId() +
                ", pollutant=" + incident.getPollutant() +
                ", peakValue=" + incident.getPeakValue() +
                " µg/m³. Please review the evidence items and hypotheses manually.");
        content.put("nextSteps", List.of(
                "Review evidence items in the incident detail view.",
                "Consult the top hypothesis and its confidence label.",
                "Refer to official CPCB guidance documents.",
                "Make a decision using the officer review form."
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
