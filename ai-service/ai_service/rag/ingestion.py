from __future__ import annotations

from ai_service.config import Settings
from ai_service.rag.corpus import CorpusLoader
from ai_service.rag.embeddings import create_embedding_provider
from ai_service.rag.index import ChromaPassageIndex


def ingest_corpus(settings: Settings) -> int:
    loader = CorpusLoader(settings.corpus_dir, settings.chunk_size, settings.chunk_overlap)
    passages = loader.load_passages()
    embeddings = create_embedding_provider(settings)
    vectors: list[list[float]] = []
    for start in range(0, len(passages), 64):
        vectors.extend(embeddings.embed([passage.text for passage in passages[start:start + 64]]))
    ChromaPassageIndex(settings.chroma_persist_dir).replace_all(passages, vectors)
    return len(passages)
