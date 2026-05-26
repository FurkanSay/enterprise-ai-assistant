"""LLM provider abstraction via LiteLLM.

Why LiteLLM instead of vendoring our own:
  - 100+ providers out of the box (Anthropic, OpenAI, xAI, Cohere, Ollama, ...)
  - Unified prompt-cache + streaming interface
  - Saves us from re-implementing Claude Code's provider trait pattern
  - Easy fallback chain + load balancing

See: docs/claw-learnings/04-provider-abstraction.md
"""

from aiengine.providers.litellm_provider import StreamChunk, stream_completion

__all__ = ["stream_completion", "StreamChunk"]
