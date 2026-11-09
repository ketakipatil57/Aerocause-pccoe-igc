package com.aerocause.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evidence_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EvidenceItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "data_json", nullable = false, columnDefinition = "TEXT")
    private String dataJson;

    @Column(name = "source_ref", nullable = false, length = 500)
    private String sourceRef;
}
