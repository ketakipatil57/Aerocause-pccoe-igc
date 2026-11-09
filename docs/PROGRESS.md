# AeroCause — Progress Tracker

> This file is the single source of truth for project state.
> Updated after every task. A new agent with zero context can continue from here.

---

## Status: PHASE 3 COMPLETE — FRONTEND IMPLEMENTED

---

## ✅ DONE and Working
- Repository structure created
- Root config files: .gitignore, .env.example, docker-compose.yml, README.md
- Docs: architecture.md, api-contract.md, demo-script.md, PROGRESS.md
- Spring Boot ingestion, replay, QC, anomaly, fingerprinting, AQI, and evidence-package services
- Flyway schema and seed data validated against the H2 test profile
- Backend unit and integration test suites
- FastAPI `/health`, `/investigate`, `/citizen-summary`, and `/retrieve` endpoints
- Mock and OpenAI-compatible live LLM providers, brief validator, retry, and template fallback
- User-managed manifest corpus with PDF/TXT/MD parsing, embeddings, persisted ChromaDB, and retrieval tests
- AI service API, Docker, configuration, and test documentation
- AI service verification: 24 tests pass and Ruff reports no findings
- React/Vite civic-tech frontend with Incident Desk, incident detail, replay, evaluation, citizen view, and Sources & Method
- Frontend mock fixtures are clearly labelled; replay-to-review flow and language templates are covered by tests
- Incident detail API now includes related QC flags and citation metadata for the Evidence & QC view
- Frontend verification: 3 component/flow tests pass and production build succeeds

## 🔄 IN PROGRESS
- Docker/browser end-to-end verification with live backend and AI containers

## ❌ NOT STARTED
- Capture and review demo screenshots against a full Compose deployment

---

## Exact Commands to Run

```bash
# 1. Generate synthetic data
cd data && pip install -r requirements.txt && python generate_sample_episodes.py

# 2. Run backend (H2, no MySQL)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Run backend tests
cd backend && mvn test

# 4. Import episodes via API
curl -X POST http://localhost:8080/api/replay/import-all

# 5. Run all 5 episode replays for evaluation
curl -X POST http://localhost:8080/api/evaluation/run
```

---

## Known Bugs / Issues
- Live OpenAQ/Open-Meteo polling remains intentionally disabled for the prototype; replay is the supported ingestion source.
- `/api/evaluation/run` still returns guidance rather than running all episodes automatically.
- No official documents are bundled in the AI corpus; citations appear only after the user supplies and indexes reviewed sources.
- Proper-noun detection and action-to-passage grounding are heuristic and require human review.
- AI service tests ran on the available Python 3.14 runtime, not the target Python 3.11; the Docker daemon was unavailable for an image build.
- Docker daemon is unavailable in this environment, so full Compose startup and live-service browser validation remain unverified. Frontend was exercised in browser using its labelled mock-data mode.
- AQI breakpoints are configurable heuristics and require official-source verification; citizen precaution text is an explicit placeholder.

---

## Next 3 Tasks (in order)
1. Run the full Docker Compose stack and validate live API integration.
2. Capture and review the Incident Desk, Incident Detail, Evaluation, and Citizen View screenshots.
3. Verify all five synthetic replay outcomes against expected hypotheses.
