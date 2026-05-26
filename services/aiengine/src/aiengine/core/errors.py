"""Exception hierarchy.

All custom exceptions inherit from `AiEngineError`. HTTP layer maps them
to status codes via a single handler in `api/middleware.py`.
"""


class AiEngineError(Exception):
    """Base for all domain exceptions."""

    code: str = "aiengine.internal_error"
    http_status: int = 500

    def __init__(self, message: str, *, details: dict[str, object] | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details or {}


# ─── Auth / permission ──────────────────────────────────────────────────


class UnauthorizedError(AiEngineError):
    code = "aiengine.unauthorized"
    http_status = 401


class ForbiddenError(AiEngineError):
    code = "aiengine.forbidden"
    http_status = 403


class TenantContextMissingError(AiEngineError):
    code = "aiengine.tenant_context_missing"
    http_status = 401


# ─── Validation ─────────────────────────────────────────────────────────


class ValidationError(AiEngineError):
    code = "aiengine.validation_failed"
    http_status = 422


# ─── Resource ───────────────────────────────────────────────────────────


class NotFoundError(AiEngineError):
    code = "aiengine.not_found"
    http_status = 404


# ─── Quota / rate limit ─────────────────────────────────────────────────


class QuotaExceededError(AiEngineError):
    code = "aiengine.quota_exceeded"
    http_status = 429


class IterationLimitExceededError(AiEngineError):
    code = "aiengine.iteration_limit_exceeded"
    http_status = 429


# ─── Tool / agent ───────────────────────────────────────────────────────


class ToolNotFoundError(AiEngineError):
    code = "aiengine.tool_not_found"
    http_status = 404


class ToolExecutionError(AiEngineError):
    code = "aiengine.tool_execution_failed"
    http_status = 500


class PermissionDeniedError(AiEngineError):
    """Permission enforcer denied a tool call."""

    code = "aiengine.permission_denied"
    http_status = 403


# ─── Upstream ───────────────────────────────────────────────────────────


class UpstreamError(AiEngineError):
    """LLM provider, vector DB, or another service failed."""

    code = "aiengine.upstream_error"
    http_status = 502
