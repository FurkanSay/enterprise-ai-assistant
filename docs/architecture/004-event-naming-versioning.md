# ADR-004: Event Naming & Versioning (Redis Streams)

**Status:** Accepted
**Date:** 2026-05-26
**Related:** ADR-003 (gRPC contracts)

## Context

Async event bus için Redis Streams seçildi (ADR pending: Redis vs Kafka). Event şemaları ve isimlendirme stratejisi netleştirilmeli. Üç problem:

1. **Naming inconsistency:** `doc-uploaded` vs `document.created` vs `documents_created`
2. **Versioning:** Schema değiştiğinde geri uyumluluğu nasıl korurum?
3. **Discovery:** Yeni geliştirici "hangi event'ler var" sorusunu nasıl cevaplar?

## Decision

### Naming convention

**Format:** `<domain>.<noun>.<verb-past>.<version>`

```
doc.uploaded.v1
doc.parsed.v1
doc.chunked.v1
doc.embedded.v1
doc.ready.v1

session.started.v1
session.ended.v1

tool.called.v1
tool.completed.v1

llm.token.streamed.v1
```

| Bölüm | Anlam | Örnek |
|---|---|---|
| `<domain>` | Mantıksal alan | `doc`, `session`, `tool`, `user`, `llm` |
| `<noun>` | Olay öznesi | `uploaded`, `chunked` (genelde noun = verb-past) |
| `<verb-past>` | Olmuş eylem (event, command değil) | `created`, `updated`, `deleted`, `ready` |
| `<version>` | Schema version | `v1`, `v2` |

### Event payload yapısı (zorunlu envelope)

Her event aynı envelope içinde:

```json
{
  "event_id": "01HQXY...",            // ULID, idempotency için
  "event_type": "doc.ready.v1",       // routing için
  "event_version": 1,                 // semantic version (path ile redundant ama explicit)
  "occurred_at": "2026-05-26T10:00:00.000Z",
  "tenant_id": "tnt_...",             // mandatory (cross-tenant routing'i engeller)
  "trace_id": "abc123...",            // W3C trace propagation
  "correlation_id": "req_...",        // request chain
  "actor": {                          // kim tetikledi
    "type": "user" | "system" | "service",
    "id": "...",
    "service_name": "documents"
  },
  "data": {                           // event-specific payload
    "doc_id": "...",
    "minio_object_key": "...",
    "size_bytes": 123456,
    "checksum_sha256": "..."
  }
}
```

### Versioning kuralları

| Senaryo | Aksiyon |
|---|---|
| **Yeni opsiyonel field ekle** | Aynı version'da (`v1`) — geri uyumlu |
| **Field tipi değişti (string → int)** | Yeni version (`v2`) — eski consumer kırılır |
| **Required field silindi** | Yeni version (`v2`) |
| **Field semantiği değişti** | Yeni version (`v2`) — adlandırma yetmez |

Eski version **6 ay** boyunca **paralel publish edilir**:
- Documents servisi hem `doc.ready.v1` hem `doc.ready.v2` basar
- AI Engine v2'ye geçince v1 publish kaldırılır
- Consumer migration window kontrollü

### Stream/topic mapping (Redis)

Redis Streams'te 1 stream = N event type karışık tutulabilir, ama biz **1 stream = 1 event type** yapıyoruz (basit consumer):

```
Redis stream key      Event type
─────────────────────────────────────────
stream:doc.uploaded   doc.uploaded.v1
stream:doc.parsed     doc.parsed.v1
stream:doc.chunked    doc.chunked.v1
stream:doc.embedded   doc.embedded.v1
stream:doc.ready      doc.ready.v1
stream:session.*      ...
stream:tool.*         ...
```

### Consumer groups

Her servis-event çifti için ayrı consumer group:

```
stream:doc.uploaded → consumer_group: processing-svc
stream:doc.chunked  → consumer_group: aiengine-svc
stream:doc.ready    → consumer_group: realtime-svc
```

Birden fazla instance scale ederken aynı consumer group'a join → load balance otomatik.

### Idempotency

Her consumer **event_id**'yi idempotency key olarak kullanır:

```python
# pseudo
async def handle_doc_chunked(event):
    if await redis.set(f"idem:{event.event_id}", "1", nx=True, ex=86400):
        # ilk işleme
        await process(event)
        await stream.ack(event)
    else:
        # daha önce işlenmiş — sadece ack
        await stream.ack(event)
```

## Consequences

### Positive
- **Predictable URL/topic patterns.** Yeni event eklerken pattern net.
- **Idempotency built-in.** Consumer at-least-once garantili.
- **Trace context propagation.** Her event `trace_id` taşıyor → distributed trace kopmuyor.
- **Tenant safety.** Her event `tenant_id` taşıyor → consumer cross-tenant'a yanlışlıkla yayamaz.
- **Versioning explicit.** Schema evrim disiplinli.

### Negative
- **Envelope overhead.** Her event ~200 byte boilerplate.
  - **Mitigation:** Redis Streams sıkıştırma yok ama JSON gzip yapılabilir. Şu boyutta endişe değil.
- **Discoverability hala manuel.** "Hangi event'ler var?" sorusunu dökümantasyondan cevaplıyoruz.
  - **Mitigation:** Future — AsyncAPI spec dosyası üret, schema registry.

### Neutral
- Kafka'ya geçiş kolay (Streams API benzer pattern destekler).

## Style guide (özet)

```
✅ DOĞRU
  doc.uploaded.v1
  user.password.changed.v1
  llm.token.streamed.v1
  
❌ YANLIŞ
  uploadDoc           (camelCase, command-style)
  document_uploaded   (snake_case yerine dot)
  Documents.Uploaded  (PascalCase)
  document.upload.v1  (verb present tense, command-like)
```

### Event vs Command
- **Event** (past tense): "doc.uploaded.v1" — bir şey **oldu**. Multiple consumer dinleyebilir.
- **Command** (imperative): "process_document" — bir şeyin **yapılmasını** istiyorsun. Single consumer. **Bu projede komut yok** — komutlar sync gRPC.

## Reddedilen alternatifler

### A) Tek stream, event_type filtering
- **Lehte:** Daha az stream.
- **Aleyhte:** Consumer her event'i okur, ilgisizleri filter — wasted CPU.
- **Sebep red:** Stream başına consumer group daha temiz.

### B) Kafka
- **Lehte:** Daha olgun event sourcing, retention politikaları.
- **Aleyhte:** Operasyonel ağır (ZK veya KRaft), bu projenin ölçeği için over-engineer.
- **Sebep red:** Redis Streams MVP için yeterli; Kafka'ya geçiş kolay (interface aynı).

### C) RabbitMQ
- **Lehte:** Exchange/binding flexibility.
- **Aleyhte:** Stream replay yok (consumer offset kavramı zayıf).
- **Sebep red:** Event sourcing-vari replay capability istiyoruz (Streams XRANGE).

## References

- Redis Streams docs: https://redis.io/docs/data-types/streams/
- CloudEvents spec (envelope ilham): https://cloudevents.io/
- AsyncAPI (gelecek): https://www.asyncapi.com/
