# Architecture Decision Records (ADRs)

Bu klasörde projenin **karar dökümanları** var. Her ADR:
- **Bir** karar konusunu derinlemesine açıklar
- **Why** sorusunu cevaplar (sadece "what" değil)
- Reddedilen alternatifleri ve sebeplerini içerir
- Tarih ve durum (Status) ile versiyonlanır

## Mevcut ADR'ler

| # | Başlık | Status | Konu |
|---|---|---|---|
| [001](./001-monorepo-polyglot.md) | Monorepo + Polyglot Microservices | Accepted | 5 dil, neden bu seçim, monorepo gerekçesi |
| [002](./002-shared-db-rls.md) | Shared Postgres + tenant_id + RLS | Accepted | Multi-tenant izolasyon stratejisi (6-katmanlı defense) |
| [003](./003-grpc-contracts.md) | gRPC Contracts as Source of Truth | Accepted | `protos/` + buf, code-gen, versioning |
| [004](./004-event-naming-versioning.md) | Event Naming & Versioning | Accepted | Redis Streams event taxonomy + envelope schema |
| [005](./005-ui-design-system.md) | UI Design System | Accepted | shadcn/ui + Claude-adjacent neutral theme + Inter/Source Serif |

## Diagrams

| Diagram | Açıklama |
|---|---|
| [high-level.mmd](./diagrams/high-level.mmd) | Tüm servisler + veri katmanı + observability — high-level mimari |
| [upload-flow.mmd](./diagrams/upload-flow.mmd) | Uçtan uca senaryo — PDF yükleme + soru sorma (sequence diagram) |

Mermaid render için: https://mermaid.live/

## Yeni ADR yazma kuralı

```markdown
# ADR-NNN: <başlık>

**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-XXX
**Date:** YYYY-MM-DD
**Related:** ADR-XXX (varsa)

## Context
Neden bu kararı vermek zorundayız? Hangi problem var?

## Decision
Ne karar verdik? (Tek paragraf öz.)
Detay alt-bölümler.

## Consequences
### Positive
### Negative
### Neutral

## Reddedilen alternatifler
Neden A değil, neden B değil?

## References
Linkler, dokümanlar.
```

ADR numarası **monotonic artar** (silmiş bile olsa); ADR-005 her zaman 4'ten sonra gelir.
