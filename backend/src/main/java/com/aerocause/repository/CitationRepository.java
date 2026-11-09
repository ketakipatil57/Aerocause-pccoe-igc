package com.aerocause.repository;

import com.aerocause.domain.Citation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CitationRepository extends JpaRepository<Citation, Long> {
    List<Citation> findByIncidentId(Long incidentId);
}
