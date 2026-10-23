from app.schemas.analysis import AnalysisRequestPayload
from app.services.analysis_service import AnalysisService


def test_analyze_accepts_current_revision() -> None:
    service = AnalysisService()
    request = AnalysisRequestPayload(
        revision=4,
        changes=[],
    )

    result = service.analyze(request)

    assert result.accepted_revision == 4
    assert result.status == "accepted"
