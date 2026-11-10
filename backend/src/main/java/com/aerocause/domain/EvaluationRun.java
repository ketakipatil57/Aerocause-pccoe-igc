package com.aerocause.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "evaluation_runs")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EvaluationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(name = "results_json", nullable = false, columnDefinition = "TEXT")
    private String resultsJson;

    @Column(name = "build_pass", nullable = false)
    private Boolean buildPass;
}
