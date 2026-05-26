"""Qdrant client — tenant-scoped vector operations.

Critical: every search/upsert MUST include tenant_id payload filter.
The PermissionEnforcer cannot prevent cross-tenant leaks here — Qdrant
does not have RLS. App-level filter is the only line of defense.
"""

from dataclasses import dataclass
from functools import lru_cache
from typing import Any

import structlog
from qdrant_client import AsyncQdrantClient
from qdrant_client.models import (
    Distance,
    FieldCondition,
    Filter,
    MatchValue,
    PointStruct,
    VectorParams,
)

from aiengine.core.config import get_settings
from aiengine.core.tenant import TenantContext

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class VectorHit:
    chunk_id: str
    document_id: str
    score: float
    text: str
    sequence: int
    source_location: str


@lru_cache(maxsize=1)
def get_qdrant() -> AsyncQdrantClient:
    settings = get_settings()
    return AsyncQdrantClient(url=settings.qdrant_url)


async def ensure_collection() -> None:
    """Create the chunks collection if it doesn't exist (idempotent)."""
    settings = get_settings()
    client = get_qdrant()
    collections = await client.get_collections()
    if any(c.name == settings.qdrant_collection for c in collections.collections):
        return
    await client.create_collection(
        collection_name=settings.qdrant_collection,
        vectors_config=VectorParams(
            size=settings.qdrant_vector_size,
            distance=Distance.COSINE,
        ),
    )
    log.info("qdrant.collection.created", name=settings.qdrant_collection)


async def upsert_chunks(
    tenant: TenantContext,
    document_id: str,
    chunks: list[dict[str, Any]],
    embeddings: list[list[float]],
) -> int:
    """Upsert chunks to Qdrant with tenant_id payload."""
    settings = get_settings()
    points = [
        PointStruct(
            id=chunk["chunk_id"],
            vector=embedding,
            payload={
                "tenant_id": tenant.tenant_id,
                "document_id": document_id,
                "chunk_id": chunk["chunk_id"],
                "text": chunk["text"],
                "sequence": chunk["sequence"],
                "source_location": chunk.get("source_location", ""),
            },
        )
        for chunk, embedding in zip(chunks, embeddings, strict=True)
    ]
    client = get_qdrant()
    await client.upsert(collection_name=settings.qdrant_collection, points=points)
    return len(points)


async def search(
    tenant: TenantContext,
    query_vector: list[float],
    top_k: int = 20,
    document_ids: list[str] | None = None,
) -> list[VectorHit]:
    """Tenant-scoped vector search."""
    settings = get_settings()
    must_conditions: list[FieldCondition] = [
        FieldCondition(key="tenant_id", match=MatchValue(value=tenant.tenant_id))
    ]
    if document_ids:
        must_conditions.append(
            FieldCondition(key="document_id", match=MatchValue(value=document_ids))  # type: ignore[arg-type]
        )

    client = get_qdrant()
    result = await client.search(
        collection_name=settings.qdrant_collection,
        query_vector=query_vector,
        query_filter=Filter(must=must_conditions),
        limit=top_k,
        with_payload=True,
    )

    return [
        VectorHit(
            chunk_id=str(point.payload["chunk_id"]),  # type: ignore[index]
            document_id=str(point.payload["document_id"]),  # type: ignore[index]
            score=point.score,
            text=str(point.payload["text"]),  # type: ignore[index]
            sequence=int(point.payload["sequence"]),  # type: ignore[index]
            source_location=str(point.payload.get("source_location", "")),  # type: ignore[union-attr]
        )
        for point in result
    ]
