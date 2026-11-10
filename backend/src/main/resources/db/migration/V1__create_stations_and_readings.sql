-- AeroCause Flyway Migration V1
-- Creates core monitoring tables: stations, readings, raw_payloads, weather_obs
-- Compatible with H2 (dev/test) and MySQL (prod).
-- Uses DATETIME instead of DATETIME(6) for H2 compatibility.

CREATE TABLE IF NOT EXISTS stations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    city        VARCHAR(255) NOT NULL,
    lat         DOUBLE       NOT NULL,
    lon         DOUBLE       NOT NULL,
    provider    VARCHAR(100) NOT NULL,
    CONSTRAINT uq_stations_external_id UNIQUE (external_id)
);

CREATE TABLE IF NOT EXISTS raw_payloads (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    source       VARCHAR(100) NOT NULL,
    fetched_at   DATETIME     NOT NULL,
    payload_json TEXT         NOT NULL
);

CREATE TABLE IF NOT EXISTS readings (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id     BIGINT       NOT NULL,
    pollutant      VARCHAR(20)  NOT NULL,
    reading_value  DOUBLE       NOT NULL,
    unit           VARCHAR(20)  NOT NULL,
    observed_at    DATETIME     NOT NULL,
    fetched_at     DATETIME     NOT NULL,
    source         VARCHAR(100) NOT NULL,
    quality_status VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    episode_id     BIGINT       NULL,
    CONSTRAINT uq_readings_station_pollutant_observed UNIQUE (station_id, pollutant, observed_at),
    CONSTRAINT fk_readings_station FOREIGN KEY (station_id) REFERENCES stations(id)
);

CREATE INDEX idx_readings_station_pollutant_observed
    ON readings (station_id, pollutant, observed_at);

CREATE TABLE IF NOT EXISTS weather_obs (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id             BIGINT      NOT NULL,
    observed_at            DATETIME    NOT NULL,
    wind_speed             DOUBLE      NULL,
    wind_dir_deg           DOUBLE      NULL,
    boundary_layer_height  DOUBLE      NULL,
    fire_hotspot_upwind    BOOLEAN     NOT NULL DEFAULT FALSE,
    source                 VARCHAR(100) NOT NULL,
    CONSTRAINT uq_weather_station_observed UNIQUE (station_id, observed_at),
    CONSTRAINT fk_weather_station FOREIGN KEY (station_id) REFERENCES stations(id)
);

CREATE INDEX idx_weather_station_observed
    ON weather_obs (station_id, observed_at);
