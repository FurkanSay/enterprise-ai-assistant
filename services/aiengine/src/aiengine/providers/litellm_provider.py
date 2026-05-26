"""LiteLLM-backed streaming provider.

This is the thin adapter agent loop uses. All multi-provider knowledge
(API keys, endpoints, format differences) is delegated to LiteLLM.
"""

from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

import structlog

from aiengine.agent.state import Message

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class StreamChunk:
    """Normalized stream event (provider-agnostic)."""

    kind: str  # "text_delta" | "tool_use" | "usage" | "thinking" | "stop"
    data: dict[str, Any]


async def stream_completion(
    model: str,
    messages: list[Message],
    tools: list[dict[str, Any]] | None = None,
    max_tokens: int = 4096,
) -> AsyncIterator[StreamChunk]:
    """Stream LLM completion. Yields StreamChunk events.

    TODO: implement with litellm.acompletion(..., stream=True) and parse
    delta blocks into normalized StreamChunk events.
    """
    log.debug("llm.stream.start", model=model, message_count=len(messages))
    # Placeholder — yields nothing for now
    if False:
        yield StreamChunk("text_delta", {"text": ""})
