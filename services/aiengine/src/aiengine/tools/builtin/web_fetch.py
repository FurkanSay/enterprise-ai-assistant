"""web_fetch — fetch URL, extract readable text, return summary or text.

Honors tenant URL allowlist (admin-managed).
"""

import orjson

from aiengine.core.tenant import TenantContext
from aiengine.tools.base import PermissionMode, ToolSpec
from aiengine.tools.registry import GlobalToolRegistry

SPEC = ToolSpec(
    name="web_fetch",
    description=(
        "Fetch a URL and return its main text content. "
        "Use this when the user provides a link or you need data from a known web page."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "url": {"type": "string", "format": "uri"},
            "prompt": {
                "type": "string",
                "description": "Optional — focused question about the page content.",
            },
        },
        "required": ["url"],
        "additionalProperties": False,
    },
    required_permission=PermissionMode.READ_ONLY,
    category="web",
)


async def handler(*, tenant: TenantContext, tool_input: dict) -> str:
    # TODO: implement — use httpx + readability + tenant allowlist
    return orjson.dumps(
        {"url": tool_input["url"], "text": "TODO: implement web_fetch"}
    ).decode("utf-8")


def register(registry: GlobalToolRegistry) -> None:
    registry.register_builtin(SPEC, handler)
