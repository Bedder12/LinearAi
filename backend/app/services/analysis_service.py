from app.domain.analysis_result import AnalysisResult
from app.schemas.analysis import AnalysisRequestPayload


class AnalysisService:
    def analyze(
        self,
        request: AnalysisRequestPayload,
    ) -> AnalysisResult:
        return AnalysisResult(
            accepted_revision=request.revision,
            status="accepted",
        )
