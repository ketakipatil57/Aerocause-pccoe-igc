from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

PACKAGE_ROOT = Path(__file__).resolve().parent
SERVICE_ROOT = PACKAGE_ROOT.parent


@dataclass(frozen=True)
class Settings:
    llm_provider: str
    llm_api_key: str
    llm_model: str
    llm_base_url: str
    embedding_provider: str
    embedding_model: str
    embedding_api_key: str
    embedding_api_url: str
    corpus_dir: Path
    chroma_persist_dir: Path
    chunk_size: int
    chunk_overlap: int
    retrieval_top_k: int
    retrieval_min_similarity: float

    @classmethod
    def from_env(cls) -> Settings:
        llm_provider = os.getenv("LLM_PROVIDER", "mock").strip().lower()
        embedding_provider = os.getenv("EMBEDDING_PROVIDER", "local").strip().lower()
        if llm_provider not in {"mock", "openai"}:
            raise ValueError("LLM_PROVIDER must be 'mock' or 'openai'")
        if embedding_provider not in {"local", "api"}:
            raise ValueError("EMBEDDING_PROVIDER must be 'local' or 'api'")
        return cls(
            llm_provider=llm_provider,
            llm_api_key=os.getenv("LLM_API_KEY", ""),
            llm_model=os.getenv("LLM_MODEL", "gpt-4o-mini"),
            llm_base_url=os.getenv(
                "LLM_BASE_URL", "https://api.openai.com/v1/chat/completions"
            ),
            embedding_provider=embedding_provider,
            embedding_model=os.getenv(
                "EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2"
            ),
            embedding_api_key=os.getenv("EMBEDDING_API_KEY", ""),
            embedding_api_url=os.getenv(
                "EMBEDDING_API_URL", "https://api.openai.com/v1/embeddings"
            ),
            corpus_dir=Path(
                os.getenv("CORPUS_DIR", str(SERVICE_ROOT / "corpus"))
            ).resolve(),
            chroma_persist_dir=Path(
                os.getenv("CHROMA_PERSIST_DIR", str(SERVICE_ROOT / "chromadb_data"))
            ).resolve(),
            chunk_size=_bounded_int("CHUNK_SIZE", 1200, 200, 8000),
            chunk_overlap=_bounded_int("CHUNK_OVERLAP", 160, 0, 1000),
            retrieval_top_k=_bounded_int("RETRIEVAL_TOP_K", 5, 1, 20),
            retrieval_min_similarity=_bounded_float("RETRIEVAL_MIN_SIMILARITY", 0.25, 0.0, 1.0),
        )


def _bounded_int(name: str, default: int, minimum: int, maximum: int) -> int:
    raw = os.getenv(name)
    if raw is None:
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer") from exc
    if not minimum <= value <= maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")
    return value


def _bounded_float(name: str, default: float, minimum: float, maximum: float) -> float:
    raw = os.getenv(name)
    if raw is None:
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be a number") from exc
    if not minimum <= value <= maximum:
        raise ValueError(f"{name} must be between {minimum} and {maximum}")
    return value
