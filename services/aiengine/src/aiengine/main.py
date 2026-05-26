"""FastAPI application entry point."""

from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI

from aiengine.api.middleware import TenantContextMiddleware, RequestIdMiddleware
from aiengine.api.routes import chat, documents, health, sessions
from aiengine.core.config import get_settings
from aiengine.core.logging import configure_logging, get_logger
from aiengine.core.telemetry import configure_telemetry, shutdown_telemetry

log = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Application lifespan — startup + shutdown hooks."""
    settings = get_settings()
    configure_logging(level=settings.log_level)
    configure_telemetry(app=app, settings=settings)

    log.info(
        "aiengine.startup",
        version=settings.service_version,
        environment=settings.environment,
        llm_default_model=settings.default_llm_model,
    )

    yield

    log.info("aiengine.shutdown")
    shutdown_telemetry()


def create_app() -> FastAPI:
    """Application factory — enables clean testing via TestClient."""
    settings = get_settings()

    app = FastAPI(
        title="AI Engine",
        version=settings.service_version,
        description="Agent loop + tool registry + RAG pipeline (multi-tenant)",
        lifespan=lifespan,
        docs_url="/docs" if settings.environment != "production" else None,
        redoc_url=None,
    )

    # Middleware (order matters — outer first)
    app.add_middleware(TenantContextMiddleware)
    app.add_middleware(RequestIdMiddleware)

    # Routes
    app.include_router(health.router, tags=["health"])
    app.include_router(chat.router, prefix="/v1", tags=["chat"])
    app.include_router(sessions.router, prefix="/v1", tags=["sessions"])
    app.include_router(documents.router, prefix="/v1/internal", tags=["internal"])

    return app


app = create_app()
