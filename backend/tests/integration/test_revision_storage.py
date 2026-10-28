import os
from uuid import uuid4

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from app.db.models import InkChange, WorkSession
from app.schemas.analysis import AnalysisRequestPayload
from app.services.revision_sync import (
    InvalidInkChange,
    RevisionConflict,
    RevisionGap,
    apply_revisions,
)


@pytest.fixture()
def db_engine():
    url = os.environ.get("DATABASE_URL")
    if not url:
        pytest.skip("Requires DATABASE_URL with migrated PostgreSQL database")
    engine = create_engine(url)
    yield engine
    engine.dispose()


def request(session_id, revision, entries):
    return AnalysisRequestPayload.model_validate({
        "workSessionId": str(session_id),
        "revision": revision,
        "changes": [
            {"revision": rev, "change": change}
            for rev, change in entries
        ],
    })


def clear():
    return {"type": "document_cleared"}


def stroke(stroke_id):
    return {"type": "stroke_added", "stroke": {
        "id": stroke_id, "toolType": "pen",
        "points": [{"x": 2.5, "y": 4.0, "elapsedTimeMillis": 5}],
    }}


def test_persist_replay_retry_and_conflict(db_engine):
    sid = uuid4()
    first = request(sid, 1, [(1, stroke("a")), (1, stroke("b"))])
    second = request(sid, 2, [(2, {"type": "stroke_removed", "strokeId": "a"})])
    with Session(db_engine) as db:
        assert apply_revisions(db, first) == 1
        assert apply_revisions(db, first) == 1
        assert apply_revisions(db, second) == 2

    # New connection observes the committed original point payload.
    with Session(db_engine) as db:
        row = db.get(WorkSession, sid)
        assert row is not None and row.accepted_revision == 2
        events = db.scalars(select(InkChange).where(
            InkChange.work_session_id == sid
        ).order_by(InkChange.revision, InkChange.position)).all()
        assert len(events) == 3
        assert events[0].payload["stroke"]["points"][0]["x"] == 2.5
        db.rollback()  # End the read-only transaction before testing new writes.

        with pytest.raises(RevisionConflict):
            apply_revisions(db, request(sid, 1, [(1, stroke("different"))]))
        with pytest.raises(RevisionGap):
            apply_revisions(db, request(sid, 4, [(4, clear())]))
        with pytest.raises(InvalidInkChange):
            apply_revisions(db, request(sid, 3, [(3, {
                "type": "stroke_removed", "strokeId": "unknown"
            })]))
        assert db.get(WorkSession, sid).accepted_revision == 2


def test_batch_revisions_and_repeated_request(db_engine):
    sid = uuid4()
    batch = request(sid, 2, [(1, stroke("p")), (2, clear())])
    with Session(db_engine) as db:
        assert apply_revisions(db, batch) == 2
        assert apply_revisions(db, batch) == 2


def test_missing_revision_does_not_advance(db_engine):
    sid = uuid4()
    with Session(db_engine) as db:
        with pytest.raises(RevisionGap):
            apply_revisions(db, request(sid, 2, [(2, clear())]))
        assert db.get(WorkSession, sid) is None
