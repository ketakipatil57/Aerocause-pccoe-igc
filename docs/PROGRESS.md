# AeroCause — Progress Tracker

> This file is the single source of truth for project state.
> Updated after every task. A new agent with zero context can continue from here.

---

## Status: PHASE 1 IN PROGRESS — BACKEND SERVICE LAYER COMPLETE

---

## ✅ DONE and Working
- Repository structure created
- Root config files: .gitignore, .env.example, docker-compose.yml, README.md
- Docs: architecture.md, api-contract.md, demo-script.md, PROGRESS.md
- Spring Boot ingestion, replay, QC, anomaly, fingerprinting, AQI, and evidence-package services
- Flyway schema and seed data validated against the H2 test profile
- Backend unit and integration test suites

## 🔄 IN PROGRESS
- Synthetic episode replay validation and end-to-end evaluation behavior

## ❌ NOT STARTED
- AI service (FastAPI, ChromaDB, RAG, mock LLM)
- Frontend (React, Vite, TypeScript, Tailwind)

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

---

## Next 3 Tasks (in order)
1. Generate synthetic data and verify expected outcomes for all five replays.
2. Scaffold AI service: FastAPI, ChromaDB, mock LLM, validator.
3. Scaffold frontend: React, Vite, TypeScript, Tailwind.
