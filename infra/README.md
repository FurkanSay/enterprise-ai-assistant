# Infrastructure config

Bu klasör altyapı bileşenlerinin (Postgres, Redis, MinIO, Qdrant, OTel collector, Prometheus, Grafana) **container'a girdi olarak verilen** config dosyalarını içerir. Docker Compose tarafından mount edilirler.

## Yapı

```
infra/
├── postgres/
│   └── init/
│       ├── 01-extensions.sql              ← pgvector, pgcrypto, pg_trgm, uuid-ossp
│       ├── 02-schemas-and-roles.sql       ← schema-per-service + least-privilege users
│       └── 03-rls-helpers.sql             ← public.current_tenant_id() function
│   └── migrations/                         ← (boş — her servis kendi migration'ını yönetir)
├── redis/                                  ← (boş — default config)
├── minio/
│   └── init-bucket.sh                     ← bucket bootstrap
├── qdrant/                                 ← (boş — default config)
├── jaeger/                                 ← (boş — all-in-one image kullanıyoruz)
├── otel-collector/
│   └── config.yaml                        ← receivers + processors + exporters
├── prometheus/
│   └── prometheus.yml                     ← scrape jobs (her servis için)
└── grafana/
    ├── provisioning/
    │   ├── datasources/datasources.yml   ← Prometheus + Jaeger auto-wire
    │   └── dashboards/dashboards.yml     ← /var/lib/grafana/dashboards okuma
    └── dashboards/                       ← JSON dashboard'lar (sonra eklenir)
```

## Postgres init sırası

Container ilk açılışta `/docker-entrypoint-initdb.d/` içindeki `.sql` ve `.sh` dosyalarını **alfabetik sırayla** çalıştırır. Bu yüzden:
1. `01-extensions.sql` — extensions önce
2. `02-schemas-and-roles.sql` — sonra schema + role
3. `03-rls-helpers.sql` — sonra RLS helper'lar

Her servis kendi tablolarını Flyway / EF Core migration'ları ile oluşturur — bu init dosyaları **sadece** bootstrap.

## RLS pattern (örnek kullanım Documents'tan)

```sql
-- Migration içinde
ALTER TABLE documents_schema.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents_schema.documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON documents_schema.documents
    USING (tenant_id = public.current_tenant_id());
```

Her servis HER connection açıldığında:
```sql
SET app.current_tenant_id = '<uuid-from-jwt>';
```
Sonrasında **tüm** sorgular otomatik olarak tenant'a filtrelenir.

## OTel akışı

```
Services (OTLP gRPC :4317)
        ↓
otel-collector
   ├─ traces  → Jaeger (:14250 / :4317)
   └─ metrics → Prometheus pull (collector :8889)
                    ↓
                Grafana (datasource: Prometheus)
                Grafana (datasource: Jaeger)
```

## Prod farklılıkları

| Bileşen | Dev (bu) | Prod (öneri) |
|---|---|---|
| Postgres | tek instance | Patroni HA + read replicas |
| Redis | tek instance | Sentinel veya Redis Cluster |
| MinIO | tek node | dağıtık MinIO (4+ node) veya managed S3 |
| Qdrant | tek node | Qdrant Cloud veya 3-node cluster |
| OTel | collector → Jaeger lokal | collector → Tempo / Datadog / Honeycomb |
| Grafana | docker | Grafana Cloud veya managed |
| Secrets | .env | Vault / AWS Secrets Manager / Azure Key Vault |
