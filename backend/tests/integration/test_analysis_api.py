import os
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker

from app.api.analysis import get_db
from app.db.models import InkChange, WorkSession
from app.main import app


@pytest.fixture()
def db_client():
    url = os.environ.get("DATABASE_URL")
    if not url:
        pytest.skip("Requires migrated PostgreSQL test database")
    engine = create_engine(url)
    factory = sessionmaker(bind=engine)

    def testing_db():
        with factory() as db:
            yield db

    app.dependency_overrides[get_db] = testing_db
    try:
        with TestClient(app) as client:
            yield client, factory
    finally:
        app.dependency_overrides.pop(get_db, None)
        engine.dispose()


def test_http_persists_strokes_and_detects_conflicting_retry(db_client):
    client, factory = db_client
    session_id = str(uuid4())
    change = {
        "revision": 1,
        "change": {
            "type": "stroke_added",
            "stroke": {
                "id": "stroke-a",
                "toolType": "pen",
                "points": [
                    {"x": 12.25, "y": 8.5, "elapsedTimeMillis": 0},
                    {"x": 15.75, "y": 11.5, "elapsedTimeMillis": 17},
                ],
            },
        },
    }
    payload = {"workSessionId": session_id, "revision": 1, "changes": [change]}
    first = client.post("/analysis/check", json=payload)
    assert first.status_code == 200
    assert first.json() == {"acceptedRevision": 1, "status": "accepted"}

    # The service must commit before returning. A *new* SQLAlchemy
    # session must read back the exact payload, not an uncommitted object.
    with factory() as db:
        session = db.get(WorkSession, session_id)
        assert session is not None and session.accepted_revision == 1
        saved = db.scalars(
            select(InkChange).where(InkChange.work_session_id == session_id)
        ).all()
        assert len(saved) == 1
        assert saved[0].payload == change["change"]

    retry = client.post("/analysis/check", json=payload)
    assert retry.status_code == 200
    assert retry.json()["acceptedRevision"] == 1

    conflicting = {
        **payload,
        "changes": [{
            **change,
            "change": {
                **change["change"],
                "stroke": {**change["change"]["stroke"], "id": "stroke-b"},
            },
        }],
    }
    assert client.post("/analysis/check", json=conflicting).status_code == 409

    gap = {
        "workSessionId": session_id,
        "revision": 3,
        "changes": [{"revision": 3, "change": {"type": "document_cleared"}}],
    }
    assert client.post("/analysis/check", json=gap).status_code == 409

    with factory() as db:
        assert db.get(WorkSession, session_id).accepted_revision == 1
        assert len(db.scalars(select(InkChange).where(
            InkChange.work_session_id == session_id
        )).all()) == 1
