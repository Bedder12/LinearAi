from fastapi.testclient import TestClient
from app.main import app

WORK_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000"


def test_invalid_request_does_not_need_database() -> None:
    client = TestClient(app)
    response = client.post("/analysis/check", json={"changes": []})
    assert response.status_code == 422


def test_invalid_session_id() -> None:
    client = TestClient(app)
    response = client.post(
        "/analysis/check",
        json={"workSessionId": "not-a-uuid", "revision": 1, "changes": []},
    )
    assert response.status_code == 422
