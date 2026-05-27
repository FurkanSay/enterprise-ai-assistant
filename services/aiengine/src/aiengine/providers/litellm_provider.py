"""LiteLLM-backed streaming provider.

Thin adapter between the agent loop and the upstream LLM. All provider-
specific knowledge (API keys, endpoint URLs, message format conversion)
is delegated to LiteLLM. The wrapper only:

  1. Translates internal Message / ContentBlock dataclasses into the
     OpenAI chat-completion shape LiteLLM expects.
  2. Translates Anthropic-style tool specs (from the registry) into the
     OpenAI tool format LiteLLM forwards to OpenRouter.
  3. Normalises streaming chunks — including chunked tool_call argument
     deltas — into provider-agnostic StreamChunk events the agent loop
     can iterate without caring about OpenAI vs Anthropic vs anything.

OpenRouter usage: pass a model id prefixed with `openrouter/`, e.g.
`openrouter/deepseek/deepseek-v4-flash:free`. LiteLLM picks up
OPENROUTER_API_KEY from the environment automatically. Note that not
every model on OpenRouter supports function calling — pick one that
advertises it, or the agent loop falls back to pure-text responses.
"""

from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any

import litellm
import orjson
import structlog

from aiengine.agent.state import ContentBlock, Message, MessageRole

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class StreamChunk:
    """Normalised stream event."""

    kind: str  # "text_delta" | "tool_use" | "usage" | "thinking" | "stop"
    data: dict[str, Any]


@dataclass(slots=True)
class _PartialToolCall:
    """Accumulator for one streamed OpenAI tool_call.

    OpenAI streams the JSON `arguments` string as deltas; we collect them
    and parse once the call is complete (finish_reason='tool_calls').
    """

    call_id: str = ""
    name: str = ""
    arguments: str = ""
    index: int = 0


def _message_to_litellm(msg: Message) -> dict[str, Any]:
    """Convert our internal Message to LiteLLM/OpenAI chat-completion shape.

    Three shapes:
      - assistant/user text → {"role": "...", "content": "..."}
      - assistant with tool_use blocks → adds "tool_calls" list
      - tool role with tool_result → {"role": "tool", "tool_call_id": ..., "content": ...}
    """
    role = msg.role.value if hasattr(msg.role, "value") else str(msg.role)

    if role == "tool":
        # Tool result rows — one ContentBlock per row.
        text_parts: list[str] = []
        tool_call_id = ""
        for block in msg.blocks:
            if block.type == "tool_result":
                tool_call_id = block.tool_result_for_id or tool_call_id
                if block.tool_output:
                    text_parts.append(block.tool_output)
        return {
            "role": "tool",
            "tool_call_id": tool_call_id,
            "content": "\n".join(text_parts),
        }

    text_parts = []
    tool_calls: list[dict[str, Any]] = []
    for block in msg.blocks:
        if block.type == "text" and block.text:
            text_parts.append(block.text)
        elif block.type == "tool_use":
            tool_calls.append(
                {
                    "id": block.tool_use_id or "",
                    "type": "function",
                    "function": {
                        "name": block.tool_name or "",
                        "arguments": orjson.dumps(block.tool_input or {}).decode("utf-8"),
                    },
                }
            )

    msg_dict: dict[str, Any] = {"role": role, "content": "\n".join(text_parts) or ""}
    if tool_calls:
        msg_dict["tool_calls"] = tool_calls
        # OpenAI expects content=null when tool_calls are present and no
        # accompanying text. Some providers tolerate empty string, but
        # the safest contract is to clear content.
        if not text_parts:
            msg_dict["content"] = None
    return msg_dict


def _tools_to_openai(tool_specs: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Convert Anthropic-style tool specs (from registry.to_anthropic_schema)
    to the OpenAI function-tool format LiteLLM forwards downstream.

      Anthropic: {name, description, input_schema}
      OpenAI:    {type: "function", function: {name, description, parameters}}
    """
    return [
        {
            "type": "function",
            "function": {
                "name": spec["name"],
                "description": spec.get("description", ""),
                "parameters": spec.get("input_schema") or {"type": "object", "properties": {}},
            },
        }
        for spec in tool_specs
    ]


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
      StreamChunk("tool_use", {"id", "name", "input"}) when the upstream
        emits a complete tool_call (after its argument deltas are joined
        and parsed).
      StreamChunk("usage", ...) when the provider reports token counts.
      StreamChunk("stop", {"reason": ...}) at end of stream.
    """
    payload: list[dict[str, Any]] = []
    if system_prompt:
        payload.append({"role": "system", "content": system_prompt})
    payload.extend(_message_to_litellm(m) for m in messages)

    kwargs: dict[str, Any] = {
        "model": model,
        "messages": payload,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "stream": True,
        "stream_options": {"include_usage": True},
    }
    if tools:
        kwargs["tools"] = _tools_to_openai(tools)
        # Let the model decide whether to call a tool. "auto" is the
        # OpenAI/LiteLLM default but being explicit guards against any
        # provider that flips the default to "none".
        kwargs["tool_choice"] = "auto"

    log.info(
        "llm.stream.start",
        model=model,
        message_count=len(payload),
        max_tokens=max_tokens,
        has_tools=bool(tools),
        tool_count=len(tools) if tools else 0,
    )

    try:
        response_iter = await litellm.acompletion(**kwargs)
    except Exception:
        log.exception("llm.stream.start_failed", model=model)
        raise

    stop_reason: str | None = None
    # Tool-call argument deltas can arrive across many chunks and even
    # interleave across multiple calls in a single response. Index them
    # by the `index` field LiteLLM/OpenAI use.
    partial_calls: dict[int, _PartialToolCall] = {}

    async for chunk in response_iter:
        choices = getattr(chunk, "choices", None) or []
        if choices:
            choice = choices[0]
            delta = getattr(choice, "delta", None)
            if delta is not None:
                # Reasoning models (Nemotron-3, DeepSeek-R1, …) emit their
                # chain-of-thought in `reasoning_content` BEFORE any actual
                # answer lands on `content`. If we only watch `content`,
                # the user sees a long silence followed by a burst — which
                # looks like "no streaming". Stream the reasoning as a
                # distinct `thinking` event so the UI can show "düşünüyor…"
                # while the model is mid-cogitation.
                reasoning_piece = getattr(delta, "reasoning_content", None) or getattr(
                    delta, "reasoning", None
                )
                if reasoning_piece:
                    yield StreamChunk("thinking", {"text": reasoning_piece})
                text_piece = getattr(delta, "content", None)
                if text_piece:
                    yield StreamChunk("text_delta", {"text": text_piece})
                tool_calls = getattr(delta, "tool_calls", None) or []
                for tc in tool_calls:
                    idx = getattr(tc, "index", 0) or 0
                    bucket = partial_calls.setdefault(idx, _PartialToolCall(index=idx))
                    if getattr(tc, "id", None):
                        bucket.call_id = tc.id
                    fn = getattr(tc, "function", None)
                    if fn is not None:
                        name_piece = getattr(fn, "name", None)
                        if name_piece:
                            bucket.name = name_piece
                        args_piece = getattr(fn, "arguments", None)
                        if args_piece:
                            bucket.arguments += args_piece

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

    # Flush completed tool_calls. Some providers emit them only at the
    # very end (everything in one chunk); others stream argument deltas.
    if partial_calls:
        for call in sorted(partial_calls.values(), key=lambda c: c.index):
            if not call.name:
                continue  # malformed — provider sent index but never name
            try:
                tool_input = orjson.loads(call.arguments) if call.arguments else {}
            except orjson.JSONDecodeError:
                log.warning(
                    "llm.tool_call.bad_json",
                    name=call.name,
                    arguments_len=len(call.arguments),
                )
                tool_input = {}
            yield StreamChunk(
                "tool_use",
                {
                    "id": call.call_id or f"call-{call.index}",
                    "name": call.name,
                    "input": tool_input,
                },
            )

    yield StreamChunk("stop", {"reason": stop_reason or "end_turn"})
    log.info(
        "llm.stream.complete",
        model=model,
        stop_reason=stop_reason,
        tool_calls=len(partial_calls),
    )
