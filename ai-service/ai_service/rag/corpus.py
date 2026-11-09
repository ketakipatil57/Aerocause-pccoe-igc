from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pypdf import PdfReader

SUPPORTED_SUFFIXES = {".pdf", ".txt", ".md"}
HEADING = re.compile(r"^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$")


@dataclass(frozen=True)
class CorpusDocument:
    doc_key: str
    title: str
    publisher: str
    version: str | None
    url: str | None
    file: str


@dataclass(frozen=True)
class Passage:
    passage_id: str
    doc_key: str
    title: str
    publisher: str
    version: str | None
    url: str | None
    reference: str
    text: str

    def as_response(self) -> dict[str, Any]:
        compact = " ".join(self.text.split())
        excerpt = compact if len(compact) <= 360 else compact[:357].rsplit(" ", 1)[0] + "..."
        return {
            "doc_key": self.doc_key,
            "title": self.title,
            "publisher": self.publisher,
            "version": self.version,
            "url": self.url,
            "passage_id": self.passage_id,
            "reference": self.reference,
            "excerpt": excerpt,
        }


class CorpusLoader:
    def __init__(self, corpus_dir: Path, chunk_size: int = 1200, overlap: int = 160):
        if chunk_size < 1 or overlap < 0 or overlap >= chunk_size:
            raise ValueError("chunk_size must be positive and overlap smaller than chunk_size")
        self.corpus_dir = corpus_dir.resolve()
        self.chunk_size = chunk_size
        self.overlap = overlap

    def manifest_documents(self) -> list[CorpusDocument]:
        manifest_path = self.corpus_dir / "manifest.json"
        if not manifest_path.exists():
            return []
        try:
            raw_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ValueError(f"Cannot read corpus manifest {manifest_path}: {exc}") from exc

        entries = raw_manifest.get("documents", []) if isinstance(raw_manifest, dict) else raw_manifest
        if not isinstance(entries, list):
            raise TypeError("corpus/manifest.json must be a list or an object with a documents list")

        documents: list[CorpusDocument] = []
        seen_keys: set[str] = set()
        required = ("doc_key", "title", "publisher", "version", "url")
        for index, entry in enumerate(entries):
            if not isinstance(entry, dict):
                raise TypeError(f"Manifest entry {index} must be an object")
            missing = [key for key in required if key not in entry]
            if missing:
                raise ValueError(f"Manifest entry {index} is missing required fields: {missing}")
            doc_key = _required_string(entry, "doc_key", index)
            if doc_key in seen_keys:
                raise ValueError(f"Duplicate doc_key in manifest: {doc_key}")
            seen_keys.add(doc_key)
            title = _required_string(entry, "title", index)
            publisher = _required_string(entry, "publisher", index)
            version = _optional_string(entry["version"], "version", index)
            url = _optional_string(entry["url"], "url", index)
            filename = entry.get("file")
            if filename is not None and (not isinstance(filename, str) or not filename.strip()):
                raise ValueError(f"Manifest entry {index} file must be a non-empty string")
            file_path = self._resolve_file(filename or doc_key)
            documents.append(
                CorpusDocument(doc_key, title, publisher, version, url, str(file_path))
            )
        return documents

    def load_passages(self) -> list[Passage]:
        passages: list[Passage] = []
        for document in self.manifest_documents():
            path = Path(document.file)
            if not path.is_file():
                raise FileNotFoundError(f"Corpus document not found for {document.doc_key}: {path}")
            suffix = path.suffix.lower()
            if suffix not in SUPPORTED_SUFFIXES:
                raise ValueError(f"Unsupported corpus document type: {path.name}")
            for reference, text in self._read_sections(path):
                for chunk_index, chunk in enumerate(
                    _split_text(text, self.chunk_size, self.overlap)
                ):
                    if not chunk.strip():
                        continue
                    digest = hashlib.sha256(
                        f"{document.doc_key}\0{reference}\0{chunk_index}\0{chunk}".encode()
                    ).hexdigest()[:20]
                    passages.append(Passage(
                        passage_id=f"{document.doc_key}:{digest}",
                        doc_key=document.doc_key,
                        title=document.title,
                        publisher=document.publisher,
                        version=document.version,
                        url=document.url,
                        reference=reference,
                        text=chunk.strip(),
                    ))
        return passages

    def _resolve_file(self, filename: str) -> Path:
        candidate = Path(filename)
        if candidate.suffix.lower() not in SUPPORTED_SUFFIXES:
            candidate = candidate.with_suffix(".pdf")
        resolved = (self.corpus_dir / candidate).resolve()
        if not resolved.is_relative_to(self.corpus_dir):
            raise ValueError(f"Corpus file must remain inside corpus/: {filename}")
        return resolved

    def _read_sections(self, path: Path) -> list[tuple[str, str]]:
        if path.suffix.lower() == ".pdf":
            reader = PdfReader(str(path))
            return [
                (f"page {page_number}", page.extract_text() or "")
                for page_number, page in enumerate(reader.pages, start=1)
            ]
        text = path.read_text(encoding="utf-8")
        if path.suffix.lower() == ".txt":
            return [("text", text)]
        sections: list[tuple[str, list[str]]] = []
        current_heading = "document"
        current_lines: list[str] = []
        for line in text.splitlines():
            match = HEADING.match(line)
            if match:
                if current_lines:
                    sections.append((current_heading, current_lines))
                current_heading = match.group(2).strip()
                current_lines = []
            else:
                current_lines.append(line)
        if current_lines or not sections:
            sections.append((current_heading, current_lines))
        return [(heading, "\n".join(lines)) for heading, lines in sections]


def _required_string(entry: dict[str, Any], key: str, index: int) -> str:
    value = entry[key]
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Manifest entry {index} field {key} must be a non-empty string")
    return value.strip()


def _optional_string(value: Any, key: str, index: int) -> str | None:
    if value is None or value == "":
        return None
    if not isinstance(value, str):
        raise TypeError(f"Manifest entry {index} field {key} must be a string or null")
    return value.strip() or None


def _split_text(text: str, chunk_size: int, overlap: int) -> list[str]:
    normalized = re.sub(r"\s+", " ", text).strip()
    if not normalized:
        return []
    chunks: list[str] = []
    start = 0
    while start < len(normalized):
        end = min(start + chunk_size, len(normalized))
        if end < len(normalized):
            boundary = normalized.rfind(" ", start, end)
            if boundary > start:
                end = boundary
        chunks.append(normalized[start:end].strip())
        if end == len(normalized):
            break
        start = max(start + 1, end - overlap)
    return chunks
