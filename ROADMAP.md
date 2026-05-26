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

## Phase B — Code-gen + DB foundation 🚧

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

## Phase C — AI Engine end-to-end smoke ⏳

> Mock tool ile gerçek LLM streaming.

- [ ] `litellm_provider.stream_completion` gerçek implementasyon (Anthropic ile)
- [ ] Session + Message DB persistence
- [ ] `web_fetch` tool implementation (httpx + readability)
- [ ] `/v1/chat` SSE end-to-end: prompt → LLM → token stream → response
- [ ] AI Engine smoke test (gerçek Anthropic API key ile)

## Phase D — Documents service ⏳

- [ ] Multipart upload endpoint
- [ ] Apache Tika parse → text extraction
- [ ] MinIO storage (tenant-scoped path)
- [ ] Postgres metadata + RLS
- [ ] Redis Streams: `doc.uploaded.v1` publish
- [ ] gRPC server: GetDocument, ListDocuments, GetDocumentText

## Phase E — Processing service ⏳

- [ ] Redis consumer: `doc.uploaded.v1` → text fetch (Documents gRPC) → chunk
- [ ] tantivy BM25 index (tenant_id filter)
- [ ] gRPC server: BM25Search → AI Engine kullanır
- [ ] Redis publish: `doc.chunked.v1` → AI Engine consume → embed

## Phase F — Identity service ⏳

- [ ] EF Core DbContext (User, Tenant, Role, RefreshToken)
- [ ] Login endpoint (BCrypt password verify → JWT issue)
- [ ] Refresh token rotation (single-use)
- [ ] JWKS endpoint (`/.well-known/jwks.json`)
- [ ] gRPC: ValidateToken, GetUser, GetTenant

## Phase G — Realtime service ⏳

- [ ] WS upgrade handler: validate `X-Tenant-Id` / `X-User-Id` headers
- [ ] Redis subscriber: `stream.<session_id>` → fanout to client
- [ ] Presence tracking (kim hangi session'a bağlı)
- [ ] AI Engine'in stream publish'i ile uçtan uca smoke

## Phase H — Frontend ⏳

- [ ] Auth: Identity ile OIDC flow (basit form ya da NextAuth)
- [ ] Documents page: upload (drag-drop) + list + status
- [ ] Chat page: message list + SSE/WS token render + source citations
- [ ] TanStack Query data layer
- [ ] Tailwind UI polish

## Phase I — End-to-end smoke ⏳

> Kullanıcı senaryosu: PDF yükle → "X nedir?" sor → kaynak referansla cevap.

- [ ] Browser'dan login
- [ ] PDF upload → status push ile "hazır" göster
- [ ] Chat'te soru sor → token stream
- [ ] Cevapta clickable source citation (chunk → doc page)

## Phase J — Eval + production polish (optional) 💭

- [ ] RAGAS evaluation harness (retrieval + answer quality)
- [ ] File generation tools (`generate_excel`, `generate_pdf`)
- [ ] Helm chart for Kubernetes
- [ ] mTLS service-to-service
- [ ] Secret management (Vault)
- [ ] Multi-region routing

---

## Working agreements

- `main` her zaman buildable
- Her feature → ayrı branch + PR
- Commit mesaj formatı: `type(scope): message` — `feat(aiengine): real LiteLLM streaming`
- WIP yok — küçük ama anlamlı commit'ler
- ADR yaz, **then** code (mimari değişiklikler için)
- Test'i yaz, **then** PR merge (özellikle agent/tool/auth)
