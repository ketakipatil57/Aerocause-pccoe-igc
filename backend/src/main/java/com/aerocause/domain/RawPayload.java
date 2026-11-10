package com.aerocause.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "raw_payloads")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RawPayload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
}
