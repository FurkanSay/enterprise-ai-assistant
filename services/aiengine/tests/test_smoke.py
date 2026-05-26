"""Smoke tests — verify app boots + critical endpoints respond.

Run: cd services/aiengine && uv run pytest
"""

from fastapi.testclient import TestClient

from aiengine.main import create_app


def test_app_boots() -> None:
    """App can be constructed without import errors."""
    app = create_app()
    assert app is not None


def test_liveness_endpoint() -> None:
    """/health/live returns 200 with status='ok' and no tenant context needed."""
    client = TestClient(create_app())
    response = client.get("/health/live")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "aiengine"


def test_tenant_context_required_for_chat() -> None:
    """Calling /v1/chat without tenant headers should 401."""
    client = TestClient(create_app())
    response = client.post(
        "/v1/chat", json={"message": "hi"}, headers={}  # no tenant headers
    )
    assert response.status_code == 401
    assert response.json()["code"] == "aiengine.tenant_context_missing"


def test_tool_registry_has_builtins() -> None:
    """Built-in tools auto-register on startup."""
    from aiengine.tools.registry import get_tool_registry

    registry = get_tool_registry()
    names = {t.spec.name for t in registry.all_tools()}
    assert "doc_search" in names
    assert "web_fetch" in names
