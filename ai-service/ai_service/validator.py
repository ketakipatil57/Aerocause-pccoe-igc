from __future__ import annotations

import math
import re
from collections.abc import Iterable

from ai_service.models import BriefContent
from ai_service.rag.corpus import Passage

NUMBER = re.compile(r"(?<![\w.])[-+]?\d+(?:\.\d+)?%?(?![\w.])")
TITLE_CASE_WORD = re.compile(r"\b[A-Z][a-z]{2,}\b")
TITLE_CASE_PHRASE = re.compile(r"\b(?:[A-Z][a-z]+)(?:\s+(?:[A-Z][a-z]+|of|the|and)){1,3}\b")
FACILITY_SUFFIX = re.compile(
    r"\b((?:[A-Z][A-Za-z0-9&'.-]*\s+){0,3}"
    r"(?:Factory|Industries|Ltd|Limited|Company|Corporation|Corp|Plant|Road|Street|Highway))\b"
)
GENERIC_ENTITY_PHRASES = {
    "no official source",
    "air quality index",
    "official source was",
    "decision support only",
    "sample data synthetic",
}
GENERIC_SENTENCE_STARTS = {
    "a", "an", "the", "this", "these", "those", "current", "available",
    "overall", "however", "there", "it", "evidence", "based", "code",
    "the supplied", "the hypotheses", "the current", "no official",
}
GENERIC_SENTENCE_START_WORDS = {
    "a", "an", "the", "this", "these", "those", "current", "available",
    "overall", "however", "there", "it", "evidence", "based", "code",
    "no", "review", "verify", "check", "compare", "confirm", "collect",
    "measure", "validate", "consider", "please",
    "avoid", "also", "if", "when", "source", "air", "quality", "data",
    "hypothesis", "decision", "uncertainty", "important", "note", "for",
    "further", "inconclusive",
}
GENERIC_VERIFICATION_WORDS = {
    "check", "compare", "confirm", "collect", "review", "verify", "recheck",
    "re-measure", "measure", "validate",
}
STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in",
    "is", "it", "of", "on", "or", "that", "the", "this", "to", "with",
    "should", "must", "may", "will", "can", "could", "please",
}


class BriefValidator:
    """Checks traceability and safety constraints; entity detection is heuristic."""

    def validate(
        self,
        brief: BriefContent,
        evidence_package: dict,
        passages: list[Passage],
    ) -> list[str]:
        violations: list[str] = []
        evidence_ids = {
            str(item.get("id"))
            for item in evidence_package.get("evidenceItems", [])
            if isinstance(item, dict) and item.get("id") is not None
        }
        retrieved = {passage.passage_id: passage for passage in passages}

        package_types = [
            str(item.get("type"))
            for item in evidence_package.get("hypotheses", [])
            if isinstance(item, dict)
        ]
        brief_types = [hypothesis.type for hypothesis in brief.hypotheses]
        if brief_types != package_types:
            violations.append("hypothesis_mismatch: types and order must exactly match the backend package")

        for hypothesis in brief.hypotheses:
            for reference in hypothesis.evidence_refs:
                if reference not in evidence_ids:
                    violations.append(
                        f"unknown_evidence_ref: {reference!r} is not an evidenceItems[].id"
                    )

        for action in brief.recommended_actions:
            for citation_id in action.citation_ids:
                if citation_id not in retrieved:
                    violations.append(
                        f"unknown_citation: {citation_id!r} is not a retrieved passage_id"
                    )
            if not action.citation_ids and not (
                action.generic_verification and _is_generic_verification(action.text)
            ):
                violations.append(
                    "uncited_action: recommended actions require a retrieved citation "
                    "or an explicitly flagged generic verification step"
                )
            elif action.citation_ids and all(
                citation_id in retrieved for citation_id in action.citation_ids
            ) and not _action_grounded(action.text, [retrieved[cid] for cid in action.citation_ids]):
                violations.append(
                    "action_not_grounded_in_citation: action wording is not supported by its cited passage"
                )

        text = "\n".join(_brief_text(brief))
        allowed_numbers = _package_numbers(evidence_package)
        for token in NUMBER.findall(text):
            number = _parse_number(token)
            if number is not None and not any(
                math.isclose(number, allowed, rel_tol=0.01, abs_tol=0.05)
                for allowed in allowed_numbers
            ):
                violations.append(f"unsupported_number: {token!r} does not occur in the evidence package")

        package_text = " ".join(_all_strings(evidence_package)).casefold()
        for entity in _proper_noun_candidates(text):
            if entity.casefold() not in package_text and entity.casefold() not in GENERIC_ENTITY_PHRASES:
                violations.append(
                    f"unverified_proper_noun: {entity!r} is not present in the evidence package"
                )
        return list(dict.fromkeys(violations))


def _brief_text(brief: BriefContent) -> Iterable[str]:
    yield brief.headline
    yield brief.summary
    for hypothesis in brief.hypotheses:
        yield hypothesis.explanation
        yield hypothesis.uncertainty
    yield from brief.verification_steps
    for action in brief.recommended_actions:
        yield action.text
    yield from brief.unknowns
    yield brief.disclaimer


def _all_strings(value) -> Iterable[str]:
    if isinstance(value, dict):
        for nested in value.values():
            yield from _all_strings(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from _all_strings(nested)
    elif isinstance(value, str):
        yield value


def _package_numbers(evidence_package: dict) -> set[float]:
    numbers: set[float] = set()

    def visit(value) -> None:
        if isinstance(value, bool) or value is None:
            return
        if isinstance(value, (int, float)):
            if math.isfinite(float(value)):
                numbers.add(float(value))
            return
        if isinstance(value, dict):
            for nested in value.values():
                visit(nested)
        elif isinstance(value, list):
            for nested in value:
                visit(nested)
        elif isinstance(value, str):
            for token in NUMBER.findall(value):
                number = _parse_number(token)
                if number is not None:
                    numbers.add(number)

    visit(evidence_package)
    return numbers


def _parse_number(value: str) -> float | None:
    try:
        return float(value.rstrip("%"))
    except ValueError:
        return None


def _proper_noun_candidates(text: str) -> set[str]:
    candidates = {
        match.group(0).strip()
        for match in TITLE_CASE_PHRASE.finditer(text)
    }
    candidates.update(match.group(0) for match in TITLE_CASE_WORD.finditer(text))
    facilities = {
        match.group(1).strip()
        for match in FACILITY_SUFFIX.finditer(text)
    }
    candidates.update(facilities)
    return {
        candidate
        for candidate in candidates
        if (
            candidate in facilities
            or any(
                word.casefold() not in GENERIC_SENTENCE_START_WORDS
                for word in candidate.split()
            )
        )
        if candidate.casefold() not in GENERIC_ENTITY_PHRASES
        and candidate.casefold() not in GENERIC_SENTENCE_STARTS
    }


def _is_generic_verification(text: str) -> bool:
    words = set(re.findall(r"[a-z]+(?:-[a-z]+)?", text.casefold()))
    return bool(words & GENERIC_VERIFICATION_WORDS) and not bool(
        re.search(r"\b(?:enforce|prosecute|penalty|fine|violate|culprit|cause is)\b", text, re.IGNORECASE)
    )


def _action_grounded(text: str, passages: list[Passage]) -> bool:
    action_terms = _content_words(text)
    passage_terms = set().union(*(_content_words(passage.text) for passage in passages))
    shared_terms = action_terms & passage_terms
    return len(shared_terms) >= 2 and len(shared_terms) / max(len(action_terms), 1) >= 0.5


def _content_words(text: str) -> set[str]:
    return {
        word for word in re.findall(r"[a-z]{3,}", text.casefold())
        if word not in STOP_WORDS
    }
