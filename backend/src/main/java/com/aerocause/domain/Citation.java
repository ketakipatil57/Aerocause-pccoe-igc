package com.aerocause.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "citations")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Citation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hypothesis_id")
    private Hypothesis hypothesis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "passage_ref", nullable = false, length = 100)
    private String passageRef;

    @Column(columnDefinition = "TEXT")
    private String excerpt;
}
