# Roadmap

> Her faz **atomic, buildable, dokümante** ilerler. `main` branch her zaman build edilebilir durumda kalır. Her faz başına yol haritası bu dosyada güncellenir.

## Status legend
- ✅ **Done** — tamamlandı, main'de
- 🚧 **In progress** — aktif çalışılıyor
- ⏳ **Planned** — sırada
- 💭 **Considering** — kapsam dışı kalabilir

---

## Phase A — Skeleton ✅

> Monorepo iskelet + polyglot servis yapıları + observability altyapısı + gRPC kontratları.

- ✅ Monorepo yapısı (services/, protos/, infra/, frontend/, docs/, .github/)
- ✅ docker-compose: Postgres+pgvector, Redis, MinIO, Qdrant, Jaeger, OTel collector, Prometheus, Grafana
- ✅ 4 ADR + 2 Mermaid diagram
- ✅ gRPC kontratları (`protos/`) — common, documents, processing, aiengine, identity
- ✅ AI Engine (Python): FastAPI + agent loop + 3-source tool registry + RAG pipeline (Qdrant, BM25, RRF, reranker) + LiteLLM provider iskelet
- ✅ Gateway (C# .NET 10 / YARP): JWT, rate limit, tenant forwarding
- ✅ Identity (C# .NET 10): Clean Architecture 4 katman, Tenant + User entities
- ✅ Documents (Java Spring Boot 3): pom + DocumentsApplication + application.yml
- ✅ Processing (Rust): Cargo workspace + 3 crate (processing-server, chunker, bm25-index)
- ✅ Realtime (TS NestJS + Fastify): WebSocket gateway, Redis subscriber
- ✅ Frontend (Next.js 15 + React 19 + Tailwind): App Router landing
- ✅ Postgres init: 3 schema + tenants + RLS template + 4 service rolü
- ✅ 8 GitHub Actions CI workflow
- ✅ MIT LICENSE, README, ARCHITECTURE, .env.example

## Phase B — Code-gen + DB foundation ✅

> Servis stub'ları ve DB başlangıç verisi.

- [x] **B1** `buf generate` → libs/generated/{dotnet,java,rust,python} (TypeScript Phase H'de)
- [x] **B2** Servislere stub bağla
  - Python (AI Engine): `kai-protos` path-editable dep
  - Rust (Processing): `libs/generated/rust` workspace member + workspace dep
  - C# (Gateway, Identity): paylaşımlı `Kai.Protos` csproj + ProjectReference
  - Java (Documents): build-helper-maven-plugin ile source folder + gRPC runtime deps
- [x] **B3** Postgres init scripts (Phase C: per-service migration tools üstüne geçilecek)
  - identity_schema: tenants + users + roles + user_roles + refresh_tokens (5 tablo)
  - documents_schema: documents + document_chunks_meta (2 tablo)
  - aiengine_schema: sessions + messages + tool_invocations (3 tablo)
- [x] **B4** RLS policies — her business tablosunda FORCE ROW LEVEL SECURITY + tenant_isolation
- [x] **B5** `docker compose up -d` → 13/15 servis healthy
  - Çözülen sorunlar: monorepo root build context, csproj NoWarn for OTel CVE'leri,
    Rust 1.85 → 1 (edition2024), Cargo workspace member hierarchy, Java FluentValidation.DI,
    Java protobuf-java 3 vs 4 (gRPC Phase D'ye ertelendi), AI Engine CPU-only PyTorch
    + UV_LINK_MODE=copy, kai-protos lib.rs çift tonic include, bm25-index `#[from]`
    duplicate, Processing musl-static + alpine runtime (no glibc dynamic linker mismatch),
    AI Engine `python -m uvicorn` (venv shebang bypass), Documents Flyway off (init
    script source of truth), override.yml processing volume mount (binary'i gizliyordu)
  - Frontend + Realtime: containers up, sadece health probe endpoint mismatch (Phase H'de düzelir)

## Phase C — AI Engine end-to-end smoke ✅

> LLM stream → DB persist → SSE eventleri kullanıcıya akıyor.

- [x] `litellm_provider.stream_completion` gerçek implementasyon (OpenRouter via LiteLLM)
- [x] Session + Message DB persistence (SQLAlchemy async + tenant_session)
- [x] `/v1/chat` SSE end-to-end: prompt → LLM → token stream → response
- [x] AI Engine smoke test (gerçek OpenRouter ile, token akışı + DB persist doğrulandı)
- [ ] `web_fetch` tool implementation (Phase D — tool_use round-trip için lazım)

Phase C side-effects:
- `pyproject.toml`: pytorch / sentence-transformers / qdrant-client `[project.optional-dependencies.ml]` grubuna alındı.
  Base image **1.94 GB → 605 MB**, build I/O Docker Desktop pipe'ını artık devirmiyor.
- `tools/builtin/__init__.py`: doc_search ImportError'a karşı conditional (ml extra olmadan da boot eder).
- .env / .env.example: default model OpenRouter namespace'inde (`openrouter/google/gemma-4-31b-it:free` zero-cost demo).

## Phase D — Documents service ✅

- [x] Multipart upload endpoint
- [x] Apache Tika parse → text extraction
- [x] MinIO storage (tenant-scoped path)
- [x] Postgres metadata + RLS
- [x] Redis Streams: `doc.uploaded.v1` + `doc.deleted.v1` publish
- [ ] gRPC server (deferred — REST + Redis Streams cover current flows)

## Phase E — Processing service ✅

- [x] Redis consumer: `doc.uploaded.v1` → MinIO text fetch → chunk
- [x] tantivy BM25 index (tenant_id filter)
- [x] fastembed BGE-Base 768d → Qdrant dense write
- [x] `/embed` HTTP endpoint (shared embedder with AI Engine query path)
- [x] Per-doc status flow: PARSING → CHUNKING → EMBEDDING → READY / FAILED
- [x] UTF-8 panic guard in chunker + spawn_blocking pipeline isolation
- [x] `doc.deleted.v1` consumer: Qdrant + tantivy purge (Phase M)

## Phase F — Identity service ✅

- [x] EF Core DbContext (User, Tenant, Role, RefreshToken)
- [x] Login endpoint (BCrypt password verify → JWT issue)
- [x] Refresh-token rotation (single-use, hashed in DB, revoke-on-rotate)
- [x] `/v1/auth/refresh` + `/v1/auth/logout` (idempotent)
- [ ] JWKS endpoint (HS256 today; RS256 + JWKS deferred to prod hardening)
- [ ] gRPC ValidateToken (deferred — Gateway HS256 validates with shared secret)

## Phase G — Realtime service ✅

- [x] WS upgrade handler with JWT validation
- [x] Redis subscriber: `stream.<tenant>.<session>` → fanout to client
- [x] Token-by-token streaming end-to-end (AI Engine → Redis → Realtime → Browser)

## Phase H — Frontend ✅

- [x] Auth: Identity login/register forms + client-side route guards
- [x] Documents page: upload + status badge + adaptive polling
- [x] Chat page: message list + SSE token render
- [x] Tailwind UI polish + dark mode

## Phase I — End-to-end smoke + observability ✅

- [x] `scripts/e2e-smoke.sh`: register → login → upload → poll READY → ask → assert
- [x] OpenTelemetry traces across all 5 services in Jaeger
- [x] Prometheus + Grafana provisioning

## Phase J — Grounded RAG + sessions ✅

- [x] doc_search tool returns chunks with citations (document_id + source_location)
- [x] Sessions: list / get / fork — conversation tree with parent_session_id
- [x] Paste-attachment chips: long paste collapses to a clickable card with modal preview
- [x] Frontend session sidebar with delete + rename

## Phase K — Stability + UX polish ✅

- [x] Chunker UTF-8 boundary fix + regression test (Turkish PDF case)
- [x] Pipeline panic guard (spawn_blocking + catch_unwind) — one bad PDF can't freeze the queue
- [x] Reasoning-model "thinking" panel (Nemotron, DeepSeek-R1 `delta.reasoning_content`)
- [x] Typewriter pseudo-streaming for batch-streaming providers
- [x] Strong system prompt (6 explicit rules) forcing `doc_search` use

## Phase L — Deep Search literature mode ✅

- [x] Aggregator across OpenAlex / Semantic Scholar / arXiv / Unpaywall (DOI dedup)
- [x] `mode=deep_search` bypasses the LLM in `/v1/chat` — treat the message as a query
- [x] Auto-ingest every result into the RAG collection (background asyncio tasks)
- [x] `documents.source_session_id` + `source_paper_doi/title` lineage columns
- [x] Persist tool_use + tool_result blocks for the deep_search turn so cards survive reload

## Phase M — UX completion ✅

- [x] Cards-on-reload: sessions endpoint collates `tool_result` blocks onto assistant turns; frontend rebuilds `toolResults`
- [x] Documents `?source_session_id=…` filter UI (purple chip + scoped list query)
- [x] Processing `doc.deleted.v1` consumer (Qdrant + tantivy cleanup, separate consumer-group)
- [x] Next 15 Suspense wrapper for `useSearchParams()` on `/documents`

## Refresh-token flow ✅

- [x] `RefreshToken` entity + EF mapping; `token_hash` stored SHA-256 hex
- [x] Login persists hashed row; `/v1/auth/refresh` rotates atomically (single-use)
- [x] Replay of revoked / expired token → 401 (forgery signal)
- [x] `/v1/auth/logout` idempotent revoke (204 either way)
- [x] Frontend single-flight refresh on 401 + retry-once for both `authedFetch` and SSE chat path
- [x] Gateway anonymizes `/refresh` and `/logout` alongside `/login` and `/register`
- [x] 7-step live smoke: login → refresh1 → replay401 → refresh2 → logout → refresh401 → logout-idempotent

Scope-cut: Identity still connects as Postgres superuser so RLS isn't
enforced on `identity_schema.*`. The `refresh_tokens` table itself has
FORCE RLS + tenant_isolation policy ready; tightening Identity's DB role
is a separate refactor.

## Admin Grafana dashboard ✅

- [x] New "KAI Postgres" datasource (BYPASSRLS via `platform_admin`) wired through provisioning
- [x] Dashboard `kai-token-usage`: 5 stat tiles + daily input/output bars + per-model donut + per-user leaderboard
- [x] `04-roles.sql`: `GRANT USAGE ON SCHEMA … TO platform_admin` + `ALTER DEFAULT PRIVILEGES` so future tables inherit
- [x] All metrics from existing schemas — no new backend code

URL: <http://localhost:3001/d/kai-token-usage> (admin / admin)

## Next — what's still on the table 💭

- [ ] **Identity RLS hardening**: switch Identity to `identity_user` + per-request `SET LOCAL app.current_tenant_id`
- [ ] **Coolify deployment commit**: bake `NEXT_PUBLIC_*` build args + env-driven CORS for self-hosted public deployment (changes already in working tree; not yet committed)
- [ ] **In-product admin panel**: `/admin` route, role-based; same data as Grafana dashboard but inside the app for tenant-level self-service
- [ ] RAGAS evaluation harness (retrieval recall@k + answer faithfulness)
- [ ] File generation tools (`generate_excel`, `generate_pdf`) via Documents
- [ ] Helm chart for Kubernetes + mTLS service-to-service + Vault for secrets

---

## Working agreements

- `main` her zaman buildable
- Her feature → ayrı branch + PR
- Commit mesaj formatı: `type(scope): message` — `feat(aiengine): real LiteLLM streaming`
- WIP yok — küçük ama anlamlı commit'ler
- ADR yaz, **then** code (mimari değişiklikler için)
- Test'i yaz, **then** PR merge (özellikle agent/tool/auth)
