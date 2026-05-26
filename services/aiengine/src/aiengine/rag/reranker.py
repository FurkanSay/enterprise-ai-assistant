"""Cross-encoder reranker — refines top-K from hybrid retrieval.

Trade-off: ~100ms latency for ~20-30% recall improvement.
Skippable for latency-sensitive workloads via config.
"""

from dataclasses import dataclass
from functools import lru_cache

import structlog

from aiengine.core.config import get_settings

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class RankedChunk:
    chunk_id: str
    document_id: str
    text: str
    score: float
    sequence: int
    source_location: str


@lru_cache(maxsize=1)
def _get_reranker():  # type: ignore[no-untyped-def]
    """Lazy-load reranker on first call."""
    from sentence_transformers import CrossEncoder

    settings = get_settings()
    log.info("reranker.loading", model=settings.default_reranker_model)
    return CrossEncoder(settings.default_reranker_model)


def rerank(
    query: str,
    candidates: list[RankedChunk],
    top_k: int = 3,
) -> list[RankedChunk]:
    """Score candidates by relevance to query, return top-K."""
    if not candidates:
        return []

    model = _get_reranker()
    pairs = [(query, c.text) for c in candidates]
    scores = model.predict(pairs, show_progress_bar=False)

    scored = sorted(
        ((float(score), c) for score, c in zip(scores, candidates, strict=True)),
        key=lambda item: item[0],
        reverse=True,
    )
    return [
        RankedChunk(
            chunk_id=c.chunk_id,
            document_id=c.document_id,
            text=c.text,
            score=s,
            sequence=c.sequence,
            source_location=c.source_location,
        )
        for s, c in scored[:top_k]
    ]
