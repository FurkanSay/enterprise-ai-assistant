"""RAG retrieval orchestrator.

Phase J scope: dense-only retrieval via Qdrant. BM25 + reranker are
implemented but disabled on the hot path because:
  - BM25 (Processing gRPC) needs a query-side endpoint we haven't
    surfaced yet — Phase G+ work
  - Reranker (cross-encoder) requires sentence-transformers, which is
    in the [ml] optional deps; image grows ~600MB to include it

Both can be re-enabled with a config flag once the dependencies are
back in. The current path is good enough for the demo: top-K dense
search with tenant filtering, formatted for the agent loop.
"""

import structlog
from pydantic import BaseModel

from aiengine.core.tenant import TenantContext
from aiengine.rag import embeddings, qdrant_store

log = structlog.get_logger(__name__)


class RetrievalHit(BaseModel):
    chunk_id: str
    document_id: str
    text: str
    score: float
    source_location: str


async def hybrid_search(
    tenant: TenantContext,
    query: str,
    top_k: int = 5,
    document_ids: list[str] | None = None,
) -> list[RetrievalHit]:
    """Dense top-K from Qdrant. Name kept as `hybrid_search` so the
    tool catalog stays stable while we wire BM25 fusion back in."""
    query_vec = await embeddings.embed_text(query)
    dense_hits = await qdrant_store.search(
        tenant=tenant,
        query_vector=query_vec,
        top_k=top_k,
        document_ids=document_ids,
    )
    log.info(
        "rag.search.done",
        query_len=len(query),
        hits=len(dense_hits),
        top_k=top_k,
    )
    return [
        RetrievalHit(
            chunk_id=h.chunk_id,
            document_id=h.document_id,
            text=h.text,
            score=h.score,
            source_location=h.source_location,
        )
        for h in dense_hits
    ]
