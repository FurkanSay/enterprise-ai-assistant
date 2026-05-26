"""Session CRUD — list, get, delete. Mutations happen via /chat (implicit creation)."""

from fastapi import APIRouter, status
from pydantic import BaseModel

from aiengine.core.tenant import get_current_tenant

router = APIRouter()


class SessionSummary(BaseModel):
    id: str
    title: str
    model: str
    message_count: int
    created_at: str
    updated_at: str


@router.get("/sessions")
async def list_sessions(limit: int = 20) -> list[SessionSummary]:
    """List sessions for current tenant+user."""
    _ = get_current_tenant()
    # TODO: query session repo
    return []


@router.get("/sessions/{session_id}")
async def get_session(session_id: str) -> SessionSummary:
    """Get session metadata."""
    _ = get_current_tenant()
    # TODO: fetch from repo
    raise NotImplementedError


@router.delete("/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_session(session_id: str) -> None:
    """Soft-delete a session (audit log retains it)."""
    _ = get_current_tenant()
    # TODO: soft delete in repo
