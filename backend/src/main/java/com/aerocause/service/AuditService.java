package com.aerocause.service;

import com.aerocause.domain.AuditEvent;
import com.aerocause.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;

@Service
public class AuditService {

    private final AuditEventRepository auditRepo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void log(String eventType, Long incidentId, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize audit event payload", e);
        }
        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .incidentId(incidentId)
                .payloadJson(json)
                .createdAt(Instant.now())
                .build();
        auditRepo.save(event);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditEvent> getForIncident(Long incidentId) {
        return auditRepo.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }
}
