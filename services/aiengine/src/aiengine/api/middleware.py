"""HTTP middleware — tenant context binding, request id, error mapping."""

import uuid
from collections.abc import Awaitable, Callable

import structlog
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from aiengine.core.errors import AiEngineError, TenantContextMissingError
from aiengine.core.tenant import TenantContext, set_current_tenant

log = structlog.get_logger(__name__)

TENANT_HEADER = "X-Tenant-Id"
USER_HEADER = "X-User-Id"
ROLES_HEADER = "X-User-Roles"
TRACE_HEADER = "X-Trace-Id"
CORRELATION_HEADER = "X-Correlation-Id"
REQUEST_ID_HEADER = "X-Request-Id"


class RequestIdMiddleware(BaseHTTPMiddleware):
    """Assign a unique request id; surface it in response + log context."""

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        request_id = request.headers.get(REQUEST_ID_HEADER) or str(uuid.uuid4())
        structlog.contextvars.bind_contextvars(request_id=request_id)
        try:
            response = await call_next(request)
        except AiEngineError as exc:
            log.warning(
                "request.domain_error",
                code=exc.code,
                message=exc.message,
                status=exc.http_status,
            )
            response = JSONResponse(
                status_code=exc.http_status,
                content={
                    "code": exc.code,
                    "message": exc.message,
                    "details": exc.details,
                    "request_id": request_id,
                },
            )
        finally:
            structlog.contextvars.unbind_contextvars("request_id")

        response.headers[REQUEST_ID_HEADER] = request_id
        return response


class TenantContextMiddleware(BaseHTTPMiddleware):
    """Extract tenant context from Gateway-injected headers and bind to ContextVar.

    Gateway is the single authority — JWT validation happens there.
    Downstream services trust the headers.

    Public paths (e.g. /health) are exempt from tenant requirement.
    """

    # /metrics is Prometheus-scraped from inside the cluster and is not
    # tenant-scoped — exempting it keeps the log clean of every-15s 401s.
    EXEMPT_PREFIXES = ("/health", "/metrics", "/docs", "/openapi.json", "/redoc")

    async def dispatch(
        self, request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        if any(request.url.path.startswith(p) for p in self.EXEMPT_PREFIXES):
            return await call_next(request)

        tenant_id = request.headers.get(TENANT_HEADER)
        user_id = request.headers.get(USER_HEADER)
        if not tenant_id or not user_id:
            raise TenantContextMissingError(
                "Request missing X-Tenant-Id or X-User-Id header. "
                "All authenticated routes require Gateway-injected context."
            )

        roles_header = request.headers.get(ROLES_HEADER, "")
        roles = tuple(r.strip() for r in roles_header.split(",") if r.strip())

        ctx = TenantContext(
            tenant_id=tenant_id,
            user_id=user_id,
            roles=roles,
            trace_id=request.headers.get(TRACE_HEADER, ""),
            correlation_id=request.headers.get(CORRELATION_HEADER, ""),
            caller_service=request.headers.get("X-Caller-Service", "gateway"),
        )
        set_current_tenant(ctx)
        structlog.contextvars.bind_contextvars(
            tenant_id=tenant_id, user_id=user_id, trace_id=ctx.trace_id
        )
        try:
            return await call_next(request)
        finally:
            structlog.contextvars.unbind_contextvars("tenant_id", "user_id", "trace_id")
