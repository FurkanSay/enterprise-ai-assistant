"""Redis pub/sub publisher for streaming agent events.

The AI Engine's /v1/chat endpoint already returns SSE. This module adds a
parallel "tee" — every event yielded by the agent loop is also published
on a Redis channel, so the Realtime service can fan it out to any
WebSocket clients subscribed to that session.

Channel layout:
    stream.<tenant_id>.<session_id>

Why both SSE and Redis pub/sub?
    - SSE keeps a direct HTTP path for tools that prefer it (curl smoke,
      Postman, the Phase C smoke test). No dependency on Redis.
    - Redis lets multiple WebSocket clients receive the same stream
      without each opening a separate HTTP/SSE connection to AI Engine.
      Realtime is the one process that holds many WS sockets per session.

Failure mode is "drop": if Redis is down, the SSE path is unaffected.
We log a warning, never raise — the user-facing chat must not break
because of a fanout-only path.
"""

from __future__ import annotations

import orjson
import redis.asyncio as redis_async
import structlog

from aiengine.core.config import get_settings

log = structlog.get_logger(__name__)

_client: redis_async.Redis | None = None


async def _get_client() -> redis_async.Redis:
    global _client
    if _client is None:
        settings = get_settings()
        _client = redis_async.from_url(
            settings.redis_url,
            decode_responses=True,
        )
    return _client


async def publish_event(
    tenant_id: str,
    session_id: str,
    event_kind: str,
    payload: dict,
) -> None:
    """Best-effort publish. Never raises — fanout is non-critical."""
    try:
        client = await _get_client()
        channel = f"stream.{tenant_id}.{session_id}"
        message = orjson.dumps({"event": event_kind, "data": payload}).decode("utf-8")
        await client.publish(channel, message)
    except Exception as exc:  # noqa: BLE001 — log + drop, do not break SSE
        log.warning(
            "events.publish.failed",
            channel=f"stream.{tenant_id}.{session_id}",
            kind=event_kind,
            error=str(exc),
        )


async def close_client() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None
