from __future__ import annotations

from abc import ABC, abstractmethod

from ai_service.models import BriefContent
from ai_service.rag.corpus import Passage


class BriefProvider(ABC):
    mode: str

    @abstractmethod
    def generate(
        self,
        evidence_package: dict,
        passages: list[Passage],
        violations: list[str] | None = None,
    ) -> BriefContent:
        raise NotImplementedError
