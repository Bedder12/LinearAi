import pytest
from fastapi.testclient import TestClient

from app.api.analysis import get_db
from app.main import app


@pytest.fixture()
def client():
    def unused_database():
        yield None

    app.dependency_overrides[get_db] = unused_database
    try:
        with TestClient(app) as test_client:
            yield test_client
    finally:
        app.dependency_overrides.pop(get_db, None)


def test_invalid_request_does_not_need_database(client):
    response = client.post("/analysis/check", json={"changes": []})
    assert response.status_code == 422
    assert response.headers["content-type"] == "application/problem+json"
    assert response.json()["type"] == "/problems/request-validation"
    assert response.json()["status"] == 422


def test_invalid_session_id(client):
    response = client.post(
        "/analysis/check",
        json={"workSessionId": "not-a-uuid", "revision": 1, "changes": []},
    )
    assert response.status_code == 422
