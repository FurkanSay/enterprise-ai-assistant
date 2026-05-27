"""Context vars shared across the chat → tool → ingest call chain.

Tool handlers don't get the session_id through their signature (the
ToolHandler protocol takes only tenant + tool_input). Routing it via
a ContextVar means literature_search can schedule background ingest
tasks tagged with the originating session, without changing every
handler's signature.
"""

from contextvars import ContextVar

# Set by the agent loop right before tool execution; the literature_search
# handler reads it to tag any documents it ingests in the background.
current_session_id: ContextVar[str | None] = ContextVar(
    "current_session_id", default=None
)
