from __future__ import annotations

import json

import httpx

from ai_service.llm.base import BriefProvider
from ai_service.models import BriefContent
from ai_service.prompts import build_messages
from ai_service.rag.corpus import Passage


class OpenAICompatibleBriefProvider(BriefProvider):
    mode = "LIVE"

    def __init__(self, api_key: str, model: str, endpoint: str):
        if not api_key:
            raise ValueError("LLM_API_KEY is required when LLM_PROVIDER=openai")
        self.api_key = api_key
        self.model = model
        self.endpoint = endpoint.rstrip("/")
        if not self.endpoint.endswith("/chat/completions"):
            self.endpoint += "/chat/completions"

    def generate(
        self,
        evidence_package: dict,
        passages: list[Passage],
        violations: list[str] | None = None,
    ) -> BriefContent:
        response = httpx.post(
            self.endpoint,
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={
                "model": self.model,
                "temperature": 0,
                "response_format": {"type": "json_object"},
                "messages": build_messages(evidence_package, passages, violations),
            },
            timeout=60.0,
        )
        response.raise_for_status()
        payload = response.json()
        try:
            content = payload["choices"][0]["message"]["content"]
            return BriefContent.model_validate(json.loads(content))
        except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise ValueError("LLM response did not contain a JSON brief") from exc


def create_brief_provider(settings) -> BriefProvider:
    if settings.llm_provider == "mock":
        from ai_service.llm.mock import MockBriefProvider

        return MockBriefProvider()
    return OpenAICompatibleBriefProvider(
        settings.llm_api_key,
        settings.llm_model,
        settings.llm_base_url,
    )
