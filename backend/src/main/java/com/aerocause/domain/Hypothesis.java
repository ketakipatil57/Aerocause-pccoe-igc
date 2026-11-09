package com.aerocause.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hypotheses")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Hypothesis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private HypothesisType type;

    @Column(nullable = false)
    private Double score;

    @Column(name = "confidence_label", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConfidenceLabel confidenceLabel;

    @Column(name = "evidence_for_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceForJson;

    @Column(name = "evidence_against_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceAgainstJson;

    @Column(name = "missing_json", nullable = false, columnDefinition = "TEXT")
    private String missingJson;

    @Column(nullable = false)
    private Integer rank;

    public enum HypothesisType {
        SENSOR_FAULT, DUST, COMBUSTION_TRAFFIC, BIOMASS_BURNING,
        INDUSTRIAL, STAGNANT_WEATHER, INCONCLUSIVE
    }

    public enum ConfidenceLabel {
        LOW, MEDIUM, HIGH
    }
}
