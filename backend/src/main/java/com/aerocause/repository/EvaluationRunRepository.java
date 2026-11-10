package com.aerocause.repository;

import com.aerocause.domain.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {
    Optional<EvaluationRun> findTopByOrderByRunAtDesc();
}
