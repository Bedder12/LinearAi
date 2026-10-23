from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

WORK_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000"


def test_analysis_check_accepts_valid_request() -> None:
    response = client.post(
        "/analysis/check",
        json={
            "workSessionId": WORK_SESSION_ID,
            "revision": 3,
            "changes": [
                {
                    "revision": 3,
                    "change": {
                        "type": "document_cleared",
                    },
                }
            ],
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "acceptedRevision": 3,
        "status": "accepted",
    }


def test_analysis_check_rejects_missing_required_fields() -> None:
    response = client.post(
        "/analysis/check",
        json={
            "changes": [],
        },
    )

    assert response.status_code == 422


def test_analysis_check_rejects_invalid_work_session_id() -> None:
    response = client.post(
        "/analysis/check",
        json={
            "workSessionId": "not-a-uuid",
            "revision": 1,
            "changes": [],
        },
    )

    assert response.status_code == 422
