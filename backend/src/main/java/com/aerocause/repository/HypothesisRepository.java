package com.aerocause.repository;

import com.aerocause.domain.Hypothesis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HypothesisRepository extends JpaRepository<Hypothesis, Long> {
    List<Hypothesis> findByIncidentIdOrderByRankAsc(Long incidentId);
}
