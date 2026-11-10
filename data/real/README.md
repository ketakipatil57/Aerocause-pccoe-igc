# Real episode imports

Place a readings CSV (and optionally a weather CSV) in this directory, then add
an entry to `manifest.json`. Paths are relative to this directory and cannot
escape it.

```json
{
  "episodes": [
    {
      "name": "Reviewed monitoring episode",
      "description": "Optional provenance and context.",
      "readings_file": "readings.csv",
      "weather_file": "weather.csv",
      "duration_hours": 24,
      "start_time": "2025-01-01T00:00:00Z"
    }
  ]
}
```

`readings_file` must contain `station_external_id`, `pollutant`, `value`, and
`observed_at` columns. Station identifiers must already exist in AeroCause.
Weather fields are optional; missing weather and fire-hotspot observations are
recorded as missing evidence, not treated as negative observations.

Import from the repository root while the backend is running:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/episodes/import-real
```

Real episodes are always stored with `synthetic=false`. Do not add expected
hypotheses or sensor-fault labels unless a human reviewer supplies them.
Leaving these fields out keeps them unlabelled for evaluation.
