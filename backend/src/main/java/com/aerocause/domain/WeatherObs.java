package com.aerocause.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "weather_obs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"station_id", "observed_at"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WeatherObs {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_dir_deg")
    private Double windDirDeg;

    @Column(name = "boundary_layer_height")
    private Double boundaryLayerHeight;

    @Column(name = "fire_hotspot_upwind", nullable = false)
    @Builder.Default
    private Boolean fireHotspotUpwind = false;

    @Column(nullable = false, length = 100)
    private String source;
}
