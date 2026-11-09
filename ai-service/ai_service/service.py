from __future__ import annotations

import httpx
from pydantic import ValidationError

from ai_service.llm.base import BriefProvider
from ai_service.models import BriefContent, InvestigationResponse, PassageResponse
from ai_service.rag.corpus import Passage
from ai_service.validator import BriefValidator


class InvestigationService:
    def __init__(
        self,
        provider: BriefProvider,
        retrieval,
        validator: BriefValidator | None = None,
    ):
        self.provider = provider
        self.retrieval = retrieval
        self.validator = validator or BriefValidator()

    def investigate(self, evidence_package: dict) -> InvestigationResponse:
        passages = self.retrieval.retrieve(_build_query(evidence_package))
        violations: list[str] = []
        for attempt in range(2):
            try:
                brief = self.provider.generate(
                    evidence_package,
                    passages,
                    violations if attempt else None,
                )
                if not isinstance(brief, BriefContent):
                    brief = BriefContent.model_validate(brief)
                violations = self.validator.validate(brief, evidence_package, passages)
            except (httpx.HTTPError, ValueError, ValidationError, KeyError) as exc:
                violations = [f"brief_generation_or_schema_error: {type(exc).__name__}: {exc}"]
            if not violations:
                return InvestigationResponse(
                    brief=brief,
                    validator_status="PASSED" if attempt == 0 else "RETRIED",
                    llm_mode=self.provider.mode,
                    violations=[],
                    retrieved_passages=[PassageResponse(**item.as_response()) for item in passages],
                )
        fallback = self._template_fallback(evidence_package, passages)
        fallback_violations = self.validator.validate(fallback, evidence_package, passages)
        if fallback_violations:
            violations.extend(f"template_fallback:{item}" for item in fallback_violations)
        return InvestigationResponse(
            brief=fallback,
            validator_status="TEMPLATE_FALLBACK",
            llm_mode=self.provider.mode,
            violations=violations,
            retrieved_passages=[PassageResponse(**item.as_response()) for item in passages],
        )

    @staticmethod
    def _template_fallback(evidence_package: dict, passages: list[Passage]) -> BriefContent:
        from ai_service.llm.mock import MockBriefProvider

        return MockBriefProvider().generate(evidence_package, passages)


def _build_query(evidence_package: dict) -> str:
    parts = []
    incident = evidence_package.get("incident", {})
    if isinstance(incident, dict):
        parts.extend(str(incident[key]) for key in ("pollutant", "status") if incident.get(key))
    for hypothesis in evidence_package.get("hypotheses", []):
        if isinstance(hypothesis, dict) and hypothesis.get("type"):
            parts.append(str(hypothesis["type"]))
        if isinstance(hypothesis, dict):
            parts.extend(
                str(item)
                for item in hypothesis.get("evidenceFor", [])
                if isinstance(item, (str, int, float))
            )
    return " ".join(parts)[:2000] or "air quality incident evidence"
