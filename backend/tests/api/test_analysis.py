from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_analysis_check_accepts_valid_request() -> None:
    response = client.post(
        "/analysis/check",
        json={
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


def test_analysis_check_rejects_invalid_request() -> None:
    response = client.post(
        "/analysis/check",
        json={
            "changes": [],
        },
    )

    assert response.status_code == 422
