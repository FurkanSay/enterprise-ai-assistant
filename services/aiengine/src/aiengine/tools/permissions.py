"""Permission enforcement layer.

Multi-tenant adaptation of Claude Code's PermissionEnforcer.
See: docs/claw-learnings/02-permission-enforcer.md
"""

from dataclasses import dataclass
from typing import Any

from aiengine.core.errors import PermissionDeniedError
from aiengine.core.tenant import TenantContext
from aiengine.tools.base import PermissionMode


@dataclass(frozen=True, slots=True)
class TenantPolicy:
    """Resolved policy for a tenant — loaded from DB or env config."""

    active_mode: PermissionMode
    tool_allow_list: frozenset[str] | None = None  # None = all allowed by mode
    tool_deny_list: frozenset[str] = frozenset()


def resolve_tenant_policy(tenant: TenantContext) -> TenantPolicy:
    """Look up tenant's permission policy.

    TODO: query DB; for now hardcoded default.
    """
    return TenantPolicy(
        active_mode=PermissionMode.WORKSPACE_WRITE,
        tool_allow_list=None,
        tool_deny_list=frozenset(),
    )


def check_permission(
    tenant: TenantContext,
    tool_name: str,
    required_mode: PermissionMode,
    tool_input: dict[str, Any],
) -> None:
    """Raise PermissionDeniedError if the tenant cannot run this tool.

    Decision chain:
      1. Tool in deny list?       → deny
      2. Allow list set + tool not in it? → deny
      3. active_mode >= required_mode?   → allow else deny
    """
    policy = resolve_tenant_policy(tenant)

    if tool_name in policy.tool_deny_list:
        raise PermissionDeniedError(
            f"Tool '{tool_name}' is in tenant deny list.",
            details={"tool": tool_name, "tenant_id": tenant.tenant_id},
        )

    if policy.tool_allow_list is not None and tool_name not in policy.tool_allow_list:
        raise PermissionDeniedError(
            f"Tool '{tool_name}' is not in tenant allow list.",
            details={"tool": tool_name, "tenant_id": tenant.tenant_id},
        )

    if policy.active_mode < required_mode:
        raise PermissionDeniedError(
            f"Tool '{tool_name}' requires {required_mode.name}, "
            f"tenant has {policy.active_mode.name}",
            details={
                "tool": tool_name,
                "required_mode": required_mode.name,
                "active_mode": policy.active_mode.name,
            },
        )


# ─── Dynamic classifiers — input-aware permission resolution ─────────────


def classify_sql_permission(tool_input: dict[str, Any]) -> PermissionMode:
    """Inspect SQL to determine required permission.

    SELECT-only → READ_ONLY. Anything else → ADMIN_ACTION.
    Production should use sqlglot AST parsing instead of regex.
    """
    sql = str(tool_input.get("sql", "")).strip().lower()
    # TODO: replace with sqlglot AST parse
    if sql.startswith("select") and not any(
        kw in sql for kw in ("insert", "update", "delete", "drop", "alter", "truncate")
    ):
        return PermissionMode.READ_ONLY
    return PermissionMode.ADMIN_ACTION
