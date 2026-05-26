"""LiteLLM-backed streaming provider.

This is the thin adapter the agent loop uses. All multi-provider knowledge
(API keys, endpoints, message format conversion, tool-use semantics) is
delegated to LiteLLM. The wrapper only:

  1. Translates our internal Message / ContentBlock dataclasses into the
     OpenAI chat-completion format LiteLLM accepts.
  2. Normalises LiteLLM's streaming chunks into provider-agnostic
     StreamChunk events the agent loop can iterate.

OpenRouter usage: pass a model id prefixed with `openrouter/`, e.g.
`openrouter/anthropic/claude-3.5-sonnet`. LiteLLM picks up
OPENROUTER_API_KEY from the environment automatically.
"""

from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

import litellm
import structlog

from aiengine.agent.state import ContentBlock, Message, MessageRole

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class StreamChunk:
    """Normalised stream event."""

    kind: str  # "text_delta" | "tool_use" | "usage" | "thinking" | "stop"
    data: dict[str, Any]


def _message_to_litellm(msg: Message) -> dict[str, Any]:
    """Convert our internal Message to LiteLLM/OpenAI chat-completion shape.

    LiteLLM accepts either string content (simple text) or a list of
    content blocks. We use the simple form for assistant/user text and
    drop tool blocks for now — Phase D wires the full tool-use round-trip.
    """
    text_parts: list[str] = []
    for block in msg.blocks:
        if block.type == "text" and block.text:
            text_parts.append(block.text)
        elif block.type == "tool_result" and block.tool_output:
            # Tool results render as "[tool: foo] <output>" until Phase D
            # introduces the structured tool_calls / tool_response shape.
            tool_name = block.tool_name or "tool"
            text_parts.append(f"[{tool_name}] {block.tool_output}")

    role = msg.role.value if hasattr(msg.role, "value") else str(msg.role)
    return {"role": role, "content": "\n".join(text_parts) or ""}


async def stream_completion(
    model: str,
    messages: list[Message],
    tools: list[dict[str, Any]] | None = None,
    max_tokens: int = 4096,
    temperature: float = 0.7,
    system_prompt: str | None = None,
) -> AsyncIterator[StreamChunk]:
    """Stream a chat completion as normalised StreamChunk events.

    Yields:
      StreamChunk("text_delta", {"text": "..."}) for each token chunk
      StreamChunk("usage", {"input_tokens": N, "output_tokens": M}) once the
        upstream reports usage (usually with the final chunk)
      StreamChunk("stop", {"reason": "..."}) at end of stream
    """
    payload: list[dict[str, Any]] = []
    if system_prompt:
        payload.append({"role": "system", "content": system_prompt})
    payload.extend(_message_to_litellm(m) for m in messages)

    log.info(
        "llm.stream.start",
        model=model,
        message_count=len(payload),
        max_tokens=max_tokens,
        has_tools=bool(tools),
    )

    try:
        response_iter = await litellm.acompletion(
            model=model,
            messages=payload,
            max_tokens=max_tokens,
            temperature=temperature,
            stream=True,
            stream_options={"include_usage": True},
        )
    except Exception:
        log.exception("llm.stream.start_failed", model=model)
        raise

    stop_reason: str | None = None
    async for chunk in response_iter:
        # Shape: ModelResponseStream with .choices[0].delta.content / .finish_reason
        # and a final .usage block when include_usage is True.
        choices = getattr(chunk, "choices", None) or []
        if choices:
            choice = choices[0]
            delta = getattr(choice, "delta", None)
            if delta is not None:
                text_piece = getattr(delta, "content", None)
                if text_piece:
                    yield StreamChunk("text_delta", {"text": text_piece})
            finish_reason = getattr(choice, "finish_reason", None)
            if finish_reason:
                stop_reason = finish_reason

        usage = getattr(chunk, "usage", None)
        if usage is not None:
            yield StreamChunk(
                "usage",
                {
                    "input_tokens": getattr(usage, "prompt_tokens", 0) or 0,
                    "output_tokens": getattr(usage, "completion_tokens", 0) or 0,
                    "total_tokens": getattr(usage, "total_tokens", 0) or 0,
                },
            )

    yield StreamChunk("stop", {"reason": stop_reason or "end_turn"})
    log.info("llm.stream.complete", model=model, stop_reason=stop_reason)
