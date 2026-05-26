"""RAG retrieval orchestrator — hybrid search + RRF + rerank.

Algorithm (industry standard for production RAG):
  1. Dense search (Qdrant)             → top-20 by cosine
  2. Sparse search (Processing BM25)   → top-20 by BM25
  3. Reciprocal Rank Fusion            → merge into top-K (K=10 here)
  4. Cross-encoder rerank              → final top-K (K=3 default)
"""

from typing import TYPE_CHECKING

import structlog
from pydantic import BaseModel

from aiengine.core.tenant import TenantContext
from aiengine.rag import bm25_client, embeddings, qdrant_store, reranker

if TYPE_CHECKING:
    from aiengine.rag.bm25_client import BM25Hit
    from aiengine.rag.qdrant_store import VectorHit

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
    *,
    rerank_top_k: int = 3,
    dense_k: int = 20,
    sparse_k: int = 20,
    rrf_k: int = 60,
) -> list[RetrievalHit]:
    """End-to-end hybrid retrieval pipeline."""
    # Step 1+2: dense + sparse in parallel — both tenant-scoped
    query_vec = embeddings.embed_text(query)
    dense_hits = await qdrant_store.search(
        tenant=tenant,
        query_vector=query_vec,
        top_k=dense_k,
        document_ids=document_ids,
    )
    sparse_hits = await bm25_client.bm25_search(
        tenant=tenant,
        query=query,
        top_k=sparse_k,
        document_ids=document_ids,
    )

    # Step 3: Reciprocal Rank Fusion
    fused = _reciprocal_rank_fusion(dense_hits, sparse_hits, k=rrf_k, top_k=top_k * 2)

    # Step 4: Rerank
    candidates = [
        reranker.RankedChunk(
            chunk_id=h["chunk_id"],
            document_id=h["document_id"],
            text=h["text"],
            score=h["fused_score"],
            sequence=h["sequence"],
            source_location=h["source_location"],
        )
        for h in fused
    ]
    reranked = reranker.rerank(query=query, candidates=candidates, top_k=rerank_top_k)

    log.info(
        "rag.search.done",
        query_len=len(query),
        dense_count=len(dense_hits),
        sparse_count=len(sparse_hits),
        fused_count=len(fused),
        final_count=len(reranked),
    )

    return [
        RetrievalHit(
            chunk_id=r.chunk_id,
            document_id=r.document_id,
            text=r.text,
            score=r.score,
            source_location=r.source_location,
        )
        for r in reranked
    ]


def _reciprocal_rank_fusion(
    dense: list["VectorHit"],
    sparse: list["BM25Hit"],
    k: int = 60,
    top_k: int = 10,
) -> list[dict]:
    """RRF — combine two ranked lists by 1/(k+rank) weighting.

    See: Cormack et al. 2009 "Reciprocal Rank Fusion outperforms Condorcet
    and individual Rank Learning Methods".
    """
    scores: dict[str, dict] = {}
    for rank, hit in enumerate(dense, start=1):
        scores[hit.chunk_id] = {
            "chunk_id": hit.chunk_id,
            "document_id": hit.document_id,
            "text": hit.text,
            "sequence": hit.sequence,
            "source_location": hit.source_location,
            "fused_score": 1.0 / (k + rank),
        }
    for rank, hit in enumerate(sparse, start=1):
        entry = scores.setdefault(
            hit.chunk_id,
            {
                "chunk_id": hit.chunk_id,
                "document_id": hit.document_id,
                "text": hit.text,
                "sequence": hit.sequence,
                "source_location": hit.source_location,
                "fused_score": 0.0,
            },
        )
        entry["fused_score"] += 1.0 / (k + rank)

    return sorted(scores.values(), key=lambda x: x["fused_score"], reverse=True)[:top_k]
