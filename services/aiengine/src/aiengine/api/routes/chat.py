"""POST /v1/chat — main agent endpoint.

Returns Server-Sent Events (SSE) stream:
  event: token       data: {"text": "..."}
  event: tool_use    data: {"id": "...", "name": "doc_search", "input": {...}}
  event: tool_result data: {"id": "...", "output": {...}, "is_error": false}
  event: usage       data: {"input_tokens": 123, "output_tokens": 456}
  event: done        data: {"sources": [...]}
"""

from collections.abc import AsyncIterator
from typing import Annotated

import orjson
from fastapi import APIRouter, Body
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from aiengine.agent.loop import run_turn
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
    """Stream agent loop output as SSE."""
    tenant = get_current_tenant()

    async def event_stream() -> AsyncIterator[dict[str, str]]:
        async for event in run_turn(
            tenant=tenant,
            session_id=req.session_id,
            user_message=req.message,
            model_override=req.model,
            allowed_tools=req.allowed_tools,
        ):
            yield {
                "event": event.kind,
                "data": orjson.dumps(event.data).decode("utf-8"),
            }

    return EventSourceResponse(event_stream())
