from fastapi import APIRouter

from app.schemas.analysis import (
    AnalysisRequestPayload,
    AnalysisResponsePayload,
)
from app.services.analysis_service import AnalysisService

router = APIRouter(
    prefix="/analysis",
    tags=["analysis"],
)

analysis_service = AnalysisService()


@router.post(
    "/check",
    response_model=AnalysisResponsePayload,
)
async def check_analysis(
    request: AnalysisRequestPayload,
) -> AnalysisResponsePayload:
    result = analysis_service.analyze(request)

    return AnalysisResponsePayload(
        acceptedRevision=result.accepted_revision,
        status=result.status,
    )
