"""Built-in tool catalog.

Tool design notes:
- Name: snake_case for in-process; PascalCase for special (matches LLM bias).
- Description: ≤ 200 chars, action-oriented.
- input_schema: `additionalProperties: false`, strict.
- Permission: default high — relax only when proven safe.
"""

from aiengine.tools.builtin.doc_search import register as register_doc_search
from aiengine.tools.builtin.web_fetch import register as register_web_fetch
from aiengine.tools.builtin.web_search import register as register_web_search
from aiengine.tools.registry import GlobalToolRegistry


def register_all_builtin(registry: GlobalToolRegistry) -> None:
    """Wire all built-in tools into the registry."""
    register_doc_search(registry)
    register_web_fetch(registry)
    register_web_search(registry)
    # TODO: db_query (text-to-SQL), generate_excel, generate_pdf...
