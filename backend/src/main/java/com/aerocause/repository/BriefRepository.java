package com.aerocause.repository;

import com.aerocause.domain.Brief;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BriefRepository extends JpaRepository<Brief, Long> {
    Optional<Brief> findTopByIncidentIdOrderByGeneratedAtDesc(Long incidentId);
    List<Brief> findByIncidentIdOrderByGeneratedAtDesc(Long incidentId);
}
