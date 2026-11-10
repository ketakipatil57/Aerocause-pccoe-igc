INSERT INTO episodes
    (name, description, synthetic, expected_top_hypothesis, expected_fault,
     duration_hours, start_time, readings_file, weather_file)
VALUES
    ('Dust Storm Event', 'Synthetic dust-like episode for the recommended demo path.',
     TRUE, 'DUST', FALSE, 6,
     '2024-03-15 00:00:00',
     'episodes/episode_01_dust/readings.csv', 'episodes/episode_01_dust/weather.csv'),
    ('Combustion / Traffic Event', 'Synthetic mixed-pollutant episode.',
     TRUE, 'COMBUSTION_TRAFFIC', FALSE, 6,
     '2024-02-20 14:00:00',
     'episodes/episode_02_combustion/readings.csv', 'episodes/episode_02_combustion/weather.csv'),
    ('Biomass Burning Event', 'Synthetic particulate episode with a simulated hotspot indicator.',
     TRUE, 'BIOMASS_BURNING', FALSE, 6,
     '2024-10-25 02:00:00',
     'episodes/episode_03_biomass/readings.csv', 'episodes/episode_03_biomass/weather.csv'),
    ('Sensor Fault (STN-001)', 'Synthetic sensor fault episode.',
     TRUE, 'SENSOR_FAULT', TRUE, 8,
     '2024-05-10 06:00:00',
     'episodes/episode_04_sensor_fault/readings.csv', 'episodes/episode_04_sensor_fault/weather.csv'),
    ('Ambiguous Episode (INCONCLUSIVE)', 'Synthetic episode with conflicting evidence.',
     TRUE, 'INCONCLUSIVE', FALSE, 6,
     '2024-07-08 10:00:00',
     'episodes/episode_05_inconclusive/readings.csv', 'episodes/episode_05_inconclusive/weather.csv');
