package com.aerocause.repository;

import com.aerocause.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
    List<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
}
