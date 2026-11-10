-- AeroCause Flyway Migration V3
-- Creates: incidents, evidence_items, hypotheses, documents, citations

CREATE TABLE IF NOT EXISTS incidents (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id     BIGINT       NOT NULL,
    pollutant      VARCHAR(20)  NOT NULL,
    detected_at    DATETIME(6)  NOT NULL,
    window_start   DATETIME(6)  NOT NULL,
    window_end     DATETIME(6)  NOT NULL,
    peak_value     DOUBLE       NOT NULL,
    baseline_value DOUBLE       NOT NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'NEW',
    episode_id     BIGINT       NULL,
    synthetic      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_incident_station FOREIGN KEY (station_id) REFERENCES stations(id),
    CONSTRAINT chk_incident_status CHECK (status IN ('NEW','UNDER_REVIEW','CONFIRMED','REJECTED','INCONCLUSIVE'))
);

CREATE INDEX idx_incidents_station_detected
    ON incidents (station_id, detected_at);
CREATE INDEX idx_incidents_status
    ON incidents (status);

CREATE TABLE IF NOT EXISTS evidence_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT       NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    summary     VARCHAR(1000) NOT NULL,
    data_json   TEXT         NOT NULL,
    source_ref  VARCHAR(500) NOT NULL,
    CONSTRAINT fk_evidence_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
);

CREATE INDEX idx_evidence_incident
    ON evidence_items (incident_id);

CREATE TABLE IF NOT EXISTS hypotheses (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id          BIGINT       NOT NULL,
    type                 VARCHAR(50)  NOT NULL,
    score                DOUBLE       NOT NULL,
    confidence_label     VARCHAR(20)  NOT NULL,
    evidence_for_json    TEXT         NOT NULL,
    evidence_against_json TEXT        NOT NULL,
    missing_json         TEXT         NOT NULL,
    rank                 INT          NOT NULL,
    CONSTRAINT fk_hypothesis_incident FOREIGN KEY (incident_id) REFERENCES incidents(id),
    CONSTRAINT chk_hypothesis_confidence CHECK (confidence_label IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_hypothesis_type CHECK (type IN (
        'SENSOR_FAULT','DUST','COMBUSTION_TRAFFIC','BIOMASS_BURNING',
        'INDUSTRIAL','STAGNANT_WEATHER','INCONCLUSIVE'
    ))
);

CREATE INDEX idx_hypotheses_incident
    ON hypotheses (incident_id, rank);

CREATE TABLE IF NOT EXISTS documents (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_key   VARCHAR(100)  NOT NULL,
    title     VARCHAR(500)  NOT NULL,
    publisher VARCHAR(255)  NOT NULL,
    version   VARCHAR(50)   NULL,
    url       VARCHAR(1000) NULL,
    added_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_documents_doc_key UNIQUE (doc_key)
);

CREATE TABLE IF NOT EXISTS citations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id     BIGINT       NOT NULL,
    hypothesis_id   BIGINT       NULL,
    document_id     BIGINT       NOT NULL,
    passage_ref     VARCHAR(100) NOT NULL,
    excerpt         TEXT         NULL,
    CONSTRAINT fk_citation_incident  FOREIGN KEY (incident_id)   REFERENCES incidents(id),
    CONSTRAINT fk_citation_hypothesis FOREIGN KEY (hypothesis_id) REFERENCES hypotheses(id),
    CONSTRAINT fk_citation_document  FOREIGN KEY (document_id)   REFERENCES documents(id)
);
