package com.aerocause.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "episodes")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Episode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean synthetic = true;

    @Column(name = "expected_top_hypothesis", length = 50)
    private String expectedTopHypothesis;

    @Column(name = "expected_fault")
    @Builder.Default
    private Boolean expectedFault = false;

    @Column(name = "duration_hours")
    private Integer durationHours;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "readings_file", length = 500)
    private String readingsFile;

    @Column(name = "weather_file", length = 500)
    private String weatherFile;
}
