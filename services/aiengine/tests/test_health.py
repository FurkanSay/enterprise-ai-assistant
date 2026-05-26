"""Minimal smoke test — the app boots and /health/live responds."""

import pytest
from fastapi.testclient import TestClient

from aiengine.main import create_app


@pytest.fixture()
def client() -> TestClient:
    return TestClient(create_app())


def test_liveness(client: TestClient) -> None:
    response = client.get("/health/live")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "aiengine"


def test_readiness(client: TestClient) -> None:
    response = client.get("/health/ready")
    assert response.status_code == 200
