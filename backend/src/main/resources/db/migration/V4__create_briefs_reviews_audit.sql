-- AeroCause Flyway Migration V4
-- Creates: briefs, reviews, audit_events

CREATE TABLE IF NOT EXISTS briefs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id      BIGINT      NOT NULL,
    generated_at     DATETIME(6) NOT NULL,
    content_json     TEXT        NOT NULL,
    validator_status VARCHAR(30) NOT NULL,
    llm_mode         VARCHAR(20) NOT NULL,
    CONSTRAINT fk_brief_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT chk_brief_validator CHECK (validator_status IN ('PASSED','RETRIED','TEMPLATE_FALLBACK')),
    CONSTRAINT chk_brief_llm_mode CHECK (llm_mode IN ('LIVE','MOCK'))
);

CREATE TABLE IF NOT EXISTS reviews (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT       NOT NULL,
    reviewer    VARCHAR(255) NOT NULL,
    decision    VARCHAR(30)  NOT NULL,
    comment     TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_review_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT chk_review_decision CHECK (decision IN ('ACCEPT','REJECT','NEEDS_MORE_EVIDENCE'))
);

CREATE TABLE IF NOT EXISTS audit_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT       NULL,
    event_type  VARCHAR(100) NOT NULL,
    payload_json TEXT        NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_incident
    ON audit_events (incident_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_event_type
    ON audit_events (event_type, created_at);
