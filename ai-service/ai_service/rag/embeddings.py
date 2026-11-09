from __future__ import annotations

from abc import ABC, abstractmethod

import httpx

from ai_service.config import Settings


class EmbeddingProvider(ABC):
    @abstractmethod
    def embed(self, texts: list[str]) -> list[list[float]]:
        raise NotImplementedError


class SentenceTransformerEmbeddings(EmbeddingProvider):
    def __init__(self, model_name: str):
        self.model_name = model_name
        self._model = None

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if self._model is None:
            from sentence_transformers import SentenceTransformer

            self._model = SentenceTransformer(self.model_name)
        vectors = self._model.encode(texts, normalize_embeddings=True)
        return vectors.tolist()


class ApiEmbeddings(EmbeddingProvider):
    def __init__(self, api_url: str, api_key: str, model: str):
        if not api_key:
            raise ValueError("EMBEDDING_API_KEY is required when EMBEDDING_PROVIDER=api")
        self.api_url = api_url
        self.api_key = api_key
        self.model = model

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        response = httpx.post(
            self.api_url,
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={"model": self.model, "input": texts},
            timeout=60.0,
        )
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data")
        if not isinstance(data, list) or len(data) != len(texts):
            raise ValueError("Embedding API returned an unexpected response")
        ordered = sorted(data, key=lambda row: row.get("index", 0))
        return [row["embedding"] for row in ordered]


def create_embedding_provider(settings: Settings) -> EmbeddingProvider:
    if settings.embedding_provider == "api":
        return ApiEmbeddings(
            settings.embedding_api_url,
            settings.embedding_api_key,
            settings.embedding_model,
        )
    return SentenceTransformerEmbeddings(settings.embedding_model)
