"""Async SQLAlchemy engine + session factory + per-request tenant scoping.

Every request that touches Postgres MUST go through `tenant_session(...)`
so the `SET LOCAL app.current_tenant_id = X` statement runs inside the
same transaction Row-Level Security uses to decide what rows are visible.
"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from functools import lru_cache

import structlog
from sqlalchemy import event, text
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine

from aiengine.core.config import get_settings
from aiengine.core.tenant import TenantContext

log = structlog.get_logger(__name__)


@lru_cache(maxsize=1)
def get_engine() -> AsyncEngine:
    """Process-wide async engine. One pool, reused across requests."""
    settings = get_settings()
    log.info(
        "db.engine.create",
        url=settings.database_url.split("@")[-1],  # don't log credentials
        schema=settings.database_schema,
        pool_size=settings.database_pool_size,
    )
    engine = create_async_engine(
        settings.database_url,
        pool_size=settings.database_pool_size,
        max_overflow=settings.database_pool_overflow,
        pool_pre_ping=True,
        connect_args={
            # Set search_path so unqualified table names resolve to our schema
            "server_settings": {"search_path": settings.database_schema},
        },
    )
    return engine


@lru_cache(maxsize=1)
def get_sessionmaker() -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(
        bind=get_engine(),
        expire_on_commit=False,
        autoflush=False,
    )


@asynccontextmanager
async def tenant_session(tenant: TenantContext) -> AsyncIterator[AsyncSession]:
    """Yield a DB session scoped to the caller's tenant.

    Sets `app.current_tenant_id` as a session-local Postgres GUC; every
    business table's RLS policy reads this value. The setting is reset
    when the session closes (Postgres SET LOCAL semantics).
    """
    Session = get_sessionmaker()
    async with Session() as session:
        await session.execute(
            text("SELECT set_config('app.current_tenant_id', :tid, true)"),
            {"tid": tenant.tenant_id},
        )
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def dispose_engine() -> None:
    """Close pool — called on shutdown."""
    engine = get_engine()
    await engine.dispose()
