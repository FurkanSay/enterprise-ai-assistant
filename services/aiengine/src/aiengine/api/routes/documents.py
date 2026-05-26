"""Internal endpoints — called by Documents/Processing services, not browsers.

These are reachable only via service-to-service network.
Gateway does not proxy /v1/internal/*.
"""

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class EmbedChunksRequest(BaseModel):
    document_id: str
    chunks: list[dict[str, str | int]]
    model: str | None = None


class EmbedChunksResponse(BaseModel):
    embedded_count: int
    failures: list[dict[str, str]] = []
    took_ms: int


@router.post("/documents/embed-chunks")
async def embed_chunks(req: EmbedChunksRequest) -> EmbedChunksResponse:
    """Triggered by Processing after chunking. Embeds + writes Qdrant."""
    # TODO: actual embedding via rag.embeddings + Qdrant upsert
    return EmbedChunksResponse(embedded_count=0, took_ms=0)
