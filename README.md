# AeroCause — AI-Assisted Air Quality Decision Support

> **Decision support only. Final verification and enforcement decisions belong to qualified officers.**

AeroCause is a hackathon prototype (PCCOE-IGC 2026) that works *alongside* existing monitoring systems (CPCB, OpenAQ, etc.). It detects anomalies in air-quality data, identifies likely pollution-source *types* (never specific entities), retrieves relevant official guidance, and produces auditable officer briefs.

---

## Architecture

```
readings (live/replay)
   │
   ▼
QC rules ──► qc_flags
   │
   ▼
Anomaly detection ──► incidents
   │
   ▼
Fingerprinting (rule-based) ──► hypotheses + evidence_items
   │
   ▼
AI Service (FastAPI) ──► RAG retrieval + LLM narration + validator ──► briefs
   │
   ▼
Officer review ──► reviews + audit_events
   │
   ▼
Citizen API
```

Three services:
| Service | Stack | Port |
|---------|-------|------|
| `/backend` | Spring Boot 3, Java 17, Maven | 8080 |
| `/ai-service` | Python 3.11, FastAPI, ChromaDB | 8000 |
| `/frontend` | React + Vite + TypeScript, Tailwind | 5173 |

---

## Quick Start (Local Dev — no Docker needed)

### Prerequisites
- Java 17+
- Maven 3.9+
- Python 3.11+
- Node.js 20+ and pnpm

### 1. Clone and configure
```bash
git clone <repo-url>
cd pccoe_igc
cp .env.example .env
# No changes needed for demo — defaults use H2 (in-memory DB) and mock LLM
```

### 2. Generate synthetic episode data
```bash
cd data
pip install -r requirements.txt
python generate_sample_episodes.py
```
This creates `data/episodes/` with 5 CSV files and `episodes_metadata.json`.

### 3. Start the backend (H2, no MySQL needed)
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# API available at http://localhost:8080
# H2 console at http://localhost:8080/h2-console (jdbc:h2:mem:aerocause)
# OpenAPI at http://localhost:8080/swagger-ui.html
```

### 4. Start the AI service (mock LLM, no API key needed)
```bash
cd ai-service
pip install -r requirements.txt
python -m uvicorn ai_service.main:app --reload --port 8000
```

### 5. Start the frontend
```bash
cd frontend
pnpm install
pnpm dev
# UI at http://localhost:5173
```

### 6. Import episodes and run a replay
```bash
# Import all episodes (the backend must be running)
curl -X POST http://localhost:8080/api/replay/import-all

# Start replay of the dust episode (speedMultiplier=60 means 1 real minute = 1 simulated hour)
curl -X POST http://localhost:8080/api/replay/start \
  -H "Content-Type: application/json" \
  -d '{"episodeId": 1, "speedMultiplier": 60}'

# Check status
curl http://localhost:8080/api/replay/status
```

---

## Docker Compose (all services)

```bash
cp .env.example .env
# Edit .env to set MYSQL_PASSWORD, etc.
docker compose up --build
```

Services:
- Backend: http://localhost:8080
- AI Service: http://localhost:8000
- Frontend: http://localhost:5173
- MySQL: localhost:3306

---

## Running Tests

```bash
# Backend unit + integration tests
cd backend
mvn test

# AI service tests
cd ai-service
pytest tests/ -v

# Frontend tests
cd frontend
pnpm test
```

---

## Demo Script
See `/docs/demo-script.md` for the step-by-step demo walkthrough.

---

## Important Notices

- All data shown in the demo is **SAMPLE DATA (synthetic)** — clearly labelled in the UI and API responses.
- The LLM explains structured evidence produced by deterministic code. It is **not** the source of truth for readings, thresholds, or regulations.
- Source types (Dust, Combustion/Traffic, etc.) are assigned by rule-based fingerprinting. **No specific factory, company, road, or person is ever named.**
- **Decision support only.** This tool does not make enforcement decisions.
