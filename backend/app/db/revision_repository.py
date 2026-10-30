"""SQLAlchemy persistence operations for synchronized ink revisions.

The caller owns the transaction boundary.
"""

from uuid import UUID

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.db.models import InkChange, WorkSession


def get_or_create_session_for_update(db: Session, session_id: UUID) -> WorkSession:
    db.execute(
        insert(WorkSession)
        .values(id=session_id, accepted_revision=0)
        .on_conflict_do_nothing(index_elements=[WorkSession.id])
    )
    session = db.scalar(
        select(WorkSession)
        .where(WorkSession.id == session_id)
        .with_for_update()
    )
    if session is None:
        raise RuntimeError("work session could not be loaded")
    return session


def load_revision_events(db: Session, session_id: UUID, revision: int) -> list[dict]:
    events = db.scalars(
        select(InkChange)
        .where(InkChange.work_session_id == session_id)
        .where(InkChange.revision == revision)
        .order_by(InkChange.position)
    ).all()
    return [event.payload for event in events]


def load_committed_events(db: Session, session_id: UUID) -> list[dict]:
    events = db.scalars(
        select(InkChange)
        .where(InkChange.work_session_id == session_id)
        .order_by(InkChange.revision, InkChange.position)
    ).all()
    return [event.payload for event in events]


def add_change(
    db: Session, session_id: UUID, revision: int, position: int, change: dict
) -> None:
    db.add(
        InkChange(
            work_session_id=session_id,
            revision=revision,
            position=position,
            payload=change,
        )
    )
