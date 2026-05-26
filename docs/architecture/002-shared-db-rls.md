# ADR-002: Shared Postgres + tenant_id + Row-Level Security

**Status:** Accepted
**Date:** 2026-05-26
**Supersedes:** N/A
**Related:** ADR-001 (Monorepo + Polyglot)

## Context

Multi-tenant SaaS için **veri izolasyonu** strategi seçilmeli. Üç klasik seçenek:

| Yaklaşım | Avantaj | Dezavantaj |
|---|---|---|
| **Database-per-tenant** | Maksimum izolasyon, custom backup | 1000 tenant = 1000 DB; ops cehennemi |
| **Schema-per-tenant** | İzolasyon iyi, migration karmaşık | N tenant × M tablo migration |
| **Shared DB + tenant_id** | Operasyon basit, scaling kolay | Cross-tenant leak riski (app bug) |

Ek olarak **mikroservis ortodoksisi** "database-per-service" der. Bizim durumda:
- 5 servis × 1 DB-per-service = 5 ayrı Postgres instance
- Operasyonel maliyet 5x (backup, monitoring, network, connection pools)
- Servisler arası "neden bu 3 join Postgres içinde tek query'di şimdi 3 servis call?" — anti-pattern

## Decision

**Tek Postgres instance + servis başına ayrı schema + `tenant_id` column + Postgres Row-Level Security.**

### Veritabanı topolojisi

```
postgres (tek instance)
└── kai (database)
    ├── identity_schema    ← Identity servisi sadece bu schema'ya yazar
    │   ├── users
    │   ├── tenants
    │   └── roles
    ├── documents_schema   ← Documents servisi sadece bu schema'ya yazar
    │   ├── documents
    │   └── document_chunks_meta
    └── aiengine_schema    ← AI Engine servisi sadece bu schema'ya yazar
        ├── sessions
        ├── messages
        └── tool_invocations
```

Her schema için **ayrı DB role** (least-privilege):
```sql
CREATE ROLE identity_user LOGIN PASSWORD '...';
GRANT USAGE ON SCHEMA identity_schema TO identity_user;
GRANT ALL ON ALL TABLES IN SCHEMA identity_schema TO identity_user;
-- Identity user diğer schema'lara erişemez (cross-schema okuma yok)
```

### Tenant izolasyonu — 3-katman zorunlu

#### Katman 1: Application context
Her servis JWT'den `tenant_id` okur ve **her DB connection'a session-level setting set eder**:
```sql
SET app.current_tenant_id = '<uuid>';
```

#### Katman 2: Her tabloda `tenant_id` column (NOT NULL)
```sql
CREATE TABLE documents_schema.documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,              -- denormalize, RLS için
    title TEXT NOT NULL,
    -- ...
    CONSTRAINT fk_tenant FOREIGN KEY (tenant_id)
        REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT
);
CREATE INDEX idx_documents_tenant ON documents_schema.documents(tenant_id);
```

#### Katman 3: RLS policy
```sql
ALTER TABLE documents_schema.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents_schema.documents FORCE ROW LEVEL SECURITY;  -- owner bile bypass edemez

CREATE POLICY tenant_isolation ON documents_schema.documents
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
```

**Sonuç:** Application code `SELECT * FROM documents` yazsa bile Postgres otomatik `WHERE tenant_id = X` ekler. Application bug, SQL injection, ORM hatası — hiçbiri cross-tenant leak'e yol açamaz.

## Consequences

### Positive
- **Defense-in-depth:** App + DB iki bağımsız savunma.
- **Operasyon basit:** Tek backup, tek migration, tek connection pool ailesi.
- **Cross-domain query mümkün** (gerektiğinde): aynı transaction içinde `JOIN` (örn. session → user).
- **Cost:** Tek Postgres instance — 5 ayrı DB'nin maliyetinin 1/5'i.
- **Migration discipline:** Liquibase/Flyway tek changelog tree.

### Negative
- **Single point of failure:** Postgres çökerse 5 servis çöker.
  - **Mitigation:** Postgres HA (Patroni veya managed service prod'da). Read replica analytical.
- **Connection pool paylaşımı:** Tüm servisler tek DB'ye bağlanır → max connections dikkat.
  - **Mitigation:** PgBouncer transaction-mode pooler, her servis küçük pool.
- **Servis sınırı bulanıklaşabilir:** "Hemen JOIN edebiliyorum" cazibesi.
  - **Mitigation:** Code review discipline; cross-schema JOIN sadece read-only analytical use case'lerde.

### Neutral
- **RLS performans:** Modern Postgres'te RLS overhead %1-3, tenant_id indexed ise ihmal edilebilir.
- **Test data izolasyonu:** Integration test'ler ayrı schema veya transaction rollback.

## Implementation notes

### Schema-per-service ile servis sahipliği
Her servis sadece kendi schema'sının migration'larını yönetir:
- `services/identity/src/main/resources/db/migration/` → `identity_schema`
- `services/documents/src/main/resources/db/migration/` → `documents_schema`
- vs.

### Cross-schema FK kuralları
- `tenant_id` her schemada FK olarak `identity_schema.tenants` referansı.
- Diğer cross-schema FK'lar **yasak** (servis sınırı korunur).
- Eğer servis A, servis B'nin verisine ihtiyaç duyuyorsa → REST/gRPC çağrı, JOIN yok.

### RLS bypass scenarios (bilinçli)
Sadece **2 use case'de** RLS bypass edilir:
1. **DB migration scripts** — schema değişikliği tüm tenant'lar için.
2. **Admin tooling** — explicit "platform admin" rolü, audit log zorunlu.

```sql
-- Admin için ayrı role:
CREATE ROLE platform_admin LOGIN PASSWORD '...';
ALTER ROLE platform_admin BYPASSRLS;
-- Bu role sadece manuel admin script'lerinden kullanılır
```

## Reddedilen alternatifler

### A) Database-per-service (mikroservis ortodoksisi)
- **Lehte:** Tam servis bağımsızlığı, schema migration izolasyonu.
- **Aleyhte:** 5 DB instance ops yükü, cross-domain query için 5 REST call.
- **Sebep red:** Bu projenin ölçeği için over-engineer; portföy hedefi "ortodoksi" değil "akıllı karar".

### B) Database-per-tenant
- **Lehte:** Maksimum izolasyon, custom retention.
- **Aleyhte:** Tenant onboarding = DB provision; 100 tenant = ops felaketi.
- **Sebep red:** SaaS ölçeği için kabul edilemez.

### C) Schema-per-tenant
- **Lehte:** İzolasyon iyi.
- **Aleyhte:** N tenant × M tablo migration; ORM tooling karmaşıklığı.
- **Sebep red:** RLS aynı izolasyonu daha basit verir.

## References

- Postgres RLS docs: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- `feedback_no_code_execution.md` — kod yürütme YOK
- `project_deployment_and_db_access.md` — 5-katmanlı DB defense
- `project_deployment_model_appliance.md` — shared DB + tenant_id kararı
