# AeroCause Synthetic Episode Data

## CSV Import Format

Each episode is a CSV file with the following columns:

### readings.csv columns
| Column | Type | Description |
|--------|------|-------------|
| station_external_id | string | Station identifier (matches stations table) |
| pollutant | string | PM25, PM10, NO2, SO2, CO, O3 |
| value | float | Measured value |
| unit | string | µg/m³ or ppm |
| observed_at | ISO-8601 | Measurement timestamp (UTC) |
| source | string | Always "SYNTHETIC" for generated data |

### weather.csv columns
| Column | Type | Description |
|--------|------|-------------|
| station_external_id | string | Station identifier |
| observed_at | ISO-8601 | Timestamp (UTC) |
| wind_speed | float | m/s |
| wind_dir_deg | float | Degrees (0=North, 90=East) |
| boundary_layer_height | float or empty | Meters (optional) |
| source | string | Always "SYNTHETIC" |

### episodes_metadata.json
```json
{
  "episodes": [{
    "id": 1,
    "name": "Dust Storm Event",
    "description": "...",
    "synthetic": true,
    "expected_top_hypothesis": "DUST",
    "expected_fault": false,
    "stations": ["STN-001", "STN-002", "STN-003", "STN-004"],
    "duration_hours": 24,
    "readings_file": "episodes/episode_01_dust/readings.csv",
    "weather_file": "episodes/episode_01_dust/weather.csv"
  }]
}
```

## Notes
- ALL data is synthetic and labelled as such in every API response and UI view.
- Noise is added to make data realistic (diurnal cycles, sensor jitter).
- Hotspot data is simulated as a flag column in weather.csv when needed.
