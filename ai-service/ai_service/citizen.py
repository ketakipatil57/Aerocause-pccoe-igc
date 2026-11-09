from __future__ import annotations

from ai_service.models import CitizenSummaryRequest, CitizenSummaryResponse

TEMPLATES = {
    "en": "The AQI is {aqi} ({category}). Available evidence may be linked to: {evidence}.",
    "hi": "वायु गुणवत्ता सूचकांक {aqi} ({category}) है। उपलब्ध साक्ष्य का संबंध {evidence} से हो सकता है।",
    "mr": "वायु गुणवत्ता निर्देशांक {aqi} ({category}) आहे. उपलब्ध पुराव्याचा संबंध {evidence} शी असू शकतो.",
}
UNKNOWN_EVIDENCE = {
    "en": "an uncertain pattern",
    "hi": "एक अनिश्चित स्वरूप",
    "mr": "एका अनिश्चित नमुन्याशी",
}


def build_citizen_summary(request: CitizenSummaryRequest) -> CitizenSummaryResponse:
    evidence = request.evidence_summary.strip() or UNKNOWN_EVIDENCE[request.language]
    return CitizenSummaryResponse(
        summary=TEMPLATES[request.language].format(
            aqi=request.aqi,
            category=request.category,
            evidence=evidence,
        ),
        precautions=request.precaution_templates,
        language=request.language,
        needs_native_speaker_review=request.language in {"hi", "mr"},
    )
