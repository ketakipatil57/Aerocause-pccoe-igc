from __future__ import annotations

from typing import Protocol

from ai_service.rag.corpus import CorpusLoader, Passage
from ai_service.rag.embeddings import EmbeddingProvider
from ai_service.rag.index import ChromaPassageIndex


class PassageIndex(Protocol):
    def count(self) -> int: ...
    def query(self, vector: list[float], top_k: int) -> dict: ...


class RetrievalService:
    def __init__(
        self,
        corpus: CorpusLoader,
        index: PassageIndex,
        embeddings: EmbeddingProvider,
        top_k: int = 5,
        min_similarity: float = 0.25,
    ):
        if not 0.0 <= min_similarity <= 1.0:
            raise ValueError("min_similarity must be between 0 and 1")
        self.corpus = corpus
        self.index = index
        self.embeddings = embeddings
        self.top_k = top_k
        self.min_similarity = min_similarity

    def retrieve(self, query: str, top_k: int | None = None) -> list[Passage]:
        if not query.strip() or not self.corpus.manifest_documents():
            return []
        count = self.index.count()
        if count == 0:
            return []
        vector = self.embeddings.embed([query])[0]
        result = self.index.query(vector, top_k or self.top_k)
        ids = (result.get("ids") or [[]])[0]
        documents = (result.get("documents") or [[]])[0]
        metadatas = (result.get("metadatas") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]
        allowed_doc_keys = {
            document.doc_key for document in self.corpus.manifest_documents()
        }
        passages: list[Passage] = []
        for passage_id, text, metadata, distance in zip(
            ids, documents, metadatas, distances, strict=False
        ):
            if (
                distance is None
                or 1.0 - float(distance) < self.min_similarity
                or metadata.get("doc_key") not in allowed_doc_keys
            ):
                continue
            passages.append(Passage(
                passage_id=passage_id,
                doc_key=metadata["doc_key"],
                title=metadata["title"],
                publisher=metadata.get("publisher", ""),
                version=metadata.get("version") or None,
                url=metadata.get("url") or None,
                reference=metadata.get("reference", "section unavailable"),
                text=text,
            ))
        return passages


def create_retrieval_service(settings):
    from ai_service.rag.embeddings import create_embedding_provider

    return RetrievalService(
        CorpusLoader(settings.corpus_dir, settings.chunk_size, settings.chunk_overlap),
        ChromaPassageIndex(settings.chroma_persist_dir),
        create_embedding_provider(settings),
        settings.retrieval_top_k,
        settings.retrieval_min_similarity,
    )
