package com.aerocause.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "incidents")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Incident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, length = 20)
    private String pollutant;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "peak_value", nullable = false)
    private Double peakValue;

    @Column(name = "baseline_value", nullable = false)
    private Double baselineValue;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.NEW;

    @Column(name = "episode_id")
    private Long episodeId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean synthetic = false;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Hypothesis> hypotheses;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EvidenceItem> evidenceItems;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Brief> briefs;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Review> reviews;

    public enum IncidentStatus {
        NEW, UNDER_REVIEW, CONFIRMED, REJECTED, INCONCLUSIVE
    }
}
