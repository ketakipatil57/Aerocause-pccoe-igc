from __future__ import annotations

import logging

from ai_service.config import Settings
from ai_service.rag.ingestion import ingest_corpus

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
log = logging.getLogger("aerocause.corpus")


def main() -> None:
    settings = Settings.from_env()
    count = ingest_corpus(settings)
    log.info("Indexed %d passages from the configured corpus", count)


if __name__ == "__main__":
    main()
