from __future__ import annotations

from pathlib import Path
from typing import Any

from ai_service.rag.corpus import Passage

COLLECTION_NAME = "aerocause_official_passages"


class ChromaPassageIndex:
    def __init__(self, persist_dir: Path):
        self.persist_dir = persist_dir
        self._client = None
        self._collection = None

    @property
    def collection(self) -> Any:
        if self._collection is None:
            try:
                import chromadb
            except ImportError as exc:
                raise RuntimeError(
                    "ChromaDB is required for corpus indexing and retrieval; install requirements.txt"
                ) from exc
            self.persist_dir.mkdir(parents=True, exist_ok=True)
            self._client = chromadb.PersistentClient(path=str(self.persist_dir))
            self._collection = self._client.get_or_create_collection(
                COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"},
            )
        return self._collection

    def count(self) -> int:
        return self.collection.count()

    def replace_all(self, passages: list[Passage], vectors: list[list[float]]) -> None:
        if len(passages) != len(vectors):
            raise ValueError("Passage and embedding counts must match")
        collection = self.collection
        self._client.delete_collection(COLLECTION_NAME)
        self._collection = self._client.create_collection(
            COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
        collection = self._collection
        batch_size = 100
        for start in range(0, len(passages), batch_size):
            batch = passages[start:start + batch_size]
            batch_vectors = vectors[start:start + batch_size]
            collection.add(
                ids=[item.passage_id for item in batch],
                embeddings=batch_vectors,
                documents=[item.text for item in batch],
                metadatas=[{
                    "doc_key": item.doc_key,
                    "title": item.title,
                    "publisher": item.publisher,
                    "version": item.version or "",
                    "url": item.url or "",
                    "reference": item.reference,
                } for item in batch],
            )

    def query(self, vector: list[float], top_k: int) -> dict[str, Any]:
        return self.collection.query(
            query_embeddings=[vector],
            n_results=min(top_k, self.count()),
            include=["documents", "metadatas", "distances"],
        )
