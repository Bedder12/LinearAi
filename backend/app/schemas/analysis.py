from typing import Literal
from uuid import UUID

from pydantic import BaseModel


class StrokePointPayload(BaseModel):
    x: float
    y: float
    elapsedTimeMillis: int


class StrokePayload(BaseModel):
    id: str
    toolType: str
    points: list[StrokePointPayload]


class StrokeAddedPayload(BaseModel):
    type: Literal["stroke_added"]
    stroke: StrokePayload


class StrokeRemovedPayload(BaseModel):
    type: Literal["stroke_removed"]
    strokeId: str


class DocumentClearedPayload(BaseModel):
    type: Literal["document_cleared"]


InkChangePayload = StrokeAddedPayload | StrokeRemovedPayload | DocumentClearedPayload


class PendingInkChangePayload(BaseModel):
    revision: int
    change: InkChangePayload


class AnalysisRequestPayload(BaseModel):
    workSessionId: UUID
    revision: int
    changes: list[PendingInkChangePayload]


class AnalysisResponsePayload(BaseModel):
    acceptedRevision: int
    status: str
