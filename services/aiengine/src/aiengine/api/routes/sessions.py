"""Session CRUD — list, get, delete.

Session creation happens implicitly the first time /chat is called
without a session_id. These endpoints are read + delete only — the
session_id flows back to the client via the SSE `session` event.
"""

from uuid import UUID

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

from aiengine.agent.repository import (
    delete_session,
    fork_session,
    get_session as get_session_row,
    list_messages,
    list_sessions,
)
from aiengine.core.db import tenant_session
from aiengine.core.tenant import get_current_tenant

router = APIRouter()


class SessionSummary(BaseModel):
    id: str
    title: str
    model: str
    message_count: int
    created_at: str
    updated_at: str
    parent_session_id: str | None = None
    forked_from_message_id: str | None = None
    mode: str = "normal"


class ForkSessionRequest(BaseModel):
    # Optional. If absent, backend forks at the latest persisted message
    # of the parent session — the most common UX (user just read an
    # assistant reply and wants to branch from it).
    up_to_message_id: str | None = None


class SessionMessage(BaseModel):
    id: str
    role: str
    text: str
    sequence_number: int
    created_at: str


class SessionDetail(SessionSummary):
    messages: list[SessionMessage]


def _summary(s) -> SessionSummary:  # type: ignore[no-untyped-def]
    return SessionSummary(
        id=s.id,
        title=s.title or "",
        model=s.model or "",
        message_count=s.message_count,
        created_at=s.created_at.isoformat(),
        updated_at=s.updated_at.isoformat(),
        parent_session_id=s.parent_session_id,
        forked_from_message_id=s.forked_from_message_id,
        mode=s.mode or "normal",
    )


@router.get("/sessions")
async def list_sessions_endpoint(limit: int = 20) -> list[SessionSummary]:
    """Newest-first session list for the current tenant."""
    tenant = get_current_tenant()
    async with tenant_session(tenant) as db:
        rows = await list_sessions(db, limit=limit)
    return [_summary(r) for r in rows]


@router.get("/sessions/{session_id}")
async def get_session_endpoint(session_id: str) -> SessionDetail:
    """Session metadata + full message history (text blocks only)."""
    tenant = get_current_tenant()
    try:
        session_uuid = UUID(session_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="invalid session id") from exc

    async with tenant_session(tenant) as db:
        session = await get_session_row(db, session_uuid)
        if session is None:
            raise HTTPException(status_code=404, detail="session not found")
        messages = await list_messages(db, session_uuid)

    flat_messages: list[SessionMessage] = []
    for m in messages:
        # `Message.role` is stored as the enum *value* (a string) because
        # state.Message uses `use_enum_values=True`. So comparisons and
        # serialisation both go through the raw string — no .value access.
        role_str = m.role if isinstance(m.role, str) else m.role.value
        if role_str not in ("user", "assistant"):
            continue
        text = "\n".join(b.text for b in m.blocks if b.type == "text" and b.text)
        if not text:
            continue
        flat_messages.append(
            SessionMessage(
                id=m.id,
                role=role_str,
                text=text,
                sequence_number=m.sequence_number,
                created_at=m.created_at.isoformat(),
            )
        )

    return SessionDetail(
        id=session.id,
        title=session.title or "",
        model=session.model or "",
        message_count=session.message_count,
        created_at=session.created_at.isoformat(),
        updated_at=session.updated_at.isoformat(),
        messages=flat_messages,
    )


@router.post("/sessions/{session_id}/fork", status_code=status.HTTP_201_CREATED)
async def fork_session_endpoint(
    session_id: str, body: ForkSessionRequest
) -> SessionSummary:
    """Branch the conversation at a specific message. The new session
    starts with a verbatim copy of every message up to and including
    `up_to_message_id` and accrues independently afterwards.
    Useful when the assistant proposes multiple paths and you want to
    explore each without losing the other."""
    tenant = get_current_tenant()
    try:
        parent_uuid = UUID(session_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="invalid session id") from exc

    async with tenant_session(tenant) as db:
        if body.up_to_message_id is None:
            # Default — fork at the latest message of the parent.
            parent_msgs = await list_messages(db, parent_uuid)
            if not parent_msgs:
                raise HTTPException(
                    status_code=400,
                    detail="cannot fork an empty session",
                )
            msg_uuid = UUID(parent_msgs[-1].id)
        else:
            try:
                msg_uuid = UUID(body.up_to_message_id)
            except ValueError as exc:
                raise HTTPException(
                    status_code=400, detail="invalid message id"
                ) from exc

        child = await fork_session(
            db,
            tenant,
            parent_session_id=parent_uuid,
            up_to_message_id=msg_uuid,
        )
    if child is None:
        raise HTTPException(
            status_code=404,
            detail="parent session or fork-point message not found",
        )
    return _summary(child)


@router.delete("/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_session_endpoint(session_id: str) -> None:
    """Soft-delete (sets archived_at). The row stays for audit."""
    tenant = get_current_tenant()
    try:
        session_uuid = UUID(session_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="invalid session id") from exc

    async with tenant_session(tenant) as db:
        ok = await delete_session(db, session_uuid)
    if not ok:
        raise HTTPException(status_code=404, detail="session not found")
