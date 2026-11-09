from __future__ import annotations

from ai_service.config import Settings
from ai_service.llm.mock import MockBriefProvider
from ai_service.main import create_app
from ai_service.rag.corpus import Passage
from ai_service.rag.retrieval import create_retrieval_service
from ai_service.service import InvestigationService
from fastapi.testclient import TestClient

EVIDENCE_PACKAGE = {
    "packageVersion": "1.0",
    "dataNotice": "SAMPLE DATA (synthetic)",
    "incident": {
        "id": 1,
        "stationExternalId": "STN-001",
        "stationName": "Vayu Nagar Central",
        "pollutant": "PM25",
        "peakValue": 80.0,
        "status": "NEW",
    },
    "hypotheses": [
        {
            "rank": 1,
            "type": "INCONCLUSIVE",
            "score": 0.0,
            "confidenceLabel": "LOW",
            "evidenceFor": [],
            "evidenceAgainst": [],
            "missing": ["PM10 data is unavailable"],
        }
    ],
    "evidenceItems": [{"id": "E1", "type": "RULE", "summary": "Test evidence"}],
}


class EmptyRetrieval:
    def retrieve(self, query: str, top_k: int | None = None) -> list[Passage]:
        _ = query, top_k
        return []


class RetryProvider:
    mode = "LIVE"

    def __init__(self):
        self.calls = []
        self.delegate = MockBriefProvider()

    def generate(self, evidence_package, passages, violations=None):
        self.calls.append(violations)
        brief = self.delegate.generate(evidence_package, passages)
        if len(self.calls) == 1:
            return brief.model_copy(update={"hypotheses": []})
        return brief


class PersistentlyInvalidProvider:
    mode = "LIVE"

    def __init__(self):
        self.delegate = MockBriefProvider()
        self.calls = 0

    def generate(self, evidence_package, passages, violations=None):
        _ = violations
        self.calls += 1
        brief = self.delegate.generate(evidence_package, passages)
        return brief.model_copy(update={"hypotheses": []})


class NamedFactoryProvider:
    mode = "LIVE"

    def __init__(self):
        self.delegate = MockBriefProvider()
        self.retry_violations = []

    def generate(self, evidence_package, passages, violations=None):
        brief = self.delegate.generate(evidence_package, passages)
        if violations is None:
            return brief.model_copy(update={
                "summary": "Acme Factory may be linked to this pattern."
            })
        self.retry_violations = violations
        return brief


def settings(tmp_path) -> Settings:
    return Settings(
        llm_provider="mock",
        llm_api_key="",
        llm_model="test",
        llm_base_url="https://example.invalid/v1/chat/completions",
        embedding_provider="local",
        embedding_model="fake-local-model",
        embedding_api_key="",
        embedding_api_url="https://example.invalid/v1/embeddings",
        corpus_dir=tmp_path / "corpus",
        chroma_persist_dir=tmp_path / "chroma",
        chunk_size=1200,
        chunk_overlap=160,
        retrieval_top_k=5,
        retrieval_min_similarity=0.25,
    )


def test_mock_brief_is_deterministic_and_inconclusive():
    provider = MockBriefProvider()

    first = provider.generate(EVIDENCE_PACKAGE, [])
    second = provider.generate(EVIDENCE_PACKAGE, [])

    assert first.model_dump() == second.model_dump()
    assert first.hypotheses[0].type == "INCONCLUSIVE"
    assert "inconclusive" in first.summary.lower()
    assert "no official source was found" in first.summary.lower()
    assert first.recommended_actions == []


def test_investigation_retries_with_violations_then_passes():
    provider = RetryProvider()
    service = InvestigationService(provider, EmptyRetrieval())

    response = service.investigate(EVIDENCE_PACKAGE)

    assert provider.calls[0] is None
    assert any("hypothesis_mismatch" in item for item in provider.calls[1])
    assert response.validator_status == "RETRIED"
    assert response.violations == []
    assert response.llm_mode == "LIVE"


def test_second_invalid_attempt_returns_template_fallback_with_violations():
    provider = PersistentlyInvalidProvider()
    response = InvestigationService(provider, EmptyRetrieval()).investigate(EVIDENCE_PACKAGE)

    assert provider.calls == 2
    assert response.validator_status == "TEMPLATE_FALLBACK"
    assert response.brief.hypotheses[0].type == "INCONCLUSIVE"
    assert any("hypothesis_mismatch" in item for item in response.violations)
    assert "no official source was found" in response.brief.summary.lower()


def test_model_named_factory_is_rejected_and_retry_corrects_it():
    provider = NamedFactoryProvider()
    response = InvestigationService(provider, EmptyRetrieval()).investigate(EVIDENCE_PACKAGE)

    assert response.validator_status == "RETRIED"
    assert any("unverified_proper_noun" in item for item in provider.retry_violations)
    assert "Acme Factory" not in response.brief.summary


def test_endpoints_accept_backend_package_and_return_empty_corpus_details(tmp_path):
    config = settings(tmp_path)
    corpus = config.corpus_dir
    corpus.mkdir()
    (corpus / "manifest.json").write_text("[]", encoding="utf-8")
    client = TestClient(create_app(
        settings=config,
        retrieval=create_retrieval_service(config),
        provider=MockBriefProvider(),
    ))

    health = client.get("/health")
    investigation = client.post("/investigate", json=EVIDENCE_PACKAGE)
    retrieval = client.post("/retrieve", json={"query": "dust guidance"})

    assert health.status_code == 200
    assert health.json()["llm_provider"] == "MOCK"
    assert investigation.status_code == 200
    assert investigation.json()["llm_mode"] == "MOCK"
    assert investigation.json()["validator_status"] == "PASSED"
    assert investigation.json()["retrieved_passages"] == []
    assert "no official source was found" in investigation.json()["brief"]["summary"].lower()
    assert investigation.json()["brief"]["recommended_actions"] == []
    assert retrieval.json() == {"passages": []}


def test_citizen_summary_uses_language_template_and_only_supplied_precautions(tmp_path):
    client = TestClient(create_app(
        settings=settings(tmp_path),
        retrieval=EmptyRetrieval(),
        provider=MockBriefProvider(),
    ))

    response = client.post("/citizen-summary", json={
        "aqi": 156,
        "category": "Poor",
        "evidence_summary": "an uncertain particulate pattern",
        "language": "mr",
        "precaution_templates": ["Use the precaution supplied by the UI."],
    })

    assert response.status_code == 200
    payload = response.json()
    assert payload["needs_native_speaker_review"] is True
    assert payload["precautions"] == ["Use the precaution supplied by the UI."]
    assert "असू शकतो" in payload["summary"]
