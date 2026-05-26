# 🦀 Processing

> **Rust + Axum + tantivy + tonic** — CPU-bound chunking and BM25 inverted index. The "data plane" of RAG retrieval.

## Sorumluluk

- Documents servisinden gelen `doc.uploaded.v1` event'ini consume et
- Apache Tika benzeri ham metni semantic chunk'lara böl (paragraph + sentence boundary)
- `tantivy` ile **tenant-scoped** BM25 inverted index oluştur / güncelle
- AI Engine'e **gRPC `BM25Search`** arayüzü sun (hybrid retrieval'ın sparse yarısı)
- İşlem bittiğinde `doc.chunked.v1` event'i publish et

## Sorumluluk dışı

- Embedding generation (AI Engine — Python sentence-transformers)
- Vector search (Qdrant — AI Engine üzerinden)
- Doküman parse / OCR (Documents — Apache Tika)
- LLM iletişim

## Workspace yapısı

```
services/processing/
├── Cargo.toml                          ← workspace root
└── crates/
    ├── chunker/                        ← pure library: semantic chunking
    │   └── src/lib.rs
    ├── bm25-index/                     ← tantivy wrapper, tenant-scoped
    │   └── src/lib.rs
    └── processing-server/              ← binary: gRPC + HTTP + consumer
        └── src/
            ├── main.rs                 ← entry point
            ├── config.rs               ← env-driven Config
            ├── telemetry.rs            ← OTel setup
            ├── health.rs               ← /health/live, /health/ready
            ├── grpc.rs                 ← ProcessingService impl
            └── consumer.rs             ← Redis Streams consumer
```

**Crate ayrımı bilinçli (SRP):**
- `chunker` — pure function library, sıfır I/O, kolayca test edilir
- `bm25-index` — tantivy detayını kapsüller, schema kararı tek yerde
- `processing-server` — bunları orkestre eden binary

## Çalıştır

```bash
# Test
cd services/processing
cargo test --workspace

# Dev (local Redis + index dizini)
REDIS_URL=redis://localhost:6379 \
INDEX_PATH=./.tantivy-index \
cargo run --bin processing-server

# Docker
make up
docker compose logs -f processing
```

## Tasarım kararları

### Why Rust
Chunking ve BM25 indeksleme **CPU-bound**. Java/Python burada cost katar. Rust + rayon ile data-parallel chunking native hıza yakın.

### Why tantivy
"Rust'ın Lucene'i". Production-tested (Quickwit kullanıyor). BM25, faceting, fuzzy — hepsi var. **Single-writer constraint** unutma: write işlemleri tek thread'den.

### Why Bm25Index API'si tenant_id zorunlu
Cross-tenant leak'i imkânsız kılmak için. Çağıran tarafa unutma fırsatı vermeyiz — fonksiyon imzası **zorla** belirtiyor.

### Why ayrı crate'ler
- `chunker` saf algoritma → test kolay (sahte data ver, output kontrol et)
- `bm25-index` storage detayı → swap edilebilir (yarın Lucene'e geçilse ayrı crate'i değiştir)
- `processing-server` orchestration → sadece bağlama
