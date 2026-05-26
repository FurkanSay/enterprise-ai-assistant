"""gRPC client for Processing service's BM25Search.

Used in hybrid retrieval: dense (Qdrant) + sparse (this BM25) → RRF.
"""

from dataclasses import dataclass

import structlog

from aiengine.core.tenant import TenantContext

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class BM25Hit:
    chunk_id: str
    document_id: str
    score: float
    text: str
    sequence: int
    source_location: str


async def bm25_search(
    tenant: TenantContext,
    query: str,
    top_k: int = 20,
    document_ids: list[str] | None = None,
) -> list[BM25Hit]:
    """Call Processing service's BM25Search via gRPC.

    TODO: implement with grpc.aio + generated stub from libs/generated/python.
    For now returns empty list so hybrid_search falls back to dense-only.
    """
    log.debug("bm25.search", query=query, top_k=top_k)
    return []
