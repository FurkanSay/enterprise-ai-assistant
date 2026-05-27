"""Agent loop — Phase C wiring.

This is the run_turn() Python equivalent of Claude Code's run_turn. The
shape mirrors docs/claw-learnings/01-agent-loop.md:

  1. Load (or create) the session, then pull the history.
  2. Append the user message to history (DB write before LLM call).
  3. Loop:
     a. Stream the LLM completion, accumulating text + (future) tool_use blocks.
     b. Persist the assistant message BEFORE running any tools — see
        Decision #4 in the learnings doc; tool_result rows orphan otherwise.
     c. If no tool_use blocks, emit `done` and return.
     d. Otherwise execute each tool (Phase D will wire the real
        round-trip; today the tool loop is reachable but unused because
        litellm_provider does not yet emit tool_use chunks).
  4. Iteration cap protects against runaway tool loops.

Phase C scope: the stream/persist path is real; the tool path is plumbed
but not yet exercised end-to-end because the OpenRouter wire format we
return does not yet carry tool_use blocks. That arrives in Phase D once
we extend stream_completion to surface tool deltas.
"""

from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any
from uuid import UUID

import structlog

from aiengine.agent.hooks import HookDecision, run_post_tool_use, run_pre_tool_use
from aiengine.agent.repository import (
    append_message,
    create_session,
    get_session,
    list_messages,
)
from aiengine.agent.state import ContentBlock, MessageRole, TokenUsage
from aiengine.core.config import get_settings
from aiengine.core.db import tenant_session
from aiengine.core.errors import IterationLimitExceededError, NotFoundError
from aiengine.core.tenant import TenantContext
from aiengine.providers.litellm_provider import stream_completion
from aiengine.tools.registry import get_tool_registry

log = structlog.get_logger(__name__)

SYSTEM_PROMPT_NORMAL = (
    "You are the Enterprise AI Assistant for a multi-tenant platform.\n"
    "\n"
    "CRITICAL TOOL POLICY — read carefully and follow exactly:\n"
    "\n"
    "1. You MUST call `doc_search` BEFORE answering any substantive\n"
    "   question. Do not 'check if I should search' — always search\n"
    "   first. The user's documents are in a vector store you can\n"
    "   only see by calling this tool.\n"
    "\n"
    "2. NEVER answer from your training knowledge when the question\n"
    "   is even loosely about something that could be in a document —\n"
    "   technical comparisons, summaries, methodology, citations,\n"
    "   anything subject-matter. Even if you know the answer in\n"
    "   general, the user uploaded papers specifically to ground\n"
    "   YOUR answer in THEIR sources. Calling doc_search is the\n"
    "   whole job.\n"
    "\n"
    "3. NEVER claim 'RAG is not enabled' or 'I only see metadata' or\n"
    "   'I cannot access the full text'. doc_search returns full\n"
    "   chunk text from the documents. If you have not called it,\n"
    "   you have not tried.\n"
    "\n"
    "4. The query passed to doc_search should be in English even if\n"
    "   the user wrote Turkish — abstracts in the index are mostly\n"
    "   English. Use technical terms (BM25, dense retrieval, vector\n"
    "   database, etc.) rather than restating the whole question.\n"
    "\n"
    "5. If a search returns zero relevant chunks, say so honestly:\n"
    "   'Eklediğiniz kaynaklarda bu konuya dair pasaj bulamadım.'\n"
    "   Don't fall back to general knowledge.\n"
    "\n"
    "6. When citing, reference the document by name or source_location\n"
    "   from the tool result.\n"
    "\n"
    "Only exception to (1): pure social messages (hi / thanks / who\n"
    "are you) — those don't need a search.\n"
    "\n"
    "Reply in the user's language. Keep answers concise but cite."
)

SYSTEM_PROMPT_DEEP_SEARCH = (
    "You are the Enterprise AI Assistant in DEEP SEARCH mode — an academic "
    "research helper.\n"
    "\n"
    "Tools available:\n"
    "  - `literature_search(query, max_results?, year_from?)`: search the "
    "open academic graph (OpenAlex, Semantic Scholar, arXiv). Call this "
    "for any literature, related-work, or 'recent papers' question. Pass "
    "a focused English query even if the user wrote in Turkish — academic "
    "abstracts are predominantly English.\n"
    "  - `ingest_paper(paper)`: add a specific paper to the tenant's RAG "
    "corpus.\n"
    "\n"
    "ROUTING RULES (read carefully):\n"
    "  1. If the user message starts with the literal tag '[ACTION:ADD_PAPER]', "
    "the user clicked the UI's 'Add to RAG' button. The message contains a "
    "JSON `paper` object. Call `ingest_paper` with EXACTLY that object as "
    "the `paper` argument. Do NOT call literature_search. Do NOT search "
    "again. Do NOT ask for confirmation. After the tool returns, reply "
    "with one short Turkish sentence confirming the addition (e.g. "
    "'Makale RAG'e eklendi.').\n"
    "  2. Otherwise, treat the message as a research query and call "
    "`literature_search` first, then summarise the top 3-5 results in your "
    "own words. Cite by author + year (e.g. 'Smith et al., 2023').\n"
    "\n"
    "Reply in the user's language."
)

# Tool catalogues per mode. Names match registered tool IDs.
TOOLS_BY_MODE: dict[str, list[str]] = {
    "normal": ["doc_search", "web_search", "web_fetch"],
    "deep_search": ["literature_search", "ingest_paper"],
}


@dataclass(slots=True)
class AgentEvent:
    """One SSE-friendly event."""

    kind: str  # token | tool_use | tool_result | usage | done | session | error
    data: dict[str, Any]


async def run_turn(
    tenant: TenantContext,
    session_id: str | None,
    user_message: str,
    model_override: str | None = None,
    allowed_tools: list[str] | None = None,
    mode: str = "normal",
) -> AsyncIterator[AgentEvent]:
    """Execute one agent turn, yielding SSE events as they happen.

    `mode` selects the tool catalogue and system prompt. The frontend
    forces a fresh session when the user toggles modes, so a single
    session always sees one consistent toolset.
    """
    settings = get_settings()
    registry = get_tool_registry()
    iteration_cap = settings.max_tool_iterations_pro
    model = model_override or settings.default_llm_model

    # Resolve mode → system prompt + tool gate. Caller-supplied
    # allowed_tools (if any) intersects with the mode's catalogue so a
    # deep-search chat cannot escape into doc_search and vice versa.
    system_prompt = (
        SYSTEM_PROMPT_DEEP_SEARCH if mode == "deep_search" else SYSTEM_PROMPT_NORMAL
    )
    mode_tools = TOOLS_BY_MODE.get(mode, TOOLS_BY_MODE["normal"])
    if allowed_tools:
        allowed_tools = [t for t in allowed_tools if t in mode_tools]
    else:
        allowed_tools = list(mode_tools)

    async with tenant_session(tenant) as db:
        # ─── 1. Load / create session ──────────────────────────────────
        if session_id:
            try:
                session_uuid = UUID(session_id)
            except ValueError as exc:
                raise NotFoundError(f"Invalid session id: {session_id}") from exc
            session = await get_session(db, session_uuid)
            if session is None:
                raise NotFoundError(f"Session {session_id} not found")
            # Caller mode wins. The session row records the mode it was
            # BORN in (so the sidebar can label it), but the toolset for
            # *this* turn comes from req.mode. That way a user who
            # opened an old deep_search session and just wants to ask a
            # follow-up question about already-ingested papers can do
            # so without manually starting a fresh chat — the frontend
            # passes mode=normal and we hand the agent doc_search.
        else:
            session = await create_session(
                db, tenant, title=user_message[:80], model=model, mode=mode,
            )
            yield AgentEvent("session", {"id": session.id, "created": True})

        session_uuid = UUID(session.id)

        # ─── 2. Append user message + emit ─────────────────────────────
        user_block = ContentBlock(type="text", text=user_message)
        await append_message(
            db, tenant, session_uuid, role=MessageRole.USER, blocks=[user_block]
        )

        history = await list_messages(db, session_uuid)
        log.info(
            "agent.turn.start",
            session_id=session.id,
            model=model,
            history_size=len(history),
        )

        # ─── 3. Loop ───────────────────────────────────────────────────
        iterations = 0
        while True:
            iterations += 1
            if iterations > iteration_cap:
                raise IterationLimitExceededError(
                    f"Agent loop exceeded {iteration_cap} iterations",
                    details={"iterations": iterations},
                )

            tool_specs = registry.definitions_for_llm(allowed_tools=allowed_tools)

            assistant_text_parts: list[str] = []
            usage = TokenUsage()
            pending_tool_uses: list[tuple[str, str, dict[str, Any]]] = []
            stop_reason = "end_turn"

            async for chunk in stream_completion(
                model=model,
                messages=history,
                tools=tool_specs,
                system_prompt=system_prompt,
                max_tokens=settings.max_tokens_per_request,
            ):
                if chunk.kind == "text_delta":
                    text = chunk.data.get("text", "")
                    assistant_text_parts.append(text)
                    yield AgentEvent("token", {"text": text})
                elif chunk.kind == "thinking":
                    # Reasoning-model chain-of-thought. We do NOT persist
                    # this — it's transient UX, not durable history. The
                    # SSE event lets the UI render a "düşünüyor…" panel.
                    yield AgentEvent("thinking", chunk.data)
                elif chunk.kind == "tool_use":
                    yield AgentEvent("tool_use", chunk.data)
                    pending_tool_uses.append(
                        (chunk.data["id"], chunk.data["name"], chunk.data["input"])
                    )
                elif chunk.kind == "usage":
                    usage = TokenUsage(
                        input_tokens=chunk.data.get("input_tokens", 0),
                        output_tokens=chunk.data.get("output_tokens", 0),
                    )
                elif chunk.kind == "stop":
                    stop_reason = chunk.data.get("reason", "end_turn")

            # Persist assistant message BEFORE running any tools.
            assistant_blocks: list[ContentBlock] = []
            full_text = "".join(assistant_text_parts)
            if full_text:
                assistant_blocks.append(ContentBlock(type="text", text=full_text))
            for tool_use_id, tool_name, tool_input in pending_tool_uses:
                assistant_blocks.append(
                    ContentBlock(
                        type="tool_use",
                        tool_use_id=tool_use_id,
                        tool_name=tool_name,
                        tool_input=tool_input,
                    )
                )
            if assistant_blocks:
                assistant_msg = await append_message(
                    db,
                    tenant,
                    session_uuid,
                    role=MessageRole.ASSISTANT,
                    blocks=assistant_blocks,
                    usage=usage,
                )
                history.append(assistant_msg)

            if not pending_tool_uses:
                yield AgentEvent("usage", usage.model_dump())
                yield AgentEvent(
                    "done",
                    {
                        "iterations": iterations,
                        "stop_reason": stop_reason,
                        "session_id": session.id,
                    },
                )
                log.info(
                    "agent.turn.complete",
                    iterations=iterations,
                    stop_reason=stop_reason,
                )
                return

            # ─── 4. Tool execution (Phase D) ───────────────────────────
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
                    except Exception as exc:  # noqa: BLE001
                        output = f"{exc.__class__.__name__}: {exc}"
                        is_error = True

                post = await run_post_tool_use(
                    tenant, tool_name, tool_input, output, is_error
                )
                if post.decision == HookDecision.DENY:
                    output = post.reason or output
                    is_error = True
                elif post.updated_output is not None:
                    output = post.updated_output

                yield AgentEvent(
                    "tool_result",
                    {"id": tool_use_id, "output": output, "is_error": is_error},
                )

                tool_result_msg = await append_message(
                    db,
                    tenant,
                    session_uuid,
                    role=MessageRole.TOOL,
                    blocks=[
                        ContentBlock(
                            type="tool_result",
                            tool_result_for_id=tool_use_id,
                            tool_name=tool_name,
                            tool_output=output,
                            is_error=is_error,
                        )
                    ],
                )
                history.append(tool_result_msg)
