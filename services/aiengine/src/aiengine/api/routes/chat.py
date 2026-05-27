"""POST /v1/chat — main agent endpoint.

Two execution paths share the same SSE shape so the frontend doesn't
care which one ran:

  mode=normal       → full LLM agent loop (tools, doc_search, etc.)
  mode=deep_search  → bypass LLM entirely. Treat the user message as
                      a literature-search query, run the aggregator
                      directly, fire background ingestion tasks for
                      every result, and emit a `paper_list` tool_result
                      followed by a one-line confirmation token.

Why bypass the LLM in Deep Search:
  - The model adds latency, costs tokens, sometimes calls
    literature_search with a rewritten query that misses the user's
    intent (especially numbers like "30 makale").
  - In Deep Search the user explicitly asked for "search papers", not
    "have a conversation about papers". Treat the input as a query
    string, not a prompt.

Every event is also published to Redis on the
``stream.<tenant>.<session>`` channel so the Realtime service can fan
it out to WebSocket subscribers. The fanout is best-effort — a Redis
outage must not break the SSE path.
"""

import asyncio
import re
from collections.abc import AsyncIterator
from datetime import datetime, timezone
from typing import Annotated, Literal
from uuid import UUID

import orjson
import structlog
from fastapi import APIRouter, Body
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from aiengine.agent.loop import run_turn
from aiengine.agent.repository import append_message, create_session, get_session
from aiengine.agent.state import ContentBlock, MessageRole
from aiengine.core.db import tenant_session
from aiengine.core.errors import NotFoundError
from aiengine.core.events import publish_event
from aiengine.core.tenant import TenantContext, get_current_tenant
from aiengine.research.aggregator import search_all
from aiengine.research.ingestor import ingest as ingest_paper_to_documents

log = structlog.get_logger(__name__)
router = APIRouter()


class ChatRequest(BaseModel):
    session_id: str | None = Field(default=None)
    message: str = Field(..., min_length=1, max_length=200_000)
    model: str | None = Field(default=None)
    allowed_tools: list[str] | None = Field(default=None)
    mode: Literal["normal", "deep_search"] = "normal"


# ── Deep Search query parsing ──────────────────────────────────────────
# The user types a free-form question; we extract optional count + year
# hints with two narrow regexes and leave the rest as the query. We do
# NOT strip the hint words from the query — the academic APIs ignore
# stop-words like "makale" anyway, and keeping them in helps recall when
# the user does want to search on those terms.

_COUNT_RE = re.compile(
    r"\b(\d{1,3})\s*(?:tane|adet|makale|paper|sonu[cç]|result)s?\b",
    re.IGNORECASE,
)
_YEAR_FROM_RE = re.compile(
    r"(?:son|last|recent)\s+(\d+)\s*(?:yıl|year)s?", re.IGNORECASE
)
_ABS_YEAR_RE = re.compile(r"\b(\d{4})\s*(?:sonras[ıi]|onwards?|itibaren|den\s+sonra)")


def _parse_deep_search_query(text: str) -> tuple[str, int, int | None]:
    """Return (query, max_results, year_from)."""
    max_results = 10
    m = _COUNT_RE.search(text)
    if m:
        max_results = max(1, min(int(m.group(1)), 30))

    year_from: int | None = None
    rel = _YEAR_FROM_RE.search(text)
    if rel:
        year_from = datetime.now(timezone.utc).year - int(rel.group(1))
    else:
        abs_match = _ABS_YEAR_RE.search(text)
        if abs_match:
            year_from = int(abs_match.group(1))

    return text.strip(), max_results, year_from


async def _background_ingest(
    tenant: TenantContext, session_id_str: str, paper_dict: dict
) -> None:
    """Best-effort ingestion. Errors are logged but never propagate —
    a single broken PDF must not bring down the whole batch."""
    from aiengine.research.models import Paper

    try:
        paper = Paper(**paper_dict)
        await ingest_paper_to_documents(
            tenant, paper, source_session_id=session_id_str
        )
    except Exception as exc:  # noqa: BLE001
        log.warning(
            "research.background_ingest.failed",
            doi=paper_dict.get("doi"),
            error=str(exc),
        )


async def _deep_search_stream(
    tenant: TenantContext, req: ChatRequest
) -> AsyncIterator[tuple[str, dict]]:
    """SSE event generator for the LLM-bypass Deep Search path.

    Yields (event_kind, payload) tuples so the outer wrapper can both
    emit SSE and tee to the Redis pub/sub channel uniformly.
    """
    query, max_results, year_from = _parse_deep_search_query(req.message)

    async with tenant_session(tenant) as db:
        if req.session_id:
            try:
                session_uuid = UUID(req.session_id)
            except ValueError as exc:
                raise NotFoundError(f"Invalid session id: {req.session_id}") from exc
            session = await get_session(db, session_uuid)
            if session is None:
                raise NotFoundError(f"Session {req.session_id} not found")
        else:
            session = await create_session(
                db,
                tenant,
                title=req.message[:80],
                model="deep_search",
                mode="deep_search",
            )
            yield "session", {"id": session.id, "created": True}

        session_uuid = UUID(session.id)
        await append_message(
            db,
            tenant,
            session_uuid,
            role=MessageRole.USER,
            blocks=[ContentBlock(type="text", text=req.message)],
        )

        # Synthetic tool_use event so the frontend's existing
        # "Düşünüyor / tool_use" UX renders consistently.
        yield "tool_use", {
            "id": "deep_search_direct",
            "name": "literature_search",
            "input": {
                "query": query,
                "max_results": max_results,
                "year_from": year_from,
            },
        }

        papers = await search_all(query, max_results=max_results, year_from=year_from)
        tool_payload = {
            "ui_kind": "paper_list",
            "query": query,
            "count": len(papers),
            "papers": [p.model_dump() for p in papers],
        }
        yield "tool_result", {
            "id": "deep_search_direct",
            "output": orjson.dumps(tool_payload).decode("utf-8"),
            "is_error": False,
        }

        # Auto-ingest every result in the background. We never await
        # these — the user's response should land instantly while
        # downloads + Tika + chunking + embedding happen out-of-band.
        for paper in papers:
            asyncio.create_task(
                _background_ingest(tenant, session.id, paper.model_dump())
            )

        confirmation = (
            f"{len(papers)} kaynak bulundu. "
            "Hepsi otomatik olarak RAG koleksiyonuna ekleniyor — "
            "ilerlemeyi Dokümanlar sayfasından izleyebilirsiniz."
            if papers
            else "Aramada sonuç bulunamadı."
        )
        yield "token", {"text": confirmation}

        await append_message(
            db,
            tenant,
            session_uuid,
            role=MessageRole.ASSISTANT,
            blocks=[ContentBlock(type="text", text=confirmation)],
        )

        yield "done", {
            "iterations": 1,
            "stop_reason": "end_turn",
            "session_id": session.id,
        }


@router.post("/chat")
async def chat(req: Annotated[ChatRequest, Body()]) -> EventSourceResponse:
    """Stream agent loop output (or deep-search aggregator) as SSE."""
    tenant = get_current_tenant()
    tenant_id = str(tenant.tenant_id)
    current_session_id: str | None = req.session_id

    pre_session_buffer: list[tuple[str, dict]] = []

    async def event_stream() -> AsyncIterator[dict[str, str]]:
        nonlocal current_session_id

        if req.mode == "deep_search":
            async for kind, data in _deep_search_stream(tenant, req):
                if kind == "session":
                    current_session_id = data.get("id") or current_session_id
                if current_session_id:
                    await publish_event(
                        tenant_id=tenant_id,
                        session_id=current_session_id,
                        event_kind=kind,
                        payload=data,
                    )
                yield {"event": kind, "data": orjson.dumps(data).decode("utf-8")}
            return

        # ── Normal mode: existing LLM-driven agent loop ────────────────
        async for event in run_turn(
            tenant=tenant,
            session_id=req.session_id,
            user_message=req.message,
            model_override=req.model,
            allowed_tools=req.allowed_tools,
            mode=req.mode,
        ):
            if event.kind == "session":
                current_session_id = event.data.get("id") or current_session_id
                if current_session_id and pre_session_buffer:
                    for k, d in pre_session_buffer:
                        await publish_event(
                            tenant_id=tenant_id,
                            session_id=current_session_id,
                            event_kind=k,
                            payload=d,
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
                pre_session_buffer.append((event.kind, event.data))

            yield {
                "event": event.kind,
                "data": orjson.dumps(event.data).decode("utf-8"),
            }

    return EventSourceResponse(event_stream())
