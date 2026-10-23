from fastapi import APIRouter
from app.schemas.analysis import AnalysisRequestPayload, AnalysisResponsePayload


router = APIRouter(
    prefix="/analysis",
    tags=["analysis"],
)


@router.post(
    "/check",
    response_model=AnalysisResponsePayload,
)
async def check_analysis(
    request: AnalysisRequestPayload,
) -> AnalysisResponsePayload:
    return AnalysisResponsePayload(
        acceptedRevision=request.revision,
        status="accepted",
    )
