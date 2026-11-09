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
List all episodes.

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
  "incidentsCreated": 1
}
```

### POST /api/replay/stop

### POST /api/replay/import-all
Imports all episode CSVs from /data/episodes/ into the database.

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

---

## Health & Freshness

### GET /api/health/sources
```json
{
  "openaq": { "lastFetch": "2024-01-15T08:00:00Z", "stale": false, "staleSince": null },
  "openMeteo": { "lastFetch": "2024-01-15T08:00:00Z", "stale": false },
  "firms": { "enabled": false }
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

### POST /api/evaluation/run
Triggers replay of all episodes and compares expected vs actual top hypothesis.

### GET /api/evaluation/latest
```json
{
  "runAt": "2024-01-15T09:00:00Z",
  "episodes": [{
    "episodeId": 1,
    "name": "Dust Event",
    "expected": "DUST",
    "actual": "DUST",
    "pass": true,
    "confidence": "HIGH"
  }],
  "summary": { "total": 5, "pass": 4, "fail": 1 }
}
```
