"""literature_search — Phase L tool.

Search the open academic graph (OpenAlex + Semantic Scholar + arXiv)
and return ranked, deduped paper metadata. The frontend uses the
structured output to render paper cards with an "İndir + RAG'e ekle"
button per row; the model uses it to ground its summaries.

Only available in `mode=deep_search` chats — the regular tenant
chat catalogue exposes doc_search + web_search instead.
"""

import orjson

from aiengine.core.config import get_settings
from aiengine.core.tenant import TenantContext
from aiengine.research.aggregator import search_all
from aiengine.tools.base import PermissionMode, ToolSpec
from aiengine.tools.registry import GlobalToolRegistry

SPEC = ToolSpec(
    name="literature_search",
    description=(
        "Search the open academic literature (OpenAlex, Semantic Scholar, arXiv) "
        "for papers matching a topic. Returns deduped, citation-sorted records "
        "with title, authors, year, abstract, DOI and a hint about whether an "
        "open-access PDF is available. Use this in Deep Search mode whenever "
        "the user asks for a literature overview, related work, or 'recent "
        "papers about X'."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Search topic, English or Turkish. Be specific.",
                "minLength": 2,
            },
            "max_results": {
                "type": "integer",
                "description": "How many papers to return (default 8, max 20).",
                "minimum": 1,
                "maximum": 20,
            },
            "year_from": {
                "type": "integer",
                "description": "Optional — only return papers published this year or later.",
                "minimum": 1900,
                "maximum": 2100,
            },
        },
        "required": ["query"],
        "additionalProperties": False,
    },
    required_permission=PermissionMode.READ_ONLY,
    category="retrieval",
    base_or_deferred="base",
)


async def handler(*, tenant: TenantContext, tool_input: dict) -> str:
    settings = get_settings()
    query = tool_input["query"]
    max_results = int(tool_input.get("max_results", 8))
    year_from = tool_input.get("year_from")

    papers = await search_all(
        query,
        max_results=max_results,
        year_from=year_from,
        polite_email=settings.polite_email or None,
    )
    # Emit a uiHint key the frontend recognises so the chat-messages
    # renderer can switch from raw-JSON dump to the paper-card layout.
    return orjson.dumps(
        {
            "ui_kind": "paper_list",
            "query": query,
            "count": len(papers),
            "papers": [p.model_dump() for p in papers],
        }
    ).decode("utf-8")


def register(registry: GlobalToolRegistry) -> None:
    registry.register_builtin(SPEC, handler)
