package com.aerocause.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "briefs")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Brief {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    private String contentJson;

    @Column(name = "validator_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ValidatorStatus validatorStatus;

    @Column(name = "llm_mode", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LlmMode llmMode;

    public enum ValidatorStatus { PASSED, RETRIED, TEMPLATE_FALLBACK }
    public enum LlmMode { LIVE, MOCK }
}
