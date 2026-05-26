"""doc_search — RAG retrieval tool.

Hybrid search: Qdrant dense + Processing BM25 → RRF → rerank → top-K.
See: docs/mvp-tools/02-rag-document-qa.md (when written)
"""

import orjson

from aiengine.core.tenant import TenantContext
from aiengine.rag.pipeline import hybrid_search
from aiengine.tools.base import PermissionMode, ToolSpec
from aiengine.tools.registry import GlobalToolRegistry

SPEC = ToolSpec(
    name="doc_search",
    description=(
        "Search the tenant's uploaded documents for relevant passages. "
        "Returns top-K chunks with citations (document name + page/section). "
        "Use this when the user asks about anything related to their own files."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Natural-language search query.",
                "minLength": 2,
            },
            "top_k": {
                "type": "integer",
                "description": "How many chunks to return (default 5, max 20).",
                "minimum": 1,
                "maximum": 20,
            },
            "document_ids": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Optional — restrict search to specific documents.",
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
    """Execute hybrid search."""
    query = tool_input["query"]
    top_k = int(tool_input.get("top_k", 5))
    document_ids = tool_input.get("document_ids")

    results = await hybrid_search(
        tenant=tenant,
        query=query,
        top_k=top_k,
        document_ids=document_ids,
    )

    return orjson.dumps(
        {"query": query, "results": [r.model_dump() for r in results]}
    ).decode("utf-8")


def register(registry: GlobalToolRegistry) -> None:
    registry.register_builtin(SPEC, handler)
