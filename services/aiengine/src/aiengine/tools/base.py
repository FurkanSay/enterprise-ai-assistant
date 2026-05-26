"""Tool primitives — ToolSpec, ToolHandler protocol, PermissionMode.

Mirrors Claude Code's `ToolSpec` struct (Rust) in Python.
See: docs/claw-learnings/03-tool-registry.md
"""

from collections.abc import Awaitable, Callable
from enum import IntEnum
from typing import Any, Protocol

from pydantic import BaseModel, ConfigDict

from aiengine.core.tenant import TenantContext


class PermissionMode(IntEnum):
    """Hierarchical permission. Higher mode = more dangerous.

    `active_mode >= required_mode` → allowed.
    """

    READ_ONLY = 1
    WORKSPACE_WRITE = 2
    ADMIN_ACTION = 3   # equivalent of Claw's DangerFullAccess
    PROMPT = 99        # special: defer to human approval queue


class ToolSpec(BaseModel):
    """LLM-facing tool contract."""

    model_config = ConfigDict(frozen=True, arbitrary_types_allowed=True)

    name: str
    description: str
    input_schema: dict[str, Any]
    required_permission: PermissionMode
    category: str  # filesystem | retrieval | web | action | meta | admin
    base_or_deferred: str = "deferred"  # "base" = always loaded, "deferred" = ToolSearch

    def to_anthropic_schema(self) -> dict[str, Any]:
        """Anthropic tools API expects {name, description, input_schema}."""
        return {
            "name": self.name,
            "description": self.description,
            "input_schema": self.input_schema,
        }


class ToolHandler(Protocol):
    """Callable that executes a tool. Returns JSON-stringified result."""

    async def __call__(
        self, *, tenant: TenantContext, tool_input: dict[str, Any]
    ) -> str:
        ...


# Optional dynamic classifier — given input, return required permission mode.
# Used for tools where the danger level depends on input (e.g. SQL: SELECT vs DELETE).
PermissionClassifier = Callable[[dict[str, Any]], PermissionMode]
