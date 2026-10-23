from dataclasses import dataclass


@dataclass(frozen=True)
class AnalysisResult:
    accepted_revision: int
    status: str
