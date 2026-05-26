"""Embedding generation — sentence-transformers, lazy-loaded model."""

from functools import lru_cache

import structlog

from aiengine.core.config import get_settings

log = structlog.get_logger(__name__)


@lru_cache(maxsize=1)
def _get_model():  # type: ignore[no-untyped-def]
    """Lazy-load model on first call. Avoids loading at import time."""
    from sentence_transformers import SentenceTransformer

    settings = get_settings()
    log.info("embeddings.model.loading", model=settings.default_embedding_model)
    return SentenceTransformer(settings.default_embedding_model)


def embed_text(text: str) -> list[float]:
    """Embed a single text."""
    model = _get_model()
    vec = model.encode(text, convert_to_numpy=True, show_progress_bar=False)
    return vec.tolist()  # type: ignore[no-any-return]


def embed_batch(texts: list[str]) -> list[list[float]]:
    """Embed a batch of texts. ~10-50x faster than calling embed_text in a loop."""
    model = _get_model()
    vecs = model.encode(texts, convert_to_numpy=True, show_progress_bar=False, batch_size=32)
    return vecs.tolist()  # type: ignore[no-any-return]
