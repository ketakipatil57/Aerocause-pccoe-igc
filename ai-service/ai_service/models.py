from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, RootModel, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class HypothesisBrief(StrictModel):
    type: str = Field(min_length=1)
    explanation: str = Field(min_length=1)
    evidence_refs: list[str] = Field(default_factory=list)
    uncertainty: str = Field(min_length=1)


class RecommendedAction(StrictModel):
    text: str = Field(min_length=1)
    citation_ids: list[str] = Field(default_factory=list)
    generic_verification: bool = False


class BriefContent(StrictModel):
    headline: str = Field(min_length=1)
    summary: str = Field(min_length=1)
    hypotheses: list[HypothesisBrief]
    verification_steps: list[str]
    recommended_actions: list[RecommendedAction]
    unknowns: list[str]
    disclaimer: str = Field(min_length=1)


class InvestigationRequest(RootModel[dict[str, Any]]):
    pass


class PassageResponse(StrictModel):
    doc_key: str
    title: str
    version: str | None = None
    url: str | None = None
    passage_id: str
    reference: str
    excerpt: str


class InvestigationResponse(StrictModel):
    brief: BriefContent
    validator_status: Literal["PASSED", "RETRIED", "TEMPLATE_FALLBACK"]
    llm_mode: Literal["LIVE", "MOCK"]
    violations: list[str]
    retrieved_passages: list[PassageResponse]


class RetrieveRequest(StrictModel):
    query: str = Field(min_length=1, max_length=2000)
    top_k: int | None = Field(default=None, ge=1, le=20)

    @field_validator("query")
    @classmethod
    def query_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("query must not be blank")
        return value


class CitizenSummaryRequest(StrictModel):
    aqi: int = Field(ge=0)
    category: str = Field(min_length=1, max_length=40)
    evidence_summary: str = Field(default="", max_length=500)
    language: Literal["en", "hi", "mr"] = "en"
    precaution_templates: list[str] = Field(default_factory=list, max_length=10)


class CitizenSummaryResponse(StrictModel):
    summary: str
    precautions: list[str]
    language: Literal["en", "hi", "mr"]
    needs_native_speaker_review: bool
    llm_mode: Literal["MOCK"] = "MOCK"
