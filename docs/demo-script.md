# AeroCause Demo Script

**Audience:** PCCOE-IGC 2026 judges  
**Duration:** ~10 minutes  
**Setup:** Backend + AI service (mock mode) running locally. Browser open to http://localhost:5173

---

## Setup (before demo)

```bash
# Terminal 1 — Backend
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2 — AI service (mock, no API key needed)
cd ai-service && LLM_MODE=mock uvicorn main:app --reload --port 8000

# Terminal 3 — Frontend
cd frontend && pnpm dev

# Terminal 4 — Import episodes
curl -X POST http://localhost:8080/api/replay/import-all
```

---

## Step 1: Show the Dashboard (1 min)

- Open http://localhost:5173
- Point out: **"SAMPLE DATA (synthetic)"** badge — data is not real
- Point out: **"Decision support only"** notice
- Show the 4 monitoring stations on the map (Vayu Nagar, imaginary city)

---

## Step 2: Replay the Dust Episode (2 min)

```bash
curl -X POST http://localhost:8080/api/replay/start \
  -d '{"episodeId":1,"speedMultiplier":120}'
```

- In the UI, watch the incident appear as readings are processed
- Click the incident — show evidence tab:
  - PM10 >> PM2.5 (ratio evidence)
  - High wind speed
  - No NO2/CO elevation
  - Evidence FOR dust, Evidence AGAINST combustion
- Top hypothesis: **DUST — HIGH confidence**

---

## Step 3: Generate AI Brief (2 min)

```bash
curl -X POST http://localhost:8080/api/incidents/1/investigate
```

- In the UI, show the generated brief:
  - Every claim has an evidence item ID (`[EV-003]`)
  - Every recommended action has a document citation (`[DOC-001 §3.2]`)
  - Brief says "MOCK LLM" — no fake API calls
  - Recommended next steps (verify upwind dust source, check road-sweeping logs)

---

## Step 4: Officer Review (1 min)

- Click **Review** tab → Select "Accept" → Enter name → Submit
- Show audit trail: every action recorded with timestamp

---

## Step 5: Sensor Fault Episode (1 min)

```bash
curl -X POST http://localhost:8080/api/replay/start \
  -d '{"episodeId":4,"speedMultiplier":120}'
```

- One station spikes; neighbours stay normal
- QC flag: ISOLATED_SPIKE
- Top hypothesis: **SENSOR_FAULT — HIGH confidence**
- Brief recommends: "Inspect sensor hardware, do not issue public advisory"

---

## Step 6: Ambiguous / INCONCLUSIVE Episode (1 min)

```bash
curl -X POST http://localhost:8080/api/replay/start \
  -d '{"episodeId":5,"speedMultiplier":120}'
```

- Evidence conflicts (some PM2.5, some NO2, weak wind signal)
- Top hypothesis: **INCONCLUSIVE**
- Missing evidence list: ["fire hotspot data not available", "only 1 upwind station"]
- Brief: "Insufficient evidence to determine source type. Recommended: deploy mobile monitor, request satellite data."

---

## Step 7: Citizen View (1 min)

```bash
open http://localhost:5173/citizen
```

- Shows AQI category and plain-language advice
- **Never** mentions specific factories, companies, or people
- Disclaimer: "This is not an official government reading."

---

## Step 8: Evaluation Page (1 min)

- Open http://localhost:5173/evaluation
- Shows all 5 episodes: expected vs actual hypothesis
- 4/5 pass (INCONCLUSIVE counts as pass for the ambiguous episode)

---

## Key Points to Emphasise

1. **LLM explains; code decides** — no hallucinated thresholds
2. **INCONCLUSIVE is a valid answer** — honest about uncertainty
3. **Full audit trail** — every decision traceable
4. **Mock mode** — full demo with zero API keys
5. **No specific entities named** — only source types
