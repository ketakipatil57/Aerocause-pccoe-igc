from __future__ import annotations

import httpx
from ai_service.llm.mock import MockBriefProvider
from ai_service.llm.openai_compatible import OpenAICompatibleBriefProvider
from ai_service.prompts import SYSTEM_PROMPT
from ai_service.rag.embeddings import ApiEmbeddings

PACKAGE = {
    "incident": {"pollutant": "PM25"},
    "hypotheses": [{"type": "INCONCLUSIVE", "evidenceFor": [], "missing": []}],
    "evidenceItems": [],
}


def test_openai_compatible_provider_requests_json_and_validates_response(monkeypatch):
    expected = MockBriefProvider().generate(PACKAGE, [])
    requests = []

    def fake_post(url, **kwargs):
        requests.append((url, kwargs))
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": expected.model_dump_json()}}]},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("ai_service.llm.openai_compatible.httpx.post", fake_post)
    provider = OpenAICompatibleBriefProvider(
        "test-key", "test-model", "https://example.test/v1"
    )

    actual = provider.generate(PACKAGE, [])

    assert actual == expected
    assert provider.mode == "LIVE"
    assert requests[0][0] == "https://example.test/v1/chat/completions"
    assert requests[0][1]["json"]["model"] == "test-model"
    assert requests[0][1]["json"]["response_format"] == {"type": "json_object"}
    assert SYSTEM_PROMPT in requests[0][1]["json"]["messages"][0]["content"]


def test_api_embedding_provider_parses_embedding_response(monkeypatch):
    requests = []

    def fake_post(url, **kwargs):
        requests.append((url, kwargs))
        return httpx.Response(
            200,
            json={
                "data": [
                    {"index": 1, "embedding": [0.2, 0.8]},
                    {"index": 0, "embedding": [0.9, 0.1]},
                ]
            },
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr("ai_service.rag.embeddings.httpx.post", fake_post)
    provider = ApiEmbeddings(
        "https://example.test/v1/embeddings",
        "test-key",
        "test-embedding-model",
    )

    vectors = provider.embed(["first", "second"])

    assert vectors == [[0.9, 0.1], [0.2, 0.8]]
    assert requests[0][1]["json"]["model"] == "test-embedding-model"
    assert requests[0][1]["headers"]["Authorization"] == "Bearer test-key"
