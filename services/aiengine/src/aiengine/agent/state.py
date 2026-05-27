"""Session + Message + ContentBlock types.

Mirrors Claude Code's session model (see docs/claw-learnings/05-session-persistence.md)
but adapted for multi-tenant SaaS:
  - session_id + tenant_id + user_id
  - messages stored in Postgres (sessions_schema), not JSONL
  - append-only with sequence_number for ordering
"""

from datetime import datetime
from enum import Enum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field
from ulid import ULID


class MessageRole(str, Enum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"
    TOOL = "tool"


class ContentBlock(BaseModel):
    """Discriminated union — exactly one field populated."""

    type: str  # "text" | "thinking" | "tool_use" | "tool_result"

    # text
    text: str | None = None

    # thinking
    thinking: str | None = None
    signature: str | None = None

    # tool_use
    tool_use_id: str | None = None
    tool_name: str | None = None
    tool_input: dict[str, Any] | None = None

    # tool_result
    tool_result_for_id: str | None = None
    tool_output: str | None = None
    is_error: bool = False


class TokenUsage(BaseModel):
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_tokens: int = 0
    cache_write_tokens: int = 0


class Message(BaseModel):
    """One conversation message — stored in Postgres `aiengine_schema.messages`."""

    model_config = ConfigDict(use_enum_values=True)

    id: str = Field(default_factory=lambda: str(ULID()))
    session_id: str
    tenant_id: UUID
    role: MessageRole
    blocks: list[ContentBlock]
    usage: TokenUsage | None = None
    sequence_number: int  # monotonic per session
    created_at: datetime


class Session(BaseModel):
    """Conversation session — stored in Postgres `aiengine_schema.sessions`."""

    id: str = Field(default_factory=lambda: str(ULID()))
    tenant_id: UUID
    user_id: UUID
    title: str = ""
    model: str = ""
    message_count: int = 0
    compaction_count: int = 0
    compaction_summary: str | None = None
    created_at: datetime
    updated_at: datetime
    archived_at: datetime | None = None
    # Fork lineage. Populated on sessions created via /sessions/{id}/fork.
    parent_session_id: str | None = None
    forked_from_message_id: str | None = None
