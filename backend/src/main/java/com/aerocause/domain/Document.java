package com.aerocause.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "documents")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_key", nullable = false, unique = true, length = 100)
    private String docKey;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 255)
    private String publisher;

    @Column(length = 50)
    private String version;

    @Column(length = 1000)
    private String url;

    @Column(name = "added_at", nullable = false)
    @Builder.Default
    private Instant addedAt = Instant.now();
}
