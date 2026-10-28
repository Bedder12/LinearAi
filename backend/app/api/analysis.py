from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

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


@router.post("/check", response_model=AnalysisResponsePayload)
def check_analysis(
    request: AnalysisRequestPayload,
    db: Session = Depends(get_db),
) -> AnalysisResponsePayload:
    try:
        revision = apply_revisions(db, request)
    except RevisionGap as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    except RevisionConflict as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    except InvalidInkChange as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return AnalysisResponsePayload(acceptedRevision=revision, status="accepted")
