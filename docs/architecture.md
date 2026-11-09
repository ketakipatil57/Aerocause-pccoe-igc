# AeroCause — Architecture

## Overview

AeroCause is a three-service monorepo. Each service has a single responsibility. Services communicate only via REST HTTP (no message queues, no WebSockets). Polling is used for UI refresh.

## Services

### Backend (Spring Boot 3 / Java 17)
- Ingestion: pulls from OpenAQ (live) or replays synthetic CSV data
- QC: applies quality-control rules, flags suspect readings
- Anomaly detection: MAD-based robust z-score over 14-day baselines
- Fingerprinting: rule-based source-type hypothesis scoring
- Evidence builder: assembles structured JSON for the AI service
- REST API: all data access, replay control, review submission
- Storage: MySQL (prod) / H2 (dev)
- Flyway migrations for schema versioning

### AI Service (FastAPI / Python 3.11)
- RAG: user-managed PDF/TXT/MD documents listed in `/corpus/manifest.json`, chunked with page/section references and indexed in persisted ChromaDB
- Narration: deterministic MOCK provider by default or an OpenAI-compatible LIVE provider
- Validator: checks evidence and passage references, package hypothesis order, unsupported numbers, unverified proper nouns, uncited actions, and action-to-passage overlap
- Fallback: one validator-guided retry followed by a deterministic template brief
- Citizen summaries use language templates and only caller-supplied precaution text

### Frontend (React / Vite / TypeScript)
- Officer dashboard: incidents list, incident detail, review form
- Citizen page: safe, plain-language AQI explanation
- Evaluation page: episode replay results with expected vs actual
- All synthetic data labelled with "SAMPLE DATA (synthetic)" badge

## Data Flow

```
External APIs / CSV replay
        │
        ▼
  ┌─────────────┐
  │  Ingestion  │──► raw_payloads, readings, weather_obs
  └─────────────┘
        │
        ▼
  ┌─────────────┐
  │  QC Rules   │──► qc_flags, quality_status on readings
  └─────────────┘
        │ (non-SUSPECT or suspected fault)
        ▼
  ┌──────────────────┐
  │ Anomaly Detection│──► incidents
  └──────────────────┘
        │
        ▼
  ┌───────────────┐
  │ Fingerprinting│──► hypotheses, evidence_items
  └───────────────┘
        │
        ▼
  ┌────────────────────┐
  │ Evidence Package   │──► JSON blob
  └────────────────────┘
        │
        ▼ POST /investigate
  ┌──────────────┐
  │  AI Service  │──► retrieval, narration, validation ──► briefs, citations
  └──────────────┘
        │
        ▼
  ┌──────────────┐
  │ Officer Review│──► reviews, audit_events
  └──────────────┘
        │
        ▼
  ┌──────────────┐
  │  Citizen API │──► safe AQI explanation
  └──────────────┘
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| LLM explains; code decides | Prevents hallucinated thresholds or fake regulations |
| Mock LLM mode | Full demo without any API key |
| H2 dev profile | Instant startup; no DB server needed for dev/test |
| Idempotent upserts | Re-running ingestion is safe; raw payloads preserved |
| Config-driven thresholds | Heuristics can be tuned without code changes |
| INCONCLUSIVE is a valid answer | Prevents overconfident incorrect diagnoses |
| No specific entity naming | Only source TYPES — prevents defamation risk |
