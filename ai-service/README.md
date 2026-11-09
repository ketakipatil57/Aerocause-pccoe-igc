# AeroCause AI Service

FastAPI service for evidence-grounded incident briefs, retrieval from user-supplied official documents, and templated citizen summaries. The service does not decide causes or enforcement outcomes.

## Requirements and local start

Python 3.11 is the supported runtime.

```powershell
cd ai-service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
uvicorn ai_service.main:app --reload --port 8000
```

The default `LLM_PROVIDER=mock` runs without an API key. The default local embedding provider is loaded lazily and is only needed after documents are indexed. Add official files and their metadata to [corpus/README.md](./corpus/README.md), then run:

```powershell
python -m scripts.ingest_corpus
```

The ChromaDB collection persists under `ai-service/chromadb_data` locally. Its data is not committed.

## API

- `GET /health` reports service and provider readiness.
- `POST /investigate` accepts the backend evidence-package JSON directly as the request body and returns `{brief, validator_status, llm_mode, violations, retrieved_passages}`.
- `POST /citizen-summary` accepts `aqi`, `category`, `evidence_summary`, `language` (`en`, `hi`, `mr`), and optional `precaution_templates`. The service never invents precautions. Hindi and Marathi templates are marked for native-speaker review.
- `POST /retrieve` accepts `{ "query": "...", "top_k": 5 }` and returns an empty `passages` list when no corpus is indexed.

The brief schema is enforced using Pydantic. The validator checks evidence and passage references, package hypothesis order, unsupported numbers, unverified proper nouns, uncited recommendations, and whether cited recommendations share meaningful terms with the cited passage. Entity detection and action-to-passage grounding are heuristic and can have false positives or false negatives; humans must review generated text.

Invalid model output is retried once with the validator violations. If the second attempt still fails, a deterministic template brief is returned with `validator_status=TEMPLATE_FALLBACK` and the violations that triggered the fallback. Every response reports `llm_mode=MOCK` or `LIVE`.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `mock` | `mock` or `openai` (OpenAI-compatible chat-completions API) |
| `LLM_API_KEY` | empty | Required for `openai` |
| `LLM_MODEL` | `gpt-4o-mini` | Chat model name |
| `LLM_BASE_URL` | OpenAI chat-completions URL | Optional compatible endpoint |
| `EMBEDDING_PROVIDER` | `local` | `local` sentence-transformers model or `api` |
| `EMBEDDING_MODEL` | `sentence-transformers/all-MiniLM-L6-v2` | Local or API embedding model |
| `EMBEDDING_API_KEY` | empty | Required when embedding provider is `api` |
| `EMBEDDING_API_URL` | OpenAI embeddings URL | Compatible API endpoint |
| `CORPUS_DIR` | `ai-service/corpus` | User-managed manifest and documents |
| `CHROMA_PERSIST_DIR` | `ai-service/chromadb_data` | Persistent vector-store directory |
| `CHUNK_SIZE` / `CHUNK_OVERLAP` | `1200` / `160` | Chunking parameters in characters |
| `RETRIEVAL_TOP_K` | `5` | Maximum retrieved passages |
| `RETRIEVAL_MIN_SIMILARITY` | `0.25` | Cosine-similarity floor; lower-scoring passages are omitted |

The retrieval similarity floor is a prototype heuristic, not a validated relevance guarantee. Tune it with reviewed corpus/query examples.

Never put credentials in the corpus manifest or commit secrets.

## Tests

```powershell
python -m pytest tests -v
```

Tests use only synthetic brief data and `FAKE_FOR_TESTS` fixtures. They do not read test fixtures into the official-document corpus.

## Container

Build from the repository root with `docker compose build ai-service`. Mount the corpus folder and persistent Chroma volume as configured in the root compose file.
