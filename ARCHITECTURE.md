# Architecture

Bu doküman teknik kararları derinlemesine açıklar. Üst seviye genel bakış için [README.md](./README.md).

## İçindekiler

- [Tasarım prensipleri](#tasarım-prensipleri)
- [Servis sorumlulukları](#servis-sorumlulukları)
- [İletişim desenleri](#iletişim-desenleri)
- [Multi-tenant izolasyon](#multi-tenant-izolasyon)
- [Veri katmanı](#veri-katmanı)
- [Observability](#observability)
- [Güvenlik modeli](#güvenlik-modeli)
- [RAG pipeline](#rag-pipeline)
- [Agent loop (AI Engine)](#agent-loop-ai-engine)

## Tasarım prensipleri

1. **Polyglot bilinçli seçim, gösteriş değil.** Her servisin dili, sorumluluğunun doğasına göre seçildi:
   - CPU-bound + memory safety (chunking, parse) → **Rust**
   - I/O-bound + ekosistem (AI/ML) → **Python**
   - Enterprise transactional (file ops) → **Java**
   - Performance + ekosistem (gateway) → **C#**
   - Event loop verimliliği (WebSocket fanout) → **Node/TS**

2. **Kontratlar koddan önce.** Servisler arası iletişim `protos/` altında protobuf ile tanımlı. Her dilin client/server stub'ları code-gen ile üretilir. `buf lint` + `buf breaking` CI'da çalışır.

3. **Shared Postgres + tenant_id + RLS.** Database-per-service ortodoksisi yerine, operasyonel basitlik için tek Postgres. Ama tenant izolasyonu DB seviyesinde **Row-Level Security** ile zorunlu — application code bug'ı bile cross-tenant data leak'e yol açamaz.

4. **Async events for fire-and-forget, gRPC for queries.** Bir doküman yüklenince "bu doc geldi" event'i Redis Streams'e basılır → consumer group'lar consume eder. Ama AI Engine'in "BM25 sonucu ne?" sorusu gRPC ile sync — sonucu beklemesi gerekiyor.

5. **OpenTelemetry her serviste birinci sınıf.** Trace, metric, log — üçü de OTel ile. Vendor lock-in yok (Jaeger lokal dev, prod'da Tempo/Datadog değişebilir).

6. **Her servis bağımsız deploy edilebilir.** Monorepo ama her servisin kendi Dockerfile, CI pipeline, README ve test suite'i var.

## Servis sorumlulukları

### 🟦 Gateway (C# .NET 10 / YARP)
**Yapar:** JWT signature validation, rate limit (Redis-backed), request routing (YARP dynamic config), CORS, request aggregation (BFF pattern).
**Yapmaz:** Business logic. Auth karar verme (Identity'ye sorar).
**Neden YARP:** Microsoft purpose-built reverse proxy. Native AOT ile Go'ya yakın latency. Dynamic routing config.

### 🟦 Identity (C# .NET 10)
**Yapar:** User/tenant/role CRUD, OAuth2/OIDC, JWT issuance (RS256), refresh token rotation, RBAC policy.
**Yapmaz:** Document-related yetkilendirme (Documents kendi RBAC layer'ını uygular).
**Mimari:** Clean Architecture 4 katman — Domain, Application (MediatR/CQRS), Infrastructure, Api.

### ☕ Documents (Java Spring Boot 3)
**Yapar:** PDF/DOCX upload, Apache Tika ile parse, MinIO'ya kalıcı saklama, Postgres'e metadata, JPA transactional file ops, Bean Validation, "doc.ingested" event publish.
**Yapmaz:** Chunking (Processing servisi), embedding (AI Engine), semantic understanding.
**Neden Spring:** Transactional file ops + karmaşık metadata + validation kuralları için en olgun ekosistem. Avrupa enterprise iş ilanlarının yarısı bunu ister.

### 🦀 Processing (Rust Axum + tantivy)
**Yapar:** Documents'ten gelen ham metni semantik chunk'lara böler (paragraph + sentence boundary), tantivy ile BM25 inverted index oluşturur, AI Engine'e gRPC ile BM25 search arayüzü sunar.
**Yapmaz:** Embedding (embedding model çağrıları), reranking, LLM iletişim.
**Neden Rust:** Chunking ve PDF parse CPU-bound — rayon ile paralelizm. Bozuk/malicious PDF'lerde memory safety. tantivy = Rust'ın Lucene'i, endüstri seviyesi BM25.

### 🐍 AI Engine ⭐ (Python FastAPI)
**Yapar:** Embedding (sentence-transformers veya Voyage AI), Qdrant'a vector write + dense similarity search, hybrid retrieval (Qdrant dense + Processing BM25 = Reciprocal Rank Fusion), reranking (cross-encoder veya Cohere), LLM orchestration (LiteLLM ile multi-provider), SSE token streaming, **agent loop + tool registry**, RAGAS evaluation.
**Yapmaz:** Dosya storage (Documents), chunking (Processing), websocket (Realtime).
**Neden Python:** Modern AI/ML ekosistemi Python-first. CV'de en kritik kod buradadır.

### 🟨 Realtime (TS NestJS)
**Yapar:** WebSocket gateway, Redis pub/sub'tan token stream consume + frontend'e fanout, doküman işleme status notifications, presence tracking.
**Yapmaz:** LLM iletişim (sadece AI Engine'in bastığı stream'i ileti).
**Neden Node:** Event loop verimliliği — binlerce eşzamanlı WebSocket için thread tutmuyor. NestJS Angular-benzeri DI/module yapısı ile Spring Boot bilen birine familiar.

## İletişim desenleri

### Senaryo: Doküman yükleme → soru sorma (uçtan uca)

```
[1] UPLOAD (sync REST)
  Browser --[POST /api/v1/documents]--> Gateway --[POST /documents]--> Documents
  Documents: MinIO write + Postgres metadata + hash
  Documents --[XADD doc.ingested.v1]--> Redis Streams
  Documents --[202 Accepted + job_id]--> Browser

[2] INGESTION (async pub/sub)
  Processing --[XREADGROUP doc.ingested]--> processes chunks + BM25
  Processing --[XADD doc.chunked.v1]--> Redis Streams
  AI Engine --[XREADGROUP doc.chunked]--> embeds + writes Qdrant
  AI Engine --[XADD doc.ready.v1]--> Redis Streams

[3] STATUS PUSH (server push)
  Realtime --[XREADGROUP doc.ready]--> WebSocket --> Browser
  Browser: "Doküman hazır" toast notification

[4] QUESTION (sync REST + SSE + gRPC)
  Browser --[POST /api/v1/chat]--> Gateway --[POST /chat]--> AI Engine
  AI Engine: agent loop başlatır
    LLM --[tool_use: doc_search]--> AI Engine
    AI Engine: embed query
    AI Engine --[Qdrant search]--> top-20 dense
    AI Engine --[gRPC BM25Search]--> Processing --> top-20 sparse
    AI Engine: RRF + rerank → top-3
    AI Engine --[tool_result]--> LLM
    LLM: token-by-token answer
  AI Engine --[PUBLISH stream.<session_id>]--> Redis
  Realtime --[SUBSCRIBE]--> WebSocket --[token]--> Browser
```

### Pattern seçim mantığı

| Pattern | Ne zaman |
|---|---|
| **Sync REST (JSON)** | Browser ile her iletişim, basit query/command |
| **Sync gRPC (protobuf)** | Servisler arası, latency-kritik, typed contract gereken |
| **Async pub/sub (Redis Streams)** | Fire-and-forget, multi-consumer, geri dönüşü beklemiyorsun |
| **Server push (WS/SSE)** | LLM streaming, real-time updates, kullanıcı bekliyor |

## Multi-tenant izolasyon

**Strateji:** Shared Postgres + `tenant_id` column + Postgres Row-Level Security.

### Katmanlar (defense-in-depth)

```
┌─ Katman 1: JWT'de tenant_id claim ─────────────────────────────┐
│ Identity, JWT'ye `tenant_id` ekler. Gateway validate eder.     │
└─────────────────────────────────────────────────────────────────┘
┌─ Katman 2: Service-level tenant context ────────────────────────┐
│ Her servis JWT'den tenant_id okur, request context'e koyar.    │
│ Her DB query'sinde `SET app.current_tenant_id = X` çağrısı.    │
└─────────────────────────────────────────────────────────────────┘
┌─ Katman 3: Postgres RLS policy ─────────────────────────────────┐
│ Her tabloda tenant_id column + RLS policy:                     │
│ CREATE POLICY ... USING (tenant_id = current_setting(...))     │
│ Application bug'ı bile cross-tenant data göremez.              │
└─────────────────────────────────────────────────────────────────┘
┌─ Katman 4: Qdrant payload filter ───────────────────────────────┐
│ Vector search'te zorunlu filter:                               │
│ filter: { must: [{ key: "tenant_id", match: { value: X }}]}    │
└─────────────────────────────────────────────────────────────────┘
┌─ Katman 5: MinIO bucket per tenant veya prefix ─────────────────┐
│ Object storage path: <tenant_id>/<doc_id>/<filename>           │
│ Service-account credentials tenant-scoped IAM policy.          │
└─────────────────────────────────────────────────────────────────┘
┌─ Katman 6: Audit log ───────────────────────────────────────────┐
│ Her tool çağrısı, her DB query, her doc access:                │
│ tenant_id + user_id + timestamp + payload_hash kaydedilir.     │
└─────────────────────────────────────────────────────────────────┘
```

Detaylı şema ve policy SQL: [infra/postgres/init/](./infra/postgres/init/)

## Veri katmanı

| Store | Kullanan servisler | Amaç |
|---|---|---|
| **Postgres 16 + pgvector** | Identity, Documents, AI Engine | Relational data + (opsiyonel) embeddings backup |
| **Qdrant** | AI Engine | Production vector search |
| **MinIO** | Documents | Object storage (PDF, DOCX, generated files) |
| **Redis 7** | Tüm servisler | Streams (event bus), pub/sub (token stream), rate limit, session cache |

### Schema-per-service (shared DB içinde)

```sql
-- Tek Postgres database
CREATE SCHEMA identity_schema;   -- Identity servisi
CREATE SCHEMA documents_schema;  -- Documents servisi
CREATE SCHEMA aiengine_schema;   -- AI Engine servisi

-- Her servis sadece kendi schema'sına yazar
GRANT USAGE ON SCHEMA identity_schema TO identity_user;
-- (cross-schema okuma yok by default)
```

## Observability

### Distributed tracing
- Her servis OTel SDK ile span üretir
- W3C Trace Context header propagation (servisler arası)
- OTLP exporter → otel-collector → Jaeger
- 5-servis trace'i tek Jaeger UI'da

### Metrics
- Her servis Prometheus endpoint expose (`/metrics`)
- RED metrics (Rate, Errors, Duration)
- Custom business metrics: tokens_used, docs_ingested, rag_recall_at_k

### Logs
- Structured JSON logging (her dilde)
- `trace_id` her log line'da → trace ile correlate
- Aggregation lokal dev'de yok, prod'da Loki/ELK

### Health endpoints
- `/health/live` — process ayakta mı (Kubernetes liveness)
- `/health/ready` — bağımlılıklara erişim var mı (readiness)

## Güvenlik modeli

| Layer | Mekanizma |
|---|---|
| Edge | Gateway: JWT signature validation, rate limit, CORS |
| Service-to-service | mTLS (prod), service mesh (opsiyonel) |
| Authorization | JWT claims (user_id, tenant_id, roles) + per-service RBAC |
| Data access | Postgres RLS + Qdrant payload filter + MinIO IAM |
| Secrets | `.env` dev, prod'da Vault/AWS Secrets Manager |
| LLM access | Tenant-scoped quota + content moderation hooks |
| Audit | Tool calls + document access logged with trace_id |

## RAG pipeline

Detay: [docs/mvp-tools/](./docs/mvp-tools/) altında ayrı not.

```
INGESTION (Documents → Processing → AI Engine)
  Parse (Tika) → Chunk (semantic, 500-1000 token + overlap)
  → BM25 index (tantivy) + Embed (sentence-transformers) → Qdrant

RETRIEVAL (AI Engine)
  Query → embed → Qdrant dense search (top-20)
          ↘ gRPC → Processing BM25 search (top-20)
  → Reciprocal Rank Fusion → top-5
  → Rerank (cross-encoder) → top-3

GENERATION (AI Engine)
  Context (top-3 chunks) + query → LLM (LiteLLM, multi-provider)
  → Token stream → Redis pub/sub → Realtime → Browser
```

## Agent loop (AI Engine)

Claude Code'un `run_turn()` deseni AI Engine'de Python implementasyonu olarak yaşıyor.

```
loop:
  1. LLM call (LiteLLM)
  2. Parse response (text vs tool_use blocks)
  3. If no tool_use → break
  4. For each tool_use:
     a. PreToolUse hook (audit log + tenant policy)
     b. Permission check (active_mode >= required_mode + RLS check)
     c. Execute tool (in-process veya gRPC ile başka servise)
     d. PostToolUse hook (output redaction + audit)
     e. Append tool_result to history
  5. Iteration cap (tenant-based)
```

Tool katmanı:
- **In-process tools** (AI Engine içinde): `web_fetch`, `web_search`, `doc_search` (Qdrant + Processing gRPC), `db_query` (Postgres read-only)
- **gRPC delegated tools** (başka servise rota): `generate_excel`, `generate_pdf` (Documents) — *(MVP sonrası)*
- **MCP tools** (opsiyonel): Üçüncü taraf entegrasyonlar

Referans olarak Claude Code internals analizinden çıkarılan notlar: [docs/claw-learnings/](./docs/claw-learnings/)
