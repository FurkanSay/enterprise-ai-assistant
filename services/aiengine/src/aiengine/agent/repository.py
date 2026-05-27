"""Session + Message repository — boundary between the agent loop and Postgres.

The loop deals in Pydantic domain types (Session, Message, ContentBlock);
the DB speaks SQLAlchemy ORM. This module is the only place those two
worlds are converted, so the agent loop never has to know SQLAlchemy.
"""

from datetime import UTC, datetime
from uuid import UUID, uuid4

import structlog
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from aiengine.agent.models import MessageRow, SessionRow
from aiengine.agent.state import ContentBlock, Message, MessageRole, Session, TokenUsage
from aiengine.core.tenant import TenantContext

log = structlog.get_logger(__name__)


# ─── Session ────────────────────────────────────────────────────────────


async def create_session(
    db: AsyncSession,
    tenant: TenantContext,
    *,
    title: str = "",
    model: str = "",
    mode: str = "normal",
) -> Session:
    """Insert a new session for the calling tenant + user."""
    now = datetime.now(UTC)
    row = SessionRow(
        id=uuid4(),
        tenant_id=UUID(tenant.tenant_id),
        user_id=UUID(tenant.user_id),
        title=title,
        model=model,
        mode=mode,
        message_count=0,
        created_at=now,
        updated_at=now,
    )
    db.add(row)
    await db.flush()
    log.info("session.created", session_id=str(row.id))
    return _session_from_row(row)


async def get_session(db: AsyncSession, session_id: UUID) -> Session | None:
    """Load a session by id. RLS guarantees tenant scoping."""
    row = await db.get(SessionRow, session_id)
    return _session_from_row(row) if row else None


async def list_sessions(
    db: AsyncSession,
    *,
    limit: int = 20,
) -> list[Session]:
    """Newest-first list of sessions for the current tenant.
    Tenant scoping is enforced by Postgres RLS — no WHERE clause needed.
    """
    stmt = (
        select(SessionRow)
        .where(SessionRow.archived_at.is_(None))
        .order_by(SessionRow.updated_at.desc())
        .limit(limit)
    )
    result = await db.execute(stmt)
    return [_session_from_row(r) for r in result.scalars().all()]


async def fork_session(
    db: AsyncSession,
    tenant: TenantContext,
    *,
    parent_session_id: UUID,
    up_to_message_id: UUID,
) -> Session | None:
    """Create a new session that branches off `parent_session_id` at the
    given message. Messages up to and INCLUDING `up_to_message_id` (by
    sequence_number) are copied verbatim into the new session. Future
    /chat turns on the new session see exactly the same history as the
    parent had at the fork point but accrue independently.

    Returns None if the parent is invisible under RLS or the fork-point
    message does not belong to the parent.
    """
    parent_row = await db.get(SessionRow, parent_session_id)
    if parent_row is None:
        return None

    # Find the fork-point message and capture its sequence_number.
    fork_msg = await db.get(MessageRow, up_to_message_id)
    if fork_msg is None or fork_msg.session_id != parent_session_id:
        return None

    now = datetime.now(UTC)
    new_session_id = uuid4()
    child = SessionRow(
        id=new_session_id,
        tenant_id=UUID(tenant.tenant_id),
        user_id=UUID(tenant.user_id),
        title=parent_row.title or "Fork",
        model=parent_row.model,
        message_count=0,
        parent_session_id=parent_session_id,
        forked_from_message_id=up_to_message_id,
        created_at=now,
        updated_at=now,
    )
    db.add(child)
    await db.flush()

    # Copy the message tail. We refetch to walk in sequence order; the
    # parent's relationship cascade isn't used because we need the rows
    # but with new ids.
    stmt = (
        select(MessageRow)
        .where(MessageRow.session_id == parent_session_id)
        .where(MessageRow.sequence_number <= fork_msg.sequence_number)
        .order_by(MessageRow.sequence_number)
    )
    parent_msgs = (await db.execute(stmt)).scalars().all()
    copied = 0
    for src in parent_msgs:
        db.add(
            MessageRow(
                id=uuid4(),
                session_id=new_session_id,
                tenant_id=src.tenant_id,
                role=src.role,
                blocks=src.blocks,
                token_usage=src.token_usage,
                sequence_number=src.sequence_number,
                created_at=src.created_at,
            )
        )
        copied += 1
    child.message_count = copied
    await db.flush()
    log.info(
        "session.forked",
        parent_session_id=str(parent_session_id),
        new_session_id=str(new_session_id),
        copied=copied,
        up_to_message_id=str(up_to_message_id),
    )
    return _session_from_row(child)


async def delete_session(db: AsyncSession, session_id: UUID) -> bool:
    """Soft-delete a session by setting archived_at. Returns whether
    the session existed (and was archived) under the current tenant."""
    row = await db.get(SessionRow, session_id)
    if row is None:
        return False
    row.archived_at = datetime.now(UTC)
    await db.flush()
    log.info("session.deleted", session_id=str(session_id))
    return True


# ─── Messages ───────────────────────────────────────────────────────────


async def list_messages(db: AsyncSession, session_id: UUID) -> list[Message]:
    """Load all messages for a session, ordered by sequence_number."""
    stmt = (
        select(MessageRow)
        .where(MessageRow.session_id == session_id)
        .order_by(MessageRow.sequence_number)
    )
    rows = (await db.execute(stmt)).scalars().all()
    return [_message_from_row(r) for r in rows]


async def append_message(
    db: AsyncSession,
    tenant: TenantContext,
    session_id: UUID,
    *,
    role: MessageRole,
    blocks: list[ContentBlock],
    usage: TokenUsage | None = None,
) -> Message:
    """Insert a message at the next sequence_number.

    Locks the session row briefly to compute the next sequence atomically.
    """
    sess = await db.get(SessionRow, session_id, with_for_update=True)
    if sess is None:
        raise ValueError(f"session {session_id} not found")

    next_seq = sess.message_count + 1
    row = MessageRow(
        id=uuid4(),
        session_id=session_id,
        tenant_id=UUID(tenant.tenant_id),
        role=role.value if hasattr(role, "value") else str(role),
        blocks=[_block_to_dict(b) for b in blocks],
        token_usage=usage.model_dump() if usage else None,
        sequence_number=next_seq,
        created_at=datetime.now(UTC),
    )
    db.add(row)

    sess.message_count = next_seq
    sess.updated_at = datetime.now(UTC)

    await db.flush()
    return _message_from_row(row)


# ─── Converters ─────────────────────────────────────────────────────────


def _session_from_row(row: SessionRow) -> Session:
    return Session(
        id=str(row.id),
        tenant_id=row.tenant_id,
        user_id=row.user_id,
        title=row.title,
        model=row.model,
        message_count=row.message_count,
        compaction_count=row.compaction_count,
        compaction_summary=row.compaction_summary,
        created_at=row.created_at,
        updated_at=row.updated_at,
        archived_at=row.archived_at,
        parent_session_id=str(row.parent_session_id) if row.parent_session_id else None,
        forked_from_message_id=str(row.forked_from_message_id) if row.forked_from_message_id else None,
        mode=row.mode,
    )


def _message_from_row(row: MessageRow) -> Message:
    blocks = [_block_from_dict(b) for b in row.blocks]
    usage = TokenUsage(**row.token_usage) if row.token_usage else None
    return Message(
        id=str(row.id),
        session_id=str(row.session_id),
        tenant_id=row.tenant_id,
        role=MessageRole(row.role),
        blocks=blocks,
        usage=usage,
        sequence_number=row.sequence_number,
        created_at=row.created_at,
    )


def _block_to_dict(b: ContentBlock) -> dict:
    return b.model_dump(exclude_none=True)


def _block_from_dict(d: dict) -> ContentBlock:
    return ContentBlock(**d)
