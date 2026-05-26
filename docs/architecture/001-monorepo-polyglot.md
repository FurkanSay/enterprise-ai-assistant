# ADR-001: Monorepo + Polyglot Microservices

**Status:** Accepted
**Date:** 2026-05-26
**Decision makers:** Project author (portfolio decision)

## Context

Proje aynı anda iki hedefe hizmet ediyor:
1. **Çalışan ürün** — multi-tenant kurumsal AI asistan (RAG, doküman Q&A)
2. **CV / portföy gösterimi** — backend mühendisliği yetkinliklerini somut ölçek üzerinden göster

Bir tek dilde monolith yazılsaydı (1) için yeterli olurdu, ama (2) için fakir kalırdı. Beş dil ve dört iletişim deseni tek projede göstermek için çoklu servis gerekiyor — ama "5 küçük repo" yönetim baş ağrısı verir.

## Decision

**Monorepo + polyglot mikroservis** mimarisi seçildi.

### Servis → Dil eşleştirmesi (bilinçli)

| Servis | Dil | Sorumluluk doğası | Neden bu dil |
|---|---|---|---|
| Gateway | C# .NET 10 | Reverse proxy, performance-critical | YARP purpose-built; native AOT ile Go'ya yakın latency |
| Identity | C# .NET 10 | Enterprise auth, Clean Architecture | .NET ekosistemi, Identity Server geleneği |
| Documents | Java Spring Boot 3 | Transactional file ops, validation | Spring ekosistemi enterprise file/metadata için en olgun; AB pazarında Spring talebi yüksek |
| Processing | Rust Axum | CPU-bound chunking, memory safety | rayon paralelizm + malicious PDF güvenliği; tantivy = endüstri seviyesi BM25 |
| AI Engine | Python FastAPI | AI/ML ekosistemi | PyTorch, transformers, LangChain, sentence-transformers, qdrant-client — hepsi Python-first |
| Realtime | TS NestJS | WebSocket fanout | Node event loop, binlerce eşzamanlı WS bağlantısı için JVM'den verimli |

### Monorepo seçim sebepleri

| Lehte | Aleyhte |
|---|---|
| Atomik cross-service değişiklikler (proto + 5 servis aynı PR'da) | Repo büyür, clone yavaşlar (sorun değil bu ölçekte) |
| Tek CI config strateji | Workflow per service ama trigger pattern matching gerekiyor |
| Tek issue tracker, tek PR review | İzinler tek-katmanlı (servis seviyesi yok) |
| `protos/` tek kaynak — drift yok | Build cache discipline gerekir |
| CV'de "bir repo göster" daha güçlü | — |

### Reddedilen alternatifler

- **Polyrepo (servis başına 1 repo):** Cross-service değişiklikler 5 PR + senkronizasyon. Drift kaçınılmaz.
- **Tek dilde monolith (Python):** AI Engine zaten Python — diğer servisleri de Python yapmak CV showcase zayıflatır + Spring/Rust/.NET avantajları kaybolur.
- **Tek dilde mikroservis (örn. her şey Java):** Polyglot showcase'in tüm noktası kayıp.

## Consequences

### Positive
- CV'de **5 dil + mikroservis + iletişim desenleri** somut kanıt
- Her servis kendi domain'inin doğasına uygun stack ile yazıldı
- gRPC kontratları diller arası **typed contract discipline** zorluyor
- Future hire onboarding'i basit (servis README'leri)

### Negative
- Local dev makinesinde 5 farklı toolchain kurulu olmalı (.NET SDK, JDK, Rust toolchain, Python, Node)
  - **Mitigation:** `docker-compose up` ile alternatif (toolchain Docker image içinde)
- Build sürelerinin polyglot CI'da hesabı zor
  - **Mitigation:** Per-service CI; sadece değişen servis build edilir
- Cross-language refactoring zor (örn. tüm servislerde aynı field rename)
  - **Mitigation:** `protos/` source of truth — proto değiş, code-gen yeniden çalış

### Neutral
- Operasyonel karmaşıklık artar (5 servisin observability + logging + deployment)
  - **Mitigation:** OpenTelemetry first-class, per-service health endpoint, GitHub Actions matrix

## Notes

Aynı senaryoyu **Go monolith** ile yazmak production'da daha mantıklı olabilirdi. Ama bu proje **portföy + öğrenme** odaklı — over-engineered olması bilinçli.

Kullanıcının notları:
- `feedback_code_principles.md` — SOLID + DRY + KISS her satırda
- `project_deployment_model_appliance.md` — multi-tenant SaaS, portföy projesi

KISS prensibi mikroservis sayısı için **6 yerine 1** demek. Ama bu projenin asıl amacı (CV showcase) "KISS'i bilinçli kır" çağrısı yapıyor. Trade-off bilinçli.
