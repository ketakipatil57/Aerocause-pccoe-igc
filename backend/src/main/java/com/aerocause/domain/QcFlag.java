package com.aerocause.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "qc_flags")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class QcFlag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, length = 20)
    private String pollutant;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private QcRule rule;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum QcRule {
        FLATLINE, OUT_OF_RANGE, ISOLATED_SPIKE, STALE_DATA, MISSING_DATA
    }

    public enum Severity {
        WARNING, ERROR
    }
}
