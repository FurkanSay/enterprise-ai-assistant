"""Query-time embedding via Processing service HTTP.

Why not sentence-transformers in-process: Processing is already running
the fastembed BGE-Base ONNX model and wrote every chunk vector with it.
Calling out to Processing keeps query and ingest on the exact same model
— if we swap to bge-m3 tomorrow, one config change moves both paths
together. The alternative (local sentence-transformers) sat at 384d
MiniLM and silently returned zero results against a 768d Qdrant index.

Cost: one extra HTTP hop per chat turn (~50ms on a warm model). The
correctness gain is worth more than the latency.
"""

import httpx
import structlog

from aiengine.core.config import get_settings

log = structlog.get_logger(__name__)

_TIMEOUT = httpx.Timeout(10.0, connect=2.0)


async def embed_text(text: str) -> list[float]:
    """Embed a single query string. Async — sits on the chat hot path."""
    if not text.strip():
        raise ValueError("embed_text: empty input")
    settings = get_settings()
    url = f"{settings.processing_http_url.rstrip('/')}/embed"
    async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
        resp = await client.post(url, json={"text": text})
        resp.raise_for_status()
        body = resp.json()
    vector = body.get("vector")
    if not isinstance(vector, list) or not vector:
        raise RuntimeError(f"/embed returned no vector: {body}")
    return vector


async def embed_batch(texts: list[str]) -> list[list[float]]:
    """Batch shim — Processing's /embed is single-text today, so we
    sequence the calls. Trivial to upgrade to a real batch endpoint
    once we have a use case (currently only the query path needs this)."""
    return [await embed_text(t) for t in texts]
