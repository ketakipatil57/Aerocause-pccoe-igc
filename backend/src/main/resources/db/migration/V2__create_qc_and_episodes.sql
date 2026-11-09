-- AeroCause Flyway Migration V2
-- Creates: qc_flags, episodes tables

CREATE TABLE IF NOT EXISTS episodes (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                      VARCHAR(255) NOT NULL,
    description               TEXT         NULL,
    synthetic                 BOOLEAN      NOT NULL DEFAULT TRUE,
    expected_top_hypothesis   VARCHAR(50)  NULL,
    expected_fault            BOOLEAN      NOT NULL DEFAULT FALSE,
    duration_hours            INT          NULL,
    start_time                DATETIME(6)  NULL,
    readings_file             VARCHAR(500) NULL,
    weather_file              VARCHAR(500) NULL
);

CREATE TABLE IF NOT EXISTS qc_flags (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id   BIGINT       NOT NULL,
    pollutant    VARCHAR(20)  NOT NULL,
    window_start DATETIME(6)  NOT NULL,
    window_end   DATETIME(6)  NOT NULL,
    rule         VARCHAR(50)  NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    detail       TEXT         NOT NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_qc_flag_station FOREIGN KEY (station_id) REFERENCES stations(id)
);

CREATE INDEX IF NOT EXISTS idx_qc_flags_station_pollutant
    ON qc_flags (station_id, pollutant, window_start);
