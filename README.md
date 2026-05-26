<div align="center">

# Enterprise AI Assistant

A multi-tenant retrieval-augmented AI assistant platform, built as a polyglot microservices monorepo across **C#, Java, Rust, Python, and TypeScript**.

[![Phase](https://img.shields.io/badge/phase-A%20Skeleton-success?style=flat-square)](./ROADMAP.md)
[![Next](https://img.shields.io/badge/next-B%20Code--gen%20%26%20DB-blue?style=flat-square)](./ROADMAP.md#phase-b--code-gen--db-foundation-)
[![License](https://img.shields.io/badge/license-MIT-yellow?style=flat-square)](./LICENSE)

![.NET 10](https://img.shields.io/badge/.NET-10-512BD4?logo=dotnet&logoColor=white&style=flat-square)
![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white&style=flat-square)
![Rust](https://img.shields.io/badge/Rust-stable-CE422B?logo=rust&logoColor=white&style=flat-square)
![Python 3.12](https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white&style=flat-square)
![TypeScript 5](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white&style=flat-square)
![Next.js 15](https://img.shields.io/badge/Next.js-15-000?logo=nextdotjs&logoColor=white&style=flat-square)

![Postgres](https://img.shields.io/badge/Postgres-16%20%2B%20pgvector-336791?logo=postgresql&logoColor=white&style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white&style=flat-square)
![Qdrant](https://img.shields.io/badge/Qdrant-latest-DC244C?style=flat-square)
![MinIO](https://img.shields.io/badge/MinIO-S3-C72E49?logo=minio&logoColor=white&style=flat-square)
![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-traces-425CC7?logo=opentelemetry&logoColor=white&style=flat-square)
![gRPC](https://img.shields.io/badge/gRPC-buf-244C5A?logo=grpc&logoColor=white&style=flat-square)

</div>

> Phase A (skeleton) is complete. Service business logic is being filled in phase by phase. Progress is tracked in [ROADMAP.md](./ROADMAP.md); `main` is kept buildable on every commit.

---

## Overview

The platform lets a tenant upload internal documents (PDF, DOCX) and query them through a retrieval-augmented generation pipeline backed by an LLM provider. Tenant data is isolated at every layer: JWT claims, request-scoped context, Postgres row-level security, vector store filters, object storage scoping, and audit logging.

Each microservice is written in the language best suited to its domain:

- **Rust** — chunking and BM25 indexing (CPU-bound, memory-safe handling of untrusted documents)
- **Python** — AI engine (mature ML ecosystem: transformers, sentence-transformers, LiteLLM, Qdrant client)
- **Java** — document ingestion (transactional file operations, Apache Tika, JPA)
- **C#** — API gateway and identity (YARP reverse proxy, Clean Architecture)
- **TypeScript / Node** — WebSocket fanout (event-loop concurrency for high-throughput streaming)

## Design decisions

- Five language runtimes in a single monorepo; each service uses the toolchain native to its concern.
- Four communication patterns are exercised end-to-end: synchronous REST, synchronous gRPC, asynchronous pub/sub over Redis Streams, and server push via WebSocket and SSE.
- Tenant isolation uses defense in depth: JWT → request context → Postgres `FORCE ROW LEVEL SECURITY` → Qdrant payload filter → MinIO path scoping → append-only audit log.
- Observability is OpenTelemetry-first; traces, metrics, and logs flow through an OTel collector to Jaeger and Prometheus.
- API contracts live under `protos/` as the source of truth; stubs for all five languages are code-generated via `buf` with lint and breaking-change checks in CI.
- The RAG pipeline combines dense retrieval (Qdrant) with sparse retrieval (tantivy BM25), fused via Reciprocal Rank Fusion and refined by a cross-encoder reranker.
- The agent loop uses a three-source tool registry (built-in, plugin, runtime) with per-tenant permission policies and pre/post hooks for audit and redaction. Reference notes distilled from a deep read of Claude Code's internals are kept under [docs/claw-learnings/](./docs/claw-learnings/).

## Architecture

```mermaid
graph TB
    Browser["Browser<br/>Next.js"]
    GW["Gateway<br/>C# / YARP"]
    ID["Identity<br/>C#"]
    DOC["Documents<br/>Spring Boot"]
    PROC["Processing<br/>Rust"]
    AI["AI Engine<br/>FastAPI"]
    RT["Realtime<br/>NestJS"]

    PG[("Postgres<br/>+ pgvector + RLS")]
    REDIS[("Redis Streams")]
    MINIO[("MinIO")]
    QDRANT[("Qdrant")]

    Browser -.->|"REST + JWT"| GW
    Browser <-.->|"WebSocket"| RT
    GW -->|REST| ID
    GW -->|REST| DOC
    GW -->|REST + SSE| AI
    DOC --> MINIO
    DOC --> PG
    DOC -.->|"doc.uploaded.v1"| REDIS
    REDIS -.-> PROC
    REDIS -.-> AI
    PROC <-->|"gRPC BM25Search"| AI
    AI --> QDRANT
    AI --> PG
    AI -.->|"llm.token.streamed.v1"| REDIS
    REDIS -.-> RT

    classDef cs fill:#512BD4,color:#fff,stroke:#3a1e98
    classDef java fill:#007396,color:#fff,stroke:#004f63
    classDef rust fill:#CE422B,color:#fff,stroke:#8e2e1d
    classDef py fill:#3776AB,color:#fff,stroke:#234567
    classDef ts fill:#3178C6,color:#fff,stroke:#1f5694
    classDef data fill:#fffae0,color:#333,stroke:#aa9
    class GW,ID cs
    class DOC java
    class PROC rust
    class AI py
    class RT ts
    class PG,REDIS,MINIO,QDRANT data
```

End-to-end ingestion and query sequence: [docs/architecture/diagrams/upload-flow.mmd](./docs/architecture/diagrams/upload-flow.mmd).

## Services

| Service | Stack | Port | Responsibility |
|---|---|---|---|
| [gateway](./services/gateway/) | C# .NET 10 / YARP | 8080 | Reverse proxy, JWT validation, Redis rate-limit, tenant header forwarding |
| [identity](./services/identity/) | C# .NET 10 (Clean Architecture) | 8081 | OAuth2 / OIDC, user and tenant CRUD, JWT issuance, JWKS, RBAC |
| [documents](./services/documents/) | Java 21 / Spring Boot 3 | 8082 | Upload, Apache Tika parse, MinIO storage, JPA, event publish |
| [processing](./services/processing/) | Rust / Axum + tantivy | 8083 | Semantic chunking (rayon), BM25 inverted index, gRPC server |
| [aiengine](./services/aiengine/) | Python 3.12 / FastAPI | 8084 | Agent loop, tool registry, RAG pipeline, LiteLLM, SSE streaming |
| [realtime](./services/realtime/) | TypeScript / NestJS + Fastify | 8085 | WebSocket gateway, Redis pub/sub fanout, token streaming |
| [frontend](./frontend/) | Next.js 15 / React 19 | 3000 | Chat UI, document upload, real-time token render |

## Communication patterns

| # | Pattern | Where it is used |
|---|---|---|
| 1 | Synchronous REST | Browser ↔ Gateway ↔ services — JSON over HTTP |
| 2 | Synchronous gRPC | AI Engine ↔ Processing — typed Protobuf contracts, binary serialization |
| 3 | Asynchronous pub/sub (Redis Streams) | Documents → Processing / AI Engine — consumer groups, replay |
| 4 | Server push (WebSocket + SSE) | Realtime → Browser — LLM token streaming, document status notifications |

## Quick start

```bash
git clone https://github.com/FurkanSay/enterprise-ai-assistant
cd enterprise-ai-assistant

cp .env.example .env
# fill in at minimum: ANTHROPIC_API_KEY=sk-ant-...

make up        # docker compose up -d --build
make health    # ping every service /health/ready
```

Endpoints:

| What | Where |
|---|---|
| Frontend | http://localhost:3000 |
| Gateway | http://localhost:8080 |
| Jaeger (distributed traces) | http://localhost:16686 |
| Grafana | http://localhost:3001 |
| MinIO console | http://localhost:9001 |

## Project structure

```
enterprise-ai-assistant/
├── README.md · ARCHITECTURE.md · ROADMAP.md · LICENSE
├── docker-compose.yml · docker-compose.override.yml · Makefile
├── protos/                    gRPC contracts (buf workspace, source of truth)
├── services/
│   ├── gateway/               C# .NET 10 / YARP
│   ├── identity/              C# .NET 10 (Domain · Application · Infrastructure · Api)
│   ├── documents/             Java Spring Boot 3
│   ├── processing/            Rust Cargo workspace (3 crates)
│   ├── aiengine/              Python FastAPI (agent · tools · rag · providers · api · core)
│   └── realtime/              TypeScript NestJS + Fastify
├── frontend/                  Next.js 15 + React 19 + Tailwind
├── libs/                      shared utilities and generated gRPC stubs
├── infra/                     Postgres init (RLS, roles, schemas), OTel, Prometheus, Grafana
├── docs/
│   ├── architecture/          ADRs and Mermaid diagrams
│   ├── claw-learnings/        reference notes from Claude Code internals analysis
│   └── mvp-tools/             tool design notes
└── .github/workflows/         per-service CI and proto-validate
```

## Status

| Phase | Description | Status |
|---|---|---|
| A | Monorepo skeleton, services, infrastructure, gRPC contracts | Done |
| B | `buf generate`, DB migrations, RLS policies live | Next |
| C | AI Engine end-to-end smoke (LiteLLM streaming) | Planned |
| D | Documents upload, Tika parse, MinIO, event publish | Planned |
| E | Processing chunker, tantivy, gRPC, Redis consumer | Planned |
| F | Identity login, refresh, JWT, JWKS | Planned |
| G | Realtime WebSocket auth, Redis pub/sub fanout | Planned |
| H | Frontend chat UI, SSE / WS | Planned |
| I | End-to-end smoke: upload PDF, ask question, cited answer | Planned |

Detailed plan and working agreements: [ROADMAP.md](./ROADMAP.md).

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Technical decisions: service responsibilities, communication patterns, multi-tenant strategy, observability, security model |
| [docs/architecture/](./docs/architecture/) | Architecture Decision Records |
| · [ADR-001 Monorepo polyglot](./docs/architecture/001-monorepo-polyglot.md) | Language selection, monorepo trade-offs |
| · [ADR-002 Shared DB + RLS](./docs/architecture/002-shared-db-rls.md) | Multi-tenant isolation strategy |
| · [ADR-003 gRPC contracts](./docs/architecture/003-grpc-contracts.md) | Proto-first workflow with `buf` |
| · [ADR-004 Event naming and versioning](./docs/architecture/004-event-naming-versioning.md) | Redis Streams event taxonomy and envelope |
| [docs/architecture/diagrams/](./docs/architecture/diagrams/) | Mermaid diagrams (high-level, upload-flow sequence) |
| [docs/claw-learnings/](./docs/claw-learnings/) | Reference notes from Claude Code internals analysis |
| [protos/](./protos/) | gRPC schemas (lint with `make proto-lint`) |

Each service also has its own README inside its folder.

## Operational details

- `make up` brings the full stack online (services and infrastructure containers).
- OpenTelemetry tracing is wired across all five services and visualized in Jaeger.
- Each service exposes `/health/live` and `/health/ready` endpoints.
- Each service has its own GitHub Actions workflow; a separate `proto-validate` workflow runs `buf lint` and breaking-change checks.
- Postgres uses `FORCE ROW LEVEL SECURITY` — the table owner cannot bypass tenant isolation.
- Dockerfiles are multi-stage with healthchecks and non-root users.
- `.env.example` is checked in; no secrets are committed.

## License

[MIT](./LICENSE)
