"""Built-in tool catalog.

Tool design notes:
- Name: snake_case for in-process; PascalCase for special (matches LLM bias).
- Description: ≤ 200 chars, action-oriented.
- input_schema: `additionalProperties: false`, strict.
- Permission: default high — relax only when proven safe.
"""

import structlog

from aiengine.tools.builtin.web_fetch import register as register_web_fetch
from aiengine.tools.builtin.web_search import register as register_web_search
from aiengine.tools.registry import GlobalToolRegistry

log = structlog.get_logger(__name__)


def register_all_builtin(registry: GlobalToolRegistry) -> None:
    """Wire all built-in tools into the registry.

    doc_search depends on the `ml` extra (sentence-transformers, qdrant-client)
    so it is registered only when that extra is installed. Phase D switches
    the Docker build to `uv sync --extra ml` and this tool becomes available
    automatically.
    """
    register_web_fetch(registry)
    register_web_search(registry)

    try:
        from aiengine.tools.builtin.doc_search import register as register_doc_search
    except ImportError as exc:
        log.info("tool.doc_search.disabled", reason=str(exc))
    else:
        register_doc_search(registry)
