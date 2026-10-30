from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.api.problems import ProblemDetails, problem_response
from app.schemas.analysis import AnalysisRequestPayload, AnalysisResponsePayload
from app.services.revision_sync import (
    InvalidInkChange,
    RevisionConflict,
    RevisionGap,
    apply_revisions,
)

router = APIRouter(prefix="/analysis", tags=["analysis"])


def get_db():
    # Import and initialize lazily to preserve the standalone /health endpoint.
    # Use a cached session factory for connection-pool reuse.
    from app.db.session_provider import get_session_factory

    factory = get_session_factory()
    with factory() as session:
        yield session


@router.post(
    "/check",
    response_model=AnalysisResponsePayload,
    responses={
        409: {"model": ProblemDetails, "description": "Revision synchronization conflict"},
        422: {"model": ProblemDetails, "description": "Invalid request or ink change"},
    },
)
def check_analysis(
    request: AnalysisRequestPayload,
    db: Annotated[Session, Depends(get_db)],
) -> AnalysisResponsePayload | JSONResponse:
    try:
        revision = apply_revisions(db, request)
    except RevisionGap as exc:
        return problem_response(
            status=409, kind="revision-gap", title="Revision gap",
            detail="The request is missing required revisions.",
            accepted_revision=exc.accepted_revision,
        )
    except RevisionConflict:
        return problem_response(
            status=409, kind="revision-conflict", title="Revision conflict",
            detail="A previously accepted revision has different content.",
        )
    except InvalidInkChange as exc:
        return problem_response(
            status=422, kind="invalid-ink-change", title="Invalid ink change",
            detail=str(exc),
        )
    return AnalysisResponsePayload(acceptedRevision=revision, status="accepted")
