from __future__ import annotations

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from ai_service.citizen import build_citizen_summary
from ai_service.config import Settings
from ai_service.llm.openai_compatible import create_brief_provider
from ai_service.models import (
    CitizenSummaryRequest,
    CitizenSummaryResponse,
    InvestigationRequest,
    InvestigationResponse,
    RetrieveRequest,
)
from ai_service.rag.retrieval import create_retrieval_service
from ai_service.service import InvestigationService


def create_app(
    settings: Settings | None = None,
    retrieval=None,
    provider=None,
) -> FastAPI:
    config = settings or Settings.from_env()
    rag = retrieval or create_retrieval_service(config)
    llm = provider or create_brief_provider(config)
    investigation = InvestigationService(llm, rag)

    application = FastAPI(
        title="AeroCause AI Service",
        version="1.0.0",
        description="Evidence-grounded air quality narration and official document retrieval.",
    )

    # Enable CORS for local dev & Vite frontend
    application.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    application.state.settings = config
    application.state.retrieval = rag
    application.state.investigation = investigation

    @application.get("/health")
    def health() -> dict:
        return {
            "status": "ok",
            "llm_provider": llm.mode,
            "embedding_provider": config.embedding_provider,
        }

    @application.post("/investigate", response_model=InvestigationResponse)
    def investigate(request: InvestigationRequest) -> InvestigationResponse:
        try:
            return investigation.investigate(request.root)
        except (RuntimeError, ValueError, OSError, httpx.HTTPError) as exc:
            raise HTTPException(
                status_code=503,
                detail=f"Investigation service failed: {type(exc).__name__}: {exc}",
            ) from exc

    @application.post("/citizen-summary", response_model=CitizenSummaryResponse)
    def citizen_summary(request: CitizenSummaryRequest) -> CitizenSummaryResponse:
        return build_citizen_summary(request)

    @application.post("/retrieve")
    def retrieve(request: RetrieveRequest) -> dict:
        try:
            passages = rag.retrieve(request.query, request.top_k)
            return {"passages": [passage.as_response() for passage in passages]}
        except (RuntimeError, ValueError, OSError, httpx.HTTPError) as exc:
            raise HTTPException(
                status_code=503,
                detail=f"Document retrieval failed: {type(exc).__name__}: {exc}",
            ) from exc

    return application


app = create_app()