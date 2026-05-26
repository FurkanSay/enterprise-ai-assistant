"""Health endpoints — split liveness vs readiness.

  /health/live    → process is up (Kubernetes liveness probe)
  /health/ready   → dependencies reachable (Kubernetes readiness probe)
"""

from fastapi import APIRouter, status
from pydantic import BaseModel

router = APIRouter()


class HealthResponse(BaseModel):
    status: str
    service: str = "aiengine"
    checks: dict[str, str] = {}


@router.get("/health/live", status_code=status.HTTP_200_OK)
async def liveness() -> HealthResponse:
    """Trivial check — process responds. Always 200 unless process is dead."""
    return HealthResponse(status="ok")


@router.get("/health/ready", status_code=status.HTTP_200_OK)
async def readiness() -> HealthResponse:
    """Dependencies reachable. Returns 503 if any critical dependency is down."""
    # TODO: ping Postgres, Redis, Qdrant, Processing gRPC
    return HealthResponse(
        status="ok",
        checks={
            "postgres": "TODO",
            "redis": "TODO",
            "qdrant": "TODO",
            "processing_grpc": "TODO",
        },
    )
