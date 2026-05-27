"""POST /v1/chat — main agent endpoint.

Returns Server-Sent Events (SSE) stream:
  event: token       data: {"text": "..."}
  event: tool_use    data: {"id": "...", "name": "doc_search", "input": {...}}
  event: tool_result data: {"id": "...", "output": {...}, "is_error": false}
  event: usage       data: {"input_tokens": 123, "output_tokens": 456}
  event: done        data: {"sources": [...]}

Every event is also published to Redis on the
``stream.<tenant>.<session>`` channel so the Realtime service can fan it
out to WebSocket subscribers. The fanout is best-effort — a Redis outage
must not break the SSE path.
"""

from collections.abc import AsyncIterator
from typing import Annotated

import orjson
from fastapi import APIRouter, Body
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from aiengine.agent.loop import run_turn
from aiengine.core.events import publish_event
from aiengine.core.tenant import get_current_tenant

router = APIRouter()


class ChatRequest(BaseModel):
    session_id: str | None = Field(
        default=None,
        description="Resume an existing session, or omit to start a new one.",
    )
    message: str = Field(..., min_length=1, max_length=32_000)
    model: str | None = Field(
        default=None,
        description="Override default model. Aliases like 'opus', 'sonnet', 'haiku'.",
    )
    allowed_tools: list[str] | None = Field(
        default=None,
        description="If set, restrict tool catalog to these names.",
    )


@router.post("/chat")
async def chat(req: Annotated[ChatRequest, Body()]) -> EventSourceResponse:
    """Stream agent loop output as SSE + Redis pub/sub fanout."""
    tenant = get_current_tenant()
    tenant_id = str(tenant.tenant_id)
    # Until the first AgentEvent("session", ...) lands we don't know the
    # actual session id. For events emitted before that (none today) we
    # fall back to the client-provided session_id.
    current_session_id: str | None = req.session_id

    # Buffer events that arrive before we know the session id (only
    # possible when the client did not pass session_id AND run_turn
    # emits something *before* the "session" event — today it does not,
    # but we belt-and-suspender so a future reordering does not silently
    # drop tokens from the WebSocket path).
    pre_session_buffer: list[tuple[str, dict]] = []

    async def event_stream() -> AsyncIterator[dict[str, str]]:
        nonlocal current_session_id
        async for event in run_turn(
            tenant=tenant,
            session_id=req.session_id,
            user_message=req.message,
            model_override=req.model,
            allowed_tools=req.allowed_tools,
        ):
            if event.kind == "session":
                current_session_id = event.data.get("id") or current_session_id
                # Flush anything we held while waiting for the session id.
                if current_session_id and pre_session_buffer:
                    for kind, data in pre_session_buffer:
                        await publish_event(
                            tenant_id=tenant_id,
                            session_id=current_session_id,
                            event_kind=kind,
                            payload=data,
                        )
                    pre_session_buffer.clear()

            if current_session_id:
                await publish_event(
                    tenant_id=tenant_id,
                    session_id=current_session_id,
                    event_kind=event.kind,
                    payload=event.data,
                )
            else:
                # No session id yet — buffer for replay after "session".
                pre_session_buffer.append((event.kind, event.data))

            yield {
                "event": event.kind,
                "data": orjson.dumps(event.data).decode("utf-8"),
            }

    return EventSourceResponse(event_stream())
