"""Structured JSON logging via structlog.

Every log line includes:
  - timestamp (ISO 8601)
  - level
  - logger name
  - tenant_id, trace_id, request_id (if available via contextvars)
  - any custom kwargs
"""

import logging
import sys

import structlog
from structlog.types import Processor


def configure_logging(level: str = "info") -> None:
    """Configure stdlib logging + structlog to emit JSON to stdout."""
    log_level = getattr(logging, level.upper(), logging.INFO)

    timestamper = structlog.processors.TimeStamper(fmt="iso", utc=True)

    shared_processors: list[Processor] = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        timestamper,
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
    ]

    structlog.configure(
        processors=[
            *shared_processors,
            structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )

    # Redirect stdlib logging → structlog
    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(log_level)
    handler.setFormatter(logging.Formatter("%(message)s"))

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(log_level)

    # Tame noisy libraries
    for noisy in ("uvicorn.access", "httpx", "httpcore"):
        logging.getLogger(noisy).setLevel(logging.WARNING)


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    """Get a structlog logger. Use __name__ as `name`."""
    return structlog.stdlib.get_logger(name)  # type: ignore[return-value]
