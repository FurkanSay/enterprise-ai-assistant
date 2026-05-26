"""web_search — search the public web. Domain allowlist / blocklist per tenant."""

import orjson

from aiengine.core.tenant import TenantContext
from aiengine.tools.base import PermissionMode, ToolSpec
from aiengine.tools.registry import GlobalToolRegistry

SPEC = ToolSpec(
    name="web_search",
    description=(
        "Search the public web and return cited results. "
        "Use for current events, public reference info, things not in tenant docs."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "query": {"type": "string", "minLength": 2},
            "allowed_domains": {"type": "array", "items": {"type": "string"}},
            "blocked_domains": {"type": "array", "items": {"type": "string"}},
        },
        "required": ["query"],
        "additionalProperties": False,
    },
    required_permission=PermissionMode.READ_ONLY,
    category="web",
)


async def handler(*, tenant: TenantContext, tool_input: dict) -> str:
    # TODO: integrate with a search provider (Tavily/Brave/Serper)
    return orjson.dumps(
        {"query": tool_input["query"], "results": [], "note": "TODO"}
    ).decode("utf-8")


def register(registry: GlobalToolRegistry) -> None:
    registry.register_builtin(SPEC, handler)
