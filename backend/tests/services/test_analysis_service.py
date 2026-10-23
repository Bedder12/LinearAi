from uuid import UUID

from app.schemas.analysis import AnalysisRequestPayload
from app.services.analysis_service import AnalysisService


def test_analyze_accepts_current_revision() -> None:
    service = AnalysisService()
    request = AnalysisRequestPayload(
        workSessionId=UUID("550e8400-e29b-41d4-a716-446655440000"),
        revision=4,
        changes=[],
    )

    result = service.analyze(request)

    assert result.accepted_revision == 4
    assert result.status == "accepted"
