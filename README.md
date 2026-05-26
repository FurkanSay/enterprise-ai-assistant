# Enterprise AI Assistant

> **Enterprise AI Assistant Platform** — kurumsal dokümanlardan (PDF / DOCX) RAG ile soru cevaplama yapan, çok kiracılı (multi-tenant) bir SaaS. Polyglot monorepo: **C# · Java · Rust · Python · TypeScript** — beş dil ekosistemini ve dört mikroservis iletişim desenini tek projede gösterir.

[![Phase](https://img.shields.io/badge/Phase-A%20Skeleton%20%E2%9C%85-success)](./ROADMAP.md)
[![Next](https://img.shields.io/badge/Next-B%20Code--gen%20%26%20DB%20foundation-blue)](./ROADMAP.md#phase-b--code-gen--db-foundation-)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![.NET](https://img.shields.io/badge/.NET-10-512BD4?logo=dotnet)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk)
![Rust](https://img.shields.io/badge/Rust-stable-orange?logo=rust)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?logo=python)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript)

> **🚧 Bu repo açık geliştirme aşamasında.** Phase A (iskelet) tamamlandı; bir sonraki faz için yol haritası: [ROADMAP.md](./ROADMAP.md). Servisler şu an minimum çalışır iskelet; business logic faz-faz dolduruluyor.

## Bu nedir?

Şirketlerin kendi iç dokümanlarını (PDF, DOCX) yükleyip üzerinden Claude/GPT destekli RAG (Retrieval-Augmented Generation) ile soru sorabildiği çok kiracılı bir AI asistan platformu. Beş dil ekosistemindeki yetkinliği ve dört farklı mikroservis iletişim desenini (REST, gRPC, async pub/sub, WebSocket/SSE) tek projede demonstre etmek için **polyglot monorepo** olarak inşa edildi.

## Mimari

```mermaid
graph TB
    Browser["🌐 Browser<br/>Next.js Frontend"]
    GW["🟦 API Gateway<br/>C# .NET 10 + YARP"]
    ID["🟦 Identity<br/>C# .NET 10"]
    DOC["☕ Documents<br/>Java Spring Boot 3"]
    PROC["🦀 Processing<br/>Rust Axum + tantivy"]
    AI["🐍 AI Engine ⭐<br/>Python FastAPI"]
    RT["🟨 Realtime<br/>TS NestJS"]

    PG[("🐘 Postgres 16<br/>+ pgvector + RLS")]
    REDIS[("🔴 Redis 7<br/>Streams + pub/sub")]
    MINIO[("📦 MinIO<br/>Object storage")]
    QDRANT[("🧭 Qdrant<br/>Vector DB")]

    Browser -.->|"1. REST + JWT"| GW
    Browser <-.->|"4. WebSocket"| RT
    GW -->|REST| ID
    GW -->|REST| DOC
    GW -->|REST + SSE| AI
    DOC --> MINIO
    DOC --> PG
    DOC -.->|"3. publish doc.ingested"| REDIS
    REDIS -.->|consume| PROC
    REDIS -.->|consume| AI
    PROC <-->|"2. gRPC BM25"| AI
    AI --> QDRANT
    AI --> PG
    AI -.->|"publish token.stream"| REDIS
    REDIS -.->|consume| RT

    classDef gw fill:#512BD4,color:#fff
    classDef java fill:#007396,color:#fff
    classDef rust fill:#CE422B,color:#fff
    classDef py fill:#3776AB,color:#fff
    classDef ts fill:#3178C6,color:#fff
    class GW,ID gw
    class DOC java
    class PROC rust
    class AI py
    class RT ts
```

## Servisler

| # | Servis | Stack | Port | Sorumluluk |
|---|---|---|---|---|
| 1 | [gateway](services/gateway/) | C# .NET 10 / YARP | 8080 | Reverse proxy, JWT validation, rate limit, request aggregation |
| 2 | [identity](services/identity/) | C# .NET 10 | 8081 | OAuth2/OIDC, user/tenant CRUD, JWT issuance, RBAC |
| 3 | [documents](services/documents/) | Java Spring Boot 3 | 8082 | Upload, Apache Tika parse, MinIO storage, metadata, `doc.ingested` event |
| 4 | [processing](services/processing/) | Rust Axum + tantivy | 8083 | Semantic chunking, BM25 inverted index, gRPC server |
| 5 | [aiengine ⭐](services/aiengine/) | Python FastAPI | 8084 | Embeddings, Qdrant, hybrid retrieval, LiteLLM, agent loop, RAG eval |
| 6 | [realtime](services/realtime/) | TS NestJS | 8085 | WebSocket gateway, Redis pub/sub fanout, token streaming |
| — | [frontend](frontend/) | Next.js 15 | 3000 | Chat UI, doc upload, real-time token stream |

## İletişim desenleri

Mikroservis öğrenmenin asıl yeri "kaç servis var" değil, "nasıl konuşuyorlar". Bu projede 4 desen birlikte:

| # | Desen | Kullanım |
|---|---|---|
| 1 | **Synchronous REST** | Browser ↔ Gateway ↔ Servisler — JSON over HTTP |
| 2 | **Synchronous gRPC** | AI Engine ↔ Processing — typed protobuf contracts, binary serialization |
| 3 | **Async Pub/Sub** | Documents → Processing/AI Engine — Redis Streams ile consumer group, fire-and-forget |
| 4 | **Server Push** | Realtime → Browser — WebSocket + SSE ile LLM token stream |

## Hızlı başlangıç

```bash
# 1. Repo'yu klonla
git clone https://github.com/FurkanSay/enterprise-ai-assistant
cd enterprise-ai-assistant

# 2. Ortam değişkenlerini hazırla
cp .env.example .env
# .env dosyasını düzenle — en azından ANTHROPIC_API_KEY ekle

# 3. Tüm stack'i ayağa kaldır
make up

# 4. Sağlık kontrolü
make health

# Şimdi:
# Frontend:  http://localhost:3000
# Gateway:   http://localhost:8080
# Jaeger UI: http://localhost:16686  ← trace görselleştirme
# Grafana:   http://localhost:3001
```

## Dokümantasyon

| Doküman | İçerik |
|---|---|
| [ROADMAP.md](./ROADMAP.md) | Faz-faz yol haritası ve mevcut durum |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Derin teknik karar dokümanı |
| [docs/architecture/](./docs/architecture/) | ADR'ler (Architecture Decision Records) |
| [docs/claw-learnings/](./docs/claw-learnings/) | Claude Code internals analizinden çıkarılan referans notları |
| [docs/mvp-tools/](./docs/mvp-tools/) | Domain-spesifik tool tasarım notları (RAG, text-to-SQL, file gen) |
| [protos/](./protos/) | gRPC kontrat tanımları — kanonik kaynak |

## Üretim sınıfı ekler

- ✅ **Tek `make up`** ile her şey ayağa kalkıyor
- ✅ **OpenTelemetry + Jaeger** — bir request'in 5 servisten geçişini görselleştirme
- ✅ **Per-service `/health` endpoint'leri** — readiness/liveness ayrımı
- ✅ **Service başına ayrı CI** — GitHub Actions matrix
- ✅ **Service başına ayrı README** + kök ARCHITECTURE.md
- ✅ **Postgres RLS** — tenant izolasyonu DB seviyesinde garanti
- ✅ **Protobuf kontratları** — `buf` ile lint + breaking-change kontrol
- ✅ **Testcontainers (Java)**, **integration tests** her dilde

## Lisans

MIT
