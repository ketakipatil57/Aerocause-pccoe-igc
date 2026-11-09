package com.aerocause.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "readings",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"station_id", "pollutant", "observed_at"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Reading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, length = 20)
    private String pollutant;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "quality_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private QualityStatus qualityStatus = QualityStatus.GOOD;

    @Column(name = "episode_id")
    private Long episodeId;

    public enum QualityStatus {
        GOOD, SUSPECT, BAD
    }
}
