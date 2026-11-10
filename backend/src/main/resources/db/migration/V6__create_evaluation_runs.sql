CREATE TABLE IF NOT EXISTS evaluation_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_at DATETIME(6) NOT NULL,
    results_json TEXT NOT NULL,
    build_pass BOOLEAN NOT NULL
);

ALTER TABLE episodes MODIFY COLUMN expected_fault BOOLEAN NULL;
ALTER TABLE weather_obs MODIFY COLUMN fire_hotspot_upwind BOOLEAN NULL;
