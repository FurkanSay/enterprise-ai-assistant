"""Agent loop — `run_turn()` Python equivalent.

Based on Claude Code's run_turn() (see docs/claw-learnings/01-agent-loop.md).
Adapted for multi-tenant SaaS:
  - Tenant-scoped iteration cap
  - Audit log every tool call
  - SSE-friendly event stream output
  - Token streaming + tool_use streaming interleaved
"""

from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

import structlog

from aiengine.agent.hooks import HookDecision, run_post_tool_use, run_pre_tool_use
from aiengine.agent.state import ContentBlock, Message, MessageRole, TokenUsage
from aiengine.core.config import get_settings
from aiengine.core.errors import IterationLimitExceededError
from aiengine.core.tenant import TenantContext
from aiengine.providers.litellm_provider import stream_completion
from aiengine.tools.registry import get_tool_registry

log = structlog.get_logger(__name__)


@dataclass(slots=True)
class AgentEvent:
    """One SSE-friendly event."""

    kind: str  # "token" | "tool_use" | "tool_result" | "usage" | "done" | "error"
    data: dict[str, Any]


async def run_turn(
    tenant: TenantContext,
    session_id: str | None,
    user_message: str,
    model_override: str | None = None,
    allowed_tools: list[str] | None = None,
) -> AsyncIterator[AgentEvent]:
    """Execute one agent turn — yields events as they happen.

    Algorithm (mirrors Claude Code run_turn):
      1. Load or create session
      2. Append user message to history
      3. Loop:
         a. Stream LLM completion (text + tool_use blocks)
         b. If no tool_use → break
         c. For each tool_use:
            - Pre-hook → permission check → execute → post-hook
            - Append tool_result to history
         d. Iteration counter++ ; raise if > cap
      4. Emit `done` event
    """
    settings = get_settings()
    registry = get_tool_registry()
    iteration_cap = settings.max_tool_iterations_pro  # TODO: derive from tenant plan
    model = model_override or settings.default_llm_model

    # TODO: load session from DB or create new one
    messages: list[Message] = []
    # TODO: append user message + persist
    log.info("agent.turn.start", session_id=session_id, model=model)

    iterations = 0
    while True:
        iterations += 1
        if iterations > iteration_cap:
            raise IterationLimitExceededError(
                f"Agent loop exceeded {iteration_cap} iterations",
                details={"iterations": iterations},
            )

        # ─── Call LLM ────────────────────────────────────────────────────
        tool_specs = registry.definitions_for_llm(allowed_tools=allowed_tools)
        assistant_blocks: list[ContentBlock] = []
        usage = TokenUsage()
        pending_tool_uses: list[tuple[str, str, dict[str, Any]]] = []

        async for chunk in stream_completion(
            model=model, messages=messages, tools=tool_specs
        ):
            if chunk.kind == "text_delta":
                yield AgentEvent("token", {"text": chunk.data["text"]})
            elif chunk.kind == "tool_use":
                yield AgentEvent("tool_use", chunk.data)
                pending_tool_uses.append(
                    (chunk.data["id"], chunk.data["name"], chunk.data["input"])
                )
            elif chunk.kind == "usage":
                usage = TokenUsage(**chunk.data)

        # TODO: persist assistant message (must happen BEFORE tool execution
        # — see docs/claw-learnings/01-agent-loop.md, Decision #4)

        if not pending_tool_uses:
            yield AgentEvent("usage", usage.model_dump())
            yield AgentEvent("done", {"iterations": iterations})
            log.info("agent.turn.complete", iterations=iterations)
            return

        # ─── Execute tools ───────────────────────────────────────────────
        for tool_use_id, tool_name, tool_input in pending_tool_uses:
            pre = await run_pre_tool_use(tenant, tool_name, tool_input)
            if pre.decision == HookDecision.DENY:
                output = pre.reason or f"Tool {tool_name} denied by hook"
                is_error = True
            else:
                effective_input = pre.updated_input or tool_input
                try:
                    output = await registry.execute(
                        tenant=tenant,
                        tool_name=tool_name,
                        tool_input=effective_input,
                    )
                    is_error = False
                except Exception as exc:  # noqa: BLE001 — tool errors must surface to LLM
                    output = f"{exc.__class__.__name__}: {exc}"
                    is_error = True

            post = await run_post_tool_use(tenant, tool_name, tool_input, output, is_error)
            if post.decision == HookDecision.DENY:
                output = post.reason or output
                is_error = True
            elif post.updated_output is not None:
                output = post.updated_output

            yield AgentEvent(
                "tool_result",
                {"id": tool_use_id, "output": output, "is_error": is_error},
            )
            # TODO: append tool_result message to history + persist
