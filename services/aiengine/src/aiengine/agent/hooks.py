"""Pre/post tool hooks — audit + tenant policy + redaction.

Hooks run before and after every tool execution. They can:
  - Cancel (deny) a tool call with a reason returned to the LLM
  - Modify the input (e.g. inject tenant constraints)
  - Transform the output (e.g. redact PII)
  - Append audit log rows

Tenant-defined hooks (future) are loaded from `aiengine_schema.tenant_hooks`.
"""

from dataclasses import dataclass
from enum import Enum
from typing import Any

from aiengine.core.tenant import TenantContext


class HookDecision(str, Enum):
    ALLOW = "allow"
    DENY = "deny"
    TRANSFORM = "transform"


@dataclass(slots=True)
class HookResult:
    decision: HookDecision
    reason: str = ""
    updated_input: dict[str, Any] | None = None
    updated_output: str | None = None
    audit_metadata: dict[str, Any] | None = None


async def run_pre_tool_use(
    tenant: TenantContext,
    tool_name: str,
    tool_input: dict[str, Any],
) -> HookResult:
    """Pre-execution hook chain. Built-in hooks run first, then tenant hooks."""
    # TODO: audit log write
    # TODO: load + execute tenant-defined hooks
    return HookResult(decision=HookDecision.ALLOW)


async def run_post_tool_use(
    tenant: TenantContext,
    tool_name: str,
    tool_input: dict[str, Any],
    tool_output: str,
    is_error: bool,
) -> HookResult:
    """Post-execution hook chain. Used for output redaction + audit."""
    # TODO: PII redaction
    # TODO: audit log update with output_hash + duration
    return HookResult(decision=HookDecision.ALLOW)
