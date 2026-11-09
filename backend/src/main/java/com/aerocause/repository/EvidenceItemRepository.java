package com.aerocause.repository;

import com.aerocause.domain.EvidenceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, Long> {
    List<EvidenceItem> findByIncidentId(Long incidentId);
}
