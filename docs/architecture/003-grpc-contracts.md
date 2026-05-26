# ADR-003: gRPC Contracts as Source of Truth (`protos/` + buf)

**Status:** Accepted
**Date:** 2026-05-26
**Related:** ADR-001

## Context

5 servis × 5 farklı dil = 5 farklı tipte tip sistemi. Servisler arası iletişimde "hangi field ne tipte?" sorusu **typed contract** olmadan kaybolur:
- Documents (Java) — `LocalDateTime`
- AI Engine (Python) — `datetime`
- Processing (Rust) — `chrono::DateTime<Utc>`
- Realtime (TS) — `Date | string`
- Gateway (C#) — `DateTime`

Bu farklı tiplerin birbirini "doğru" anladığını garanti edecek bir **kanonik tanım** lazım. Aksi takdirde:
- Java tarafında `2026-05-26T10:00:00` → Python'da microsecond kaybı
- TS'de "Date string" → Rust'ta parse hatası
- Field rename'i 5 yerden manuel takip

## Decision

**Protobuf + gRPC, `protos/` klasöründe kanonik kontrat. `buf` CLI ile lint, breaking-change, code-gen.**

### Klasör yapısı

```
protos/
├── buf.yaml                 ← workspace config
├── buf.gen.yaml             ← code-gen plugins (5 dil)
├── buf.lock                 ← dependency lock
├── common/v1/
│   ├── tenant.proto         ← TenantContext (her RPC'de header)
│   ├── error.proto          ← Common error shape
│   └── pagination.proto
├── documents/v1/
│   └── documents.proto      ← DocumentsService (gRPC)
├── processing/v1/
│   └── processing.proto     ← ProcessingService (BM25Search, Chunk)
├── aiengine/v1/
│   └── aiengine.proto       ← AiEngineService (rare; mostly REST)
└── identity/v1/
    └── identity.proto       ← IdentityService (validate token)
```

### Versioning kuralı

| Kural | Örnek |
|---|---|
| Package: `<service>.v<n>` | `documents.v1` |
| Filename: aynı paket adı | `documents.proto` |
| Breaking change → `v2` paket ekle, `v1`'i deprecate (silmeyin 6 ay) | `documents.v2` |
| Field numarası **asla** değişmez | Yeni field → yeni numara, eski field → reserved |

### Code-gen flow

```bash
# 1. proto dosyasını düzenle
vi protos/documents/v1/documents.proto

# 2. Lint
make proto-lint                    # cd protos && buf lint

# 3. Breaking-change check (PR'da CI'da otomatik)
make proto-breaking                # cd protos && buf breaking --against ".git#branch=main"

# 4. Generate stubs
make proto                         # cd protos && buf generate
# → libs/generated/dotnet/, java/, rust/, python/, typescript/

# 5. Her servis stub'ı kendi build sisteminde import eder
#    Java: Maven dependency  → libs/generated/java
#    Python: editable install → libs/generated/python
#    Rust: path dependency   → libs/generated/rust
#    .NET: ProjectReference  → libs/generated/dotnet
#    TS: workspace import    → libs/generated/typescript
```

### buf.gen.yaml örnek

```yaml
version: v2
plugins:
  - remote: buf.build/protocolbuffers/csharp
    out: ../libs/generated/dotnet
  - remote: buf.build/grpc/csharp
    out: ../libs/generated/dotnet
  - remote: buf.build/protocolbuffers/java
    out: ../libs/generated/java
  - remote: buf.build/grpc/java
    out: ../libs/generated/java
  - remote: buf.build/community/neoeinstein-prost
    out: ../libs/generated/rust
  - remote: buf.build/community/neoeinstein-tonic
    out: ../libs/generated/rust
  - remote: buf.build/protocolbuffers/python
    out: ../libs/generated/python
  - remote: buf.build/grpc/python
    out: ../libs/generated/python
  - remote: buf.build/community/connectrpc-es
    out: ../libs/generated/typescript
```

## Consequences

### Positive
- **Tek source of truth.** Field rename = 1 satır + code-gen.
- **Compile-time safety.** Tip uyuşmazlığı build time'da yakalanır.
- **Breaking-change diktatörlüğü.** `buf breaking` CI'da fail eder → istemeden API kıramazsın.
- **Otomatik dokümantasyon.** `buf format` + `buf push` ile schema registry'ye gönderilebilir.
- **gRPC + REST birlikte.** gRPC-Gateway plugin ile REST endpoint'leri de gen edilir (sonra ekleyebiliriz).
- **Binary efficient.** JSON yerine binary protobuf — serialization 5-10x hızlı, payload 3-5x küçük.

### Negative
- **buf CLI dev makinesinde gerekli.** Toolchain dependency.
  - **Mitigation:** `make proto` Docker image ile alternatif (toolchain Docker içinde).
- **Generated kodu commit etmek vs etmemek tartışması.**
  - **Karar:** **Commit ediyoruz** (`libs/generated/` git'te). Sebep: CI bağımsızlığı, IDE auto-complete, code review'da görünürlük.
  - Trade-off: PR'lar şişer (mitigated: `.gitattributes` linguist-generated).
- **Proto öğrenme eğrisi.** field numarası, reserved, oneof, optional kuralları.
  - **Mitigation:** Style guide `protos/STYLE.md` + buf lint enforces.

### Neutral
- gRPC sadece sync request/response. Streaming gRPC mümkün ama bu projede pub/sub için Redis Streams kullanıyoruz.

## Style guide (özet)

```protobuf
// Doğru
message Document {
  string id = 1;
  string tenant_id = 2;
  string title = 3;
  google.protobuf.Timestamp created_at = 4;
}

// YANLIŞ (snake_case değil, plural değil)
message Documents {
  string docId = 1;        // ← camelCase yasak
  string TenantId = 2;     // ← PascalCase yasak
}
```

Kurallar (buf lint enforced):
- Mesaj adları **PascalCase**, field'lar **snake_case**, enum value'lar **SCREAMING_SNAKE_CASE**
- Her field açıklayıcı yorum (LLM'in tool description benzeri — kullanan dev için bilgi)
- Asla `required` kullanma (proto3'te zaten yok); semantic required kontrolü application'da
- `oneof` zorunlu choice için
- Nested mesajları sınırla (deep nesting okumayı zorlaştırır)

## Reddedilen alternatifler

### A) OpenAPI / REST only
- **Lehte:** Browser'dan direkt erişim, swagger UI, JSON insanlar için okunabilir.
- **Aleyhte:** Tip sistemi gevşek, runtime validation, payload şişman.
- **Sebep red:** Servisler arası iletişim için gRPC daha güçlü. **Browser-facing REST var** (Gateway → AI Engine), ama servisler arası gRPC.

### B) GraphQL
- **Lehte:** Frontend için esnek query.
- **Aleyhte:** Servisler arası overkill. N+1 problem federated GraphQL ile.
- **Sebep red:** Doğru kullanım alanı browser ↔ BFF; servisler arası değil.

### C) JSON Schema / AsyncAPI
- **Lehte:** OpenAPI ekosistemi.
- **Aleyhte:** Code-gen kalitesi protobuf kadar olgun değil.
- **Sebep red:** protobuf endüstri standart.

## References

- buf docs: https://buf.build/docs/
- gRPC docs: https://grpc.io/docs/
- Protobuf style guide: https://protobuf.dev/programming-guides/style/
