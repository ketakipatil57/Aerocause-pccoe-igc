# AeroCause — API Contract

Base URL: `http://localhost:8080`  
OpenAPI (Swagger UI): `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## Stations

### GET /api/stations
Returns all configured monitoring stations.

**Response:**
```json
[{
  "id": 1,
  "externalId": "IN-MH-PUNE-001",
  "name": "Shivajinagar",
  "city": "Vayu Nagar",
  "lat": 18.5204,
  "lon": 73.8567,
  "provider": "SYNTHETIC"
}]
```

### GET /api/stations/{id}/readings
Query readings for a station.

**Query params:** `from` (ISO-8601), `to` (ISO-8601), `pollutant` (optional)

**Response:**
```json
[{
  "id": 1,
  "pollutant": "PM25",
  "value": 87.3,
  "unit": "µg/m³",
  "observedAt": "2024-01-15T08:00:00Z",
  "fetchedAt": "2024-01-15T08:05:00Z",
  "source": "SYNTHETIC",
  "qualityStatus": "GOOD",
  "synthetic": true
}]
```

---

## Episodes

### GET /api/episodes
List all episodes. Real episodes have `synthetic: false`, `expectedTopHypothesis: null`, and `expectedFault: null` unless a human supplied labels.

### GET /api/episodes/{id}
Get episode detail including expected outcomes.

---

## Replay

### POST /api/replay/start
```json
{ "episodeId": 1, "speedMultiplier": 60 }
```
Feeds synthetic episode through the same pipeline as live data.
`speedMultiplier` must be at least `1`. Missing episode files fail visibly and are recorded as a `REPLAY_FAILED` audit event. Malformed CSV rows are logged and skipped.

### GET /api/replay/status
```json
{
  "running": true,
  "episodeId": 1,
  "progress": 0.42,
  "readingsProcessed": 84,
  "incidentsCreated": 1,
  "error": null
}
```

### POST /api/replay/stop

### POST /api/replay/import-all
Returns the pre-seeded synthetic episode catalogue.

### POST /api/episodes/import-real
Imports entries in `data/real/manifest.json`. Every imported episode is forced to `synthetic: false`; omitted expected labels remain null. File paths are restricted to `data/real/`.

---

## Incidents

### GET /api/incidents
**Query:** `status` (optional), `stationId` (optional)

Each incident summary includes its `topHypothesis` (when computed) and the `qcFlags` in that incident's pollutant window. Incident detail returns bounded JSON views of the incident, hypotheses, evidence items, QC flags, latest brief, citations, and reviews; related objects do not recursively embed their parent.

### GET /api/incidents/{id}
Full incident detail including evidence items, hypotheses, brief (if generated), citations, reviews.

**Response includes:**
```json
{
  "id": 1,
  "synthetic": true,
  "status": "CONFIRMED",
  "topHypothesis": {
    "type": "DUST",
    "score": 0.72,
    "confidenceLabel": "HIGH",
    "evidenceFor": [...],
    "evidenceAgainst": [...],
    "missing": [...]
  },
  "qcFlags": [{
    "rule": "ISOLATED_SPIKE",
    "severity": "WARNING",
    "detail": "Reading differs from nearby station pattern."
  }],
  "citations": [],
  "brief": { ... },
  "dataNotice": "SAMPLE DATA (synthetic)"
}
```

### POST /api/incidents/{id}/investigate
Calls the AI service to generate a brief. No body required.

### POST /api/incidents/{id}/review
```json
{
  "reviewer": "Officer Name",
  "decision": "ACCEPT",
  "comment": "Consistent with field observations."
}
```
`decision` must be one of: `ACCEPT`, `REJECT`, `NEEDS_MORE_EVIDENCE`

### GET /api/incidents/{id}/audit
Returns all audit_events for this incident.

## Evaluation

### POST /api/evaluation/run
Replays every configured synthetic and real episode sequentially, generates a brief for the latest incident in each episode, computes evaluation metrics, stores the result in `evaluation_runs`, and exports `docs/evaluation-results.csv` and `docs/evaluation-results.md`.

The response contains `episodes`, plus `syntheticEpisodes` and `realEpisodes` views, summary counts, `buildPass`, `unsupportedClaimsFinalTotal`, validator availability, and citation validity. Per-episode metrics include expected/actual top hypothesis, sensor-fault flag, first-attempt validator-violation count, final validator violations (reported as unsupported claims), citation validity, and milliseconds from incident creation to brief generation. Expected labels for unlabelled real episodes remain empty and are excluded from expected-vs-actual scoring.

HTTP `200` means the evaluation build gate passed. HTTP `422` means the run was stored/exported but the final brief gate failed (unsupported/final validator violations, missing validator output, or invalid citation reference). HTTP `409` means an existing replay is running or an episode replay could not start.

### GET /api/evaluation/latest
Returns the most recently persisted run, or empty episode lists before the first run.

Run the same gate from the repository root with `py backend/scripts/run_evaluation.py`; the CLI exits nonzero when the build gate fails.

## AI service response metadata

`POST /investigate` returns `validator_status`, `llm_mode`, `first_attempt_violations`, `final_violations`, `violations`, and `retrieved_passages`. `violations` preserves issues encountered in failed attempts/fallback; `final_violations` describes the final brief and is the evaluation build-gate basis.

---

## Health & Freshness

### GET /api/health/sources
```json
{
  "openaq": { "enabled": false, "note": "Live polling disabled; use replay" },
  "openMeteo": { "enabled": false, "note": "Live polling disabled; use replay" },
  "firms": { "enabled": false },
  "aiService": {
    "stale": false,
    "lastSuccessAt": null,
    "lastFailure": null
  },
  "replay": { "running": false, "episodeId": "none" },
  "dataNotice": "SAMPLE DATA (synthetic) — no live sources connected"
}
```

---

## Citizen API

### GET /api/citizen/aqi?stationId={id}
Safe, plain-language AQI summary. Never names specific polluters.

```json
{
  "stationName": "Shivajinagar",
  "aqi": 156,
  "category": "Poor",
  "dominantPollutant": "PM2.5",
  "advice": "Consider limiting prolonged outdoor activity.",
  "observedAt": "2024-01-15T08:00:00Z",
  "dataNotice": "SAMPLE DATA (synthetic)",
  "disclaimer": "Decision support only. This is not an official government reading."
}
```

---

## Evaluation
