from __future__ import annotations

import json
from pathlib import Path

from ai_service.models import PassageResponse
from ai_service.rag.corpus import CorpusLoader, Passage
from ai_service.rag.index import ChromaPassageIndex
from ai_service.rag.retrieval import RetrievalService


class EmptyIndex:
    def count(self) -> int:
        raise AssertionError("an empty manifest must return before accessing the index")


class EmptyEmbeddings:
    def embed(self, texts: list[str]) -> list[list[float]]:
        _ = texts
        raise AssertionError("an empty manifest must not load or call embeddings")


def test_retrieval_returns_empty_list_for_empty_corpus(tmp_path):
    (tmp_path / "manifest.json").write_text("[]", encoding="utf-8")
    retrieval = RetrievalService(
        CorpusLoader(tmp_path),
        EmptyIndex(),
        EmptyEmbeddings(),
    )

    assert retrieval.retrieve("air quality rules") == []


def test_loader_preserves_markdown_sections_and_passage_metadata(tmp_path):
    fixture_path = (
        Path(__file__).parent / "fixtures" / "FAKE_FOR_TESTS_retrieval.md"
    )
    (tmp_path / "manifest.json").write_text(
        json.dumps([{
            "doc_key": "FAKE_FOR_TESTS",
            "title": "Synthetic test-only content",
            "publisher": "Fixture",
            "version": None,
            "url": None,
            "file": "sample.md",
        }]),
        encoding="utf-8",
    )
    (tmp_path / "sample.md").write_text(fixture_path.read_text(encoding="utf-8"), encoding="utf-8")

    passages = CorpusLoader(tmp_path, chunk_size=80, overlap=10).load_passages()

    assert passages
    assert all(item.doc_key == "FAKE_FOR_TESTS" for item in passages)
    assert all(item.reference == "FAKE_FOR_TESTS only" for item in passages)
    assert all(item.passage_id.startswith("FAKE_FOR_TESTS:") for item in passages)
    responses = [PassageResponse(**item.as_response()) for item in passages]
    assert all(item.publisher == "Fixture" for item in responses)


def test_chroma_index_persists_test_passage_and_returns_references(tmp_path):
    passage = Passage(
        passage_id="FAKE_FOR_TESTS:one",
        doc_key="FAKE_FOR_TESTS",
        title="FAKE_FOR_TESTS only",
        publisher="Test fixture",
        version=None,
        url=None,
        reference="FAKE_FOR_TESTS only",
        text="synthetic test passage",
    )
    index = ChromaPassageIndex(tmp_path / "chroma")
    index.replace_all([passage], [[0.25, 0.75]])

    restored = ChromaPassageIndex(tmp_path / "chroma")
    result = restored.query([0.25, 0.75], 1)

    assert restored.count() == 1
    assert result["ids"] == [["FAKE_FOR_TESTS:one"]]
    assert result["documents"] == [["synthetic test passage"]]
    assert result["metadatas"][0][0]["reference"] == "FAKE_FOR_TESTS only"


def test_retrieval_omits_passages_below_similarity_floor(tmp_path):
    (tmp_path / "manifest.json").write_text(json.dumps([{
        "doc_key": "FAKE_FOR_TESTS",
        "title": "Synthetic test-only content",
        "publisher": "Fixture",
        "version": None,
        "url": None,
        "file": "sample.txt",
    }]), encoding="utf-8")
    (tmp_path / "sample.txt").write_text("synthetic text only", encoding="utf-8")

    class LowSimilarityIndex:
        def count(self) -> int:
            return 1

        def query(self, vector, top_k):
            _ = vector, top_k
            return {
                "ids": [["FAKE_FOR_TESTS:unrelated"]],
                "documents": [["unrelated test content"]],
                "metadatas": [[{
                    "doc_key": "FAKE_FOR_TESTS",
                    "title": "Synthetic test-only content",
                    "reference": "text",
                }]],
                "distances": [[0.95]],
            }

    class FixedEmbeddings:
        def embed(self, texts: list[str]) -> list[list[float]]:
            _ = texts
            return [[0.1, 0.9]]

    retrieval = RetrievalService(
        CorpusLoader(tmp_path),
        LowSimilarityIndex(),
        FixedEmbeddings(),
        min_similarity=0.25,
    )

    assert retrieval.retrieve("unrelated") == []
