"""ingest_paper — Phase L tool.

Hand a Paper record (or just enough fields to reconstruct one) to the
ingestor and get back the Documents row id once Documents has
accepted the PDF (or, when no OA PDF exists, the abstract text).

The frontend wires its "RAG'e ekle" button to a follow-up turn that
sends a structured tool_use with this name + the paper's full payload.
"""

import orjson

from aiengine.core.tenant import TenantContext
from aiengine.research.ingestor import ingest
from aiengine.research.models import Paper
from aiengine.tools.base import PermissionMode, ToolSpec
from aiengine.tools.registry import GlobalToolRegistry

SPEC = ToolSpec(
    name="ingest_paper",
    description=(
        "Add a paper to the tenant's RAG corpus by downloading its open-access "
        "PDF (if available) and routing it through the Documents pipeline. "
        "When no PDF is available, the abstract + metadata is ingested as a "
        "text document so the model can still cite it. Call this after the "
        "user picks specific papers from a literature_search result."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "paper": {
                "type": "object",
                "description": "Full paper record from literature_search.",
                "additionalProperties": True,
            },
        },
        "required": ["paper"],
        "additionalProperties": False,
    },
    required_permission=PermissionMode.WORKSPACE_WRITE,
    category="action",
    base_or_deferred="base",
)


async def handler(*, tenant: TenantContext, tool_input: dict) -> str:
    raw = tool_input.get("paper") or {}
    paper = Paper(**raw)
    result = await ingest(tenant, paper)
    return orjson.dumps(
        {
            "ui_kind": "paper_ingested",
            "document_id": result.get("id"),
            "title": result.get("title"),
            "status": result.get("status"),
            # Echo back enough id fields that the frontend can match the
            # ingest confirmation to the right card without ambiguity.
            "doi": paper.doi,
            "arxiv_id": paper.arxiv_id,
            "source_id": paper.source_id,
        }
    ).decode("utf-8")


def register(registry: GlobalToolRegistry) -> None:
    registry.register_builtin(SPEC, handler)
