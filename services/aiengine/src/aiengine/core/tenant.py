"""TenantContext — every request carries this, propagated to DB + Qdrant + downstream gRPC.

Authority chain:
  Browser → Gateway (validates JWT) → adds X-Tenant-Context header → AI Engine reads it
  AI Engine NEVER derives tenant_id from JWT itself (Gateway is the only authority).
"""

from contextvars import ContextVar
from dataclasses import dataclass

# Context-local — survives across async boundaries in a single request
_current_tenant: ContextVar["TenantContext | None"] = ContextVar(
    "current_tenant", default=None
)


@dataclass(frozen=True, slots=True)
class TenantContext:
    """Authenticated tenant + user context."""

    tenant_id: str
    user_id: str
    roles: tuple[str, ...]
    trace_id: str = ""
    correlation_id: str = ""
    caller_service: str = ""

    def has_role(self, role: str) -> bool:
        """Check if context carries a given role."""
        return role in self.roles


def set_current_tenant(ctx: TenantContext) -> None:
    """Bind tenant context to the current request (via ContextVar)."""
    _current_tenant.set(ctx)


def get_current_tenant() -> TenantContext:
    """Get tenant context for the current request.

    Raises RuntimeError if called outside an authenticated request scope.
    Use this in tool handlers, DB session factories, RAG pipeline — never
    pass tenant_id manually through layers.
    """
    ctx = _current_tenant.get()
    if ctx is None:
        raise RuntimeError(
            "No tenant context set — calling get_current_tenant() outside "
            "an authenticated request scope. Check middleware order."
        )
    return ctx


def maybe_current_tenant() -> TenantContext | None:
    """Non-throwing variant — useful for health checks etc."""
    return _current_tenant.get()
