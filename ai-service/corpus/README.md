# User-managed retrieval corpus

Add only official documents that your team has obtained and reviewed. Supported formats are PDF, plain text (`.txt`), and Markdown (`.md`). This repository deliberately includes no official regulatory text.

For each document, add an entry to `manifest.json` with these required metadata keys:

```json
[
  {
    "doc_key": "your-stable-document-key",
    "title": "Official document title",
    "publisher": "Publishing authority",
    "version": "edition or date",
    "url": "https://official-source.example/document",
    "file": "your-document.pdf"
  }
]
```

`file` is an optional filename relative to this folder. If omitted, the service looks for a file named `<doc_key>.pdf`; explicitly provide the filename for `.txt` and `.md` files. The required metadata keys must still be present; `version` and `url` may be `null` when unavailable. Documents must remain inside this folder.

To index the manifest documents, run `python -m scripts.ingest_corpus` from the `ai-service` directory. PDFs are referenced by page and Markdown files by heading; text files use a text reference. The configured chunk size and overlap preserve these references for retrieval citations. Re-index after changing a document or its manifest entry.

Test-only sample passages belong under `tests/fixtures/` and must have filenames beginning with `FAKE_FOR_TESTS`. The ingestion script reads only entries listed in this manifest and never scans test fixtures.
