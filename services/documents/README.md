# ☕ Documents

> **Java 21 + Spring Boot 3** — document upload, Tika parse, MinIO storage, metadata in Postgres, "doc.uploaded" event publish.

## Sorumluluk

- HTTP multipart upload — PDF / DOCX / TXT (limit 100 MB)
- Apache Tika ile MIME tipi + text extraction
- MinIO'ya kalıcı saklama (tenant-scoped object key)
- Postgres metadata kaydı (RLS ile tenant izolasyonu)
- Redis Streams'e `doc.uploaded.v1` event publish

## Sorumluluk dışı

- Chunking (Processing servisi)
- Embedding (AI Engine)
- Full-text indexing (Processing — tantivy BM25)

## Yapı

```
src/main/java/com/aiasistan/documents/
├── DocumentsApplication.java   ← main
├── api/                         ← REST controllers
│   ├── DocumentController.java
│   └── DocumentResponse.java
├── service/                     ← business logic
│   ├── DocumentService.java
│   ├── MinioStorage.java
│   └── DocumentEventPublisher.java
├── domain/                      ← JPA entities + repositories
│   ├── Document.java
│   ├── DocumentStatus.java
│   └── DocumentRepository.java
├── config/                      ← Spring @Configuration beans
│   └── MinioConfig.java
└── common/                      ← cross-cutting
    ├── TenantContext.java
    ├── TenantContextFilter.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.yml              ← config (env-driven)
└── db/migration/
    └── V1__init.sql             ← Flyway: schema + RLS
```

## Çalıştır

```bash
# Test (Testcontainers — Docker daemon gerekli)
./mvnw test

# Dev
./mvnw spring-boot:run

# Docker (monorepo root'tan)
make up
```

## Tasarım kararları

### Why Tika
PDF / DOCX / RTF / HTML / 50+ format için tek API. Format-specific parser yazmak DRY ihlali.

### Why Flyway (not Liquibase)
Spring Boot ile entegrasyonu daha basit (auto-config), SQL-first migrations okuması kolay.

### Why event-driven (Redis Streams)
Documents sadece "upload + persist + notify" yapar. Chunking/embedding asenkron — Documents servisi cevabı 5 saniye beklemez. Loosely-coupled servis sınırı.

### Why @Transactional + event publish
Aynı method'da DB write + event publish. Eğer event Redis'e ulaşamazsa rollback olur — tutarsız state oluşmaz. Future improvement: transactional outbox pattern.
