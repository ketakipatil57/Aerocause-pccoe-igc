from __future__ import annotations

from ai_service.llm.base import BriefProvider
from ai_service.models import BriefContent, HypothesisBrief
from ai_service.rag.corpus import Passage


class MockBriefProvider(BriefProvider):
    mode = "MOCK"

    def generate(
        self,
        evidence_package: dict,
        passages: list[Passage],
        violations: list[str] | None = None,
    ) -> BriefContent:
        _ = violations
        hypotheses = evidence_package.get("hypotheses", [])
        evidence_ids = [
            str(item["id"])
            for item in evidence_package.get("evidenceItems", [])
            if isinstance(item, dict) and item.get("id") is not None
        ]
        inconclusive = any(
            isinstance(item, dict) and item.get("type") == "INCONCLUSIVE"
            for item in hypotheses
        )
        if inconclusive:
            summary = (
                "The supplied evidence is inconclusive; a source type cannot be "
                "determined from this package."
            )
        else:
            summary = (
                "The hypotheses below are ranked by deterministic code. They are "
                "uncertain indicators, not verified causes."
            )
        if not passages:
            summary += " No official source was found in the supplied document corpus."

        brief_hypotheses = [
            HypothesisBrief(
                type=str(item.get("type", "INCONCLUSIVE")),
                explanation=(
                    "This is the source-type hypothesis computed by the deterministic "
                    "backend; this service does not establish a cause."
                ),
                evidence_refs=evidence_ids[:5],
                uncertainty="The hypothesis is heuristic and requires independent verification.",
            )
            for item in hypotheses
            if isinstance(item, dict)
        ]
        unknowns = [
            str(missing)
            for item in hypotheses
            if isinstance(item, dict)
            for missing in item.get("missing", [])
            if isinstance(missing, (str, int, float))
        ]
        return BriefContent(
            headline="Evidence summary",
            summary=summary,
            hypotheses=brief_hypotheses,
            verification_steps=[
                "Verify the readings against appropriate official monitoring records.",
                "Review the available evidence and its quality flags.",
            ],
            recommended_actions=[],
            unknowns=unknowns,
            disclaimer=(
                "Decision support only. This brief is not a finding of cause, "
                "legal conclusion, or enforcement decision."
            ),
        )
