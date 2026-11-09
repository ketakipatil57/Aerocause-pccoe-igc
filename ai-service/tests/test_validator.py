from __future__ import annotations

from pathlib import Path

import pytest
from ai_service.models import BriefContent, HypothesisBrief, RecommendedAction
from ai_service.rag.corpus import Passage
from ai_service.validator import BriefValidator
from pydantic import ValidationError

PACKAGE = {
    "incident": {
        "stationName": "Vayu Nagar Central",
        "pollutant": "PM25",
        "peakValue": 80.0,
    },
    "hypotheses": [
        {"type": "DUST", "rank": 1, "evidenceFor": ["E1: low ratio"], "missing": []},
        {"type": "INCONCLUSIVE", "rank": 2, "evidenceFor": [], "missing": ["wind data"]},
    ],
    "evidenceItems": [{"id": "E1", "summary": "Low PM ratio"}],
}
FIXTURE_TEXT = (
    Path(__file__).parent / "fixtures" / "FAKE_FOR_TESTS_retrieval.md"
).read_text(encoding="utf-8")
PASSAGE = Passage(
    passage_id="fake-doc:passage-1",
    doc_key="FAKE_FOR_TESTS",
    title="FAKE_FOR_TESTS test-only passage",
    publisher="Test fixture",
    version=None,
    url=None,
    reference="FAKE_FOR_TESTS only",
    text=FIXTURE_TEXT,
)


def valid_brief() -> BriefContent:
    return BriefContent(
        headline="Evidence summary",
        summary="The supplied evidence has uncertainty and does not establish a cause.",
        hypotheses=[
            HypothesisBrief(
                type="DUST",
                explanation="The deterministic score supports a possible dust pattern.",
                evidence_refs=["E1"],
                uncertainty="This heuristic is uncertain and needs independent verification.",
            ),
            HypothesisBrief(
                type="INCONCLUSIVE",
                explanation="The package includes an inconclusive result.",
                evidence_refs=[],
                uncertainty="Available evidence is insufficient to determine a source type.",
            ),
        ],
        verification_steps=["Verify readings against official monitoring records."],
        recommended_actions=[],
        unknowns=[],
        disclaimer="Decision support only; this is not an enforcement decision.",
    )


@pytest.mark.parametrize(
    ("mutate", "violation"),
    [
        (
            lambda brief: brief.model_copy(update={
                "hypotheses": [
                    brief.hypotheses[0].model_copy(update={"evidence_refs": ["E404"]}),
                    brief.hypotheses[1],
                ]
            }),
            "unknown_evidence_ref",
        ),
        (
            lambda brief: brief.model_copy(update={
                "recommended_actions": [
                    RecommendedAction(text="Review monitoring procedure.", citation_ids=["missing"])
                ]
            }),
            "unknown_citation",
        ),
        (
            lambda brief: brief.model_copy(update={"summary": "There were 999 readings."}),
            "unsupported_number",
        ),
        (
            lambda brief: brief.model_copy(update={
                "summary": "Acme Factory may be related to this pattern."
            }),
            "unverified_proper_noun",
        ),
        (
            lambda brief: brief.model_copy(update={
                "hypotheses": list(reversed(brief.hypotheses))
            }),
            "hypothesis_mismatch",
        ),
        (
            lambda brief: brief.model_copy(update={
                "recommended_actions": [
                    RecommendedAction(text="Order enforcement action.", citation_ids=[])
                ]
            }),
            "uncited_action",
        ),
        (
            lambda brief: brief.model_copy(update={
                "recommended_actions": [
                    RecommendedAction(
                        text="Create a new monitoring office.",
                        citation_ids=[PASSAGE.passage_id],
                    )
                ]
            }),
            "action_not_grounded_in_citation",
        ),
    ],
)
def test_validator_catches_each_required_violation(mutate, violation):
    invalid = mutate(valid_brief())

    violations = BriefValidator().validate(invalid, PACKAGE, [PASSAGE])

    assert any(violation in item for item in violations), violations


def test_valid_brief_passes_and_cited_action_is_grounded():
    brief = valid_brief().model_copy(update={
        "recommended_actions": [
            RecommendedAction(
                text="Review monitoring records.",
                citation_ids=[PASSAGE.passage_id],
            )
        ]
    })

    assert BriefValidator().validate(brief, PACKAGE, [PASSAGE]) == []


def test_explicit_generic_verification_action_is_allowed_without_citation():
    brief = valid_brief().model_copy(update={
        "recommended_actions": [
            RecommendedAction(
                text="Verify the readings independently.",
                generic_verification=True,
            )
        ]
    })

    assert BriefValidator().validate(brief, PACKAGE, []) == []


def test_named_factory_injected_by_model_is_rejected():
    injected = valid_brief().model_copy(update={
        "summary": "Acme Factory may be linked to the observed pattern."
    })

    violations = BriefValidator().validate(injected, PACKAGE, [])

    assert any("unverified_proper_noun" in item and "Acme Factory" in item for item in violations)


def test_single_word_named_company_injected_by_model_is_rejected():
    injected = valid_brief().model_copy(update={
        "summary": "Acme may be linked to the observed pattern."
    })

    violations = BriefValidator().validate(injected, PACKAGE, [])

    assert any("unverified_proper_noun" in item and "'Acme'" in item for item in violations)


def test_brief_schema_rejects_missing_fields_and_extra_fields():
    with pytest.raises(ValidationError):
        BriefContent.model_validate({"headline": "Incomplete"})

    payload = valid_brief().model_dump()
    payload["untrusted_extra"] = "not in the schema"
    with pytest.raises(ValidationError):
        BriefContent.model_validate(payload)
