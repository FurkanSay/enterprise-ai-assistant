# gRPC Contracts

Bu klasör servisler arası iletişim için **kanonik kontratları** içerir. Burası source of truth — kod buradan üretilir, tersi değil.

## Yapı

```
protos/
├── buf.yaml              ← workspace
├── buf.gen.yaml          ← code-gen plugins (5 dil için)
├── common/v1/            ← cross-service paylaşımlı tipler
│   ├── tenant.proto      ← TenantContext (her RPC header'ında)
│   ├── error.proto       ← CommonError
│   └── pagination.proto  ← cursor-based pagination
├── documents/v1/
│   └── documents.proto   ← DocumentsService
├── processing/v1/
│   └── processing.proto  ← ProcessingService (BM25)
├── aiengine/v1/
│   └── aiengine.proto    ← AiEngineService (mostly REST; this is internal)
└── identity/v1/
    └── identity.proto    ← IdentityService (ValidateToken)
```

## Workflow

```bash
# Lint
make proto-lint

# Breaking change check (CI'da otomatik)
make proto-breaking

# Code-gen — stubs to libs/generated/<lang>/
make proto
```

## Versioning

Detay: [ADR-003](../docs/architecture/003-grpc-contracts.md)

- Paket: `<service>.v<n>` — `documents.v1`
- Breaking change → yeni paket version (`documents.v2`), eski'yi 6 ay deprecate süreciyle koru
- Field numarası **asla** değişmez (silinmiş bile olsa `reserved`)

## Style

```protobuf
// ✅ Doğru
message Document {
  string id = 1;
  string tenant_id = 2;
  google.protobuf.Timestamp created_at = 3;
}

// ❌ Yanlış: camelCase field, PascalCase
message documents {
  string docId = 1;
}
```

- Mesaj `PascalCase`, field `snake_case`, enum `SCREAMING_SNAKE_CASE`
- Her field açıklayıcı yorum
- `required` kullanma (proto3'te yok); semantic required application'da
- `oneof` zorunlu choice için

## Servis sınırı

Her servis sadece kendi paketinin **server**'ını implement eder; diğerlerini **client** olarak çağırır. Çapraz sahiplik yok.
