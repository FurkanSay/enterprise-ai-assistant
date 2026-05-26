# AI Engine 🐍 ⭐

Python FastAPI servisi — agent loop, tool registry, RAG pipeline, LLM orchestration.

> **Bu projenin kalbi.** CV'ye konacak Python kalitesinin gösterileceği yer burası.

## Sorumluluk

- **Agent loop** — kullanıcı sorusu → LLM ↔ tool döngüsü → cevap
- **Tool registry** — built-in tools, dynamic permission classification
- **RAG pipeline** — embedding, Qdrant vector search, BM25 hybrid (Processing gRPC), reranking
- **LLM orchestration** — LiteLLM ile multi-provider (Anthropic, OpenAI, Ollama)
- **Token streaming** — SSE (browser) + Redis pub/sub (Realtime servisine)
- **Evaluation** — RAGAS metrics, hallucination detection
- **Multi-tenant** — her request `TenantContext` ile, DB RLS + Qdrant payload filter

## Sorumluluk dışı

- Dosya storage (Documents servisi)
- Chunking + BM25 indexing (Processing servisi)
- WebSocket fanout (Realtime servisi)
- Auth (Identity servisi — JWT validation Gateway'de yapılır)

## Geliştirme

```bash
# uv install (https://docs.astral.sh/uv/)
uv sync

# Run dev server
uv run uvicorn aiengine.main:app --reload --port 8084

# Test
uv run pytest

# Lint + format
uv run ruff check src/ tests/
uv run ruff format src/ tests/

# Type check
uv run mypy src/
```

## Mimari katmanlar

```
src/aiengine/
├── api/                  ← FastAPI routes (HTTP boundary)
│   ├── routes/
│   │   ├── chat.py       ← POST /chat (SSE token stream)
│   │   ├── sessions.py   ← GET/POST/DELETE /sessions
│   │   ├── documents.py  ← internal: trigger re-embed
│   │   └── health.py     ← /health/live, /health/ready
│   ├── middleware.py     ← tenant context, request id
│   └── dependencies.py
├── agent/                ← agent loop (Claw'ın run_turn() Python karşılığı)
│   ├── loop.py
│   ├── state.py          ← Session, Message, ContentBlock
│   └── hooks.py          ← PreToolUse, PostToolUse
├── tools/                ← tool catalog
│   ├── base.py           ← ToolSpec, ToolHandler protocol
│   ├── registry.py       ← GlobalToolRegistry
│   ├── permissions.py    ← classify_*, PermissionEnforcer
│   └── builtin/
│       ├── doc_search.py ← RAG search tool
│       ├── web_fetch.py
│       ├── web_search.py
│       └── db_query.py   ← (placeholder — text-to-SQL)
├── providers/            ← LLM provider abstraction
│   ├── base.py
│   └── litellm_provider.py
├── rag/                  ← RAG pipeline
│   ├── pipeline.py       ← orchestrator
│   ├── embeddings.py     ← sentence-transformers
│   ├── reranker.py       ← cross-encoder
│   ├── qdrant_store.py   ← vector DB client
│   └── bm25_client.py    ← gRPC client to Processing
├── eval/                 ← RAGAS + hallucination detection
│   └── ragas_metrics.py
└── core/                 ← cross-cutting
    ├── config.py         ← pydantic Settings
    ├── logging.py        ← structlog
    ├── telemetry.py      ← OpenTelemetry setup
    ├── tenant.py         ← TenantContext model
    └── errors.py         ← exception hierarchy
```

## Referans

- [docs/claw-learnings/01-agent-loop.md](../../docs/claw-learnings/01-agent-loop.md) — agent loop tasarımı
- [docs/claw-learnings/02-permission-enforcer.md](../../docs/claw-learnings/02-permission-enforcer.md) — permission sistemi
- [docs/claw-learnings/03-tool-registry.md](../../docs/claw-learnings/03-tool-registry.md) — tool registry
- [docs/claw-learnings/04-provider-abstraction.md](../../docs/claw-learnings/04-provider-abstraction.md) — provider pattern
- [docs/mvp-tools/](../../docs/mvp-tools/) — RAG, text-to-SQL, file gen tool notları
