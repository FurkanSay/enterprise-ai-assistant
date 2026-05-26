# 🟦 Gateway

> **C# .NET 10 + YARP** — reverse proxy. JWT validation, rate limiting, tenant context forwarding, CORS, distributed tracing root.

## Sorumluluk

- **Edge auth** — JWT signature + issuer + audience + lifetime validation
- **Reverse proxy** — config-driven routes (YARP) to Identity / Documents / AI Engine
- **Tenant context forwarding** — JWT claims → `X-Tenant-Id`, `X-User-Id`, `X-User-Roles` headers (downstream services trust these)
- **Rate limiting** — per-tenant fixed window (default 120 req/min)
- **CORS** — browser frontend whitelist
- **Distributed trace root** — every request creates an OTel span; downstream services continue it

## Sorumluluk dışı

- Business logic (downstream services)
- Token issuance (Identity servisi)
- User/tenant CRUD (Identity servisi)
- WebSocket fanout (Realtime servisi)

## Yapı

```
src/Gateway.Api/
├── Program.cs                  ← composition root, middleware pipeline
├── Auth/
│   ├── AuthExtensions.cs       ← JWT bearer configuration
│   └── TenantContextForwarding.cs  ← claims → X-* headers
├── RateLimit/
│   └── RateLimitExtensions.cs  ← fixed window per tenant
├── Telemetry/
│   └── TelemetryExtensions.cs  ← OTel tracing
├── HealthChecks/
│   └── HealthEndpoints.cs      ← /health/live, /health/ready
├── appsettings.json            ← YARP routes + clusters + JWT config
└── appsettings.Development.json ← localhost overrides
```

## Çalıştır

### Dev
```bash
cd services/gateway/src/Gateway.Api
dotnet run
# → http://localhost:8080
```

### Test (kök dizinden)
```bash
make test-gateway
```

### Docker
```bash
make up
docker compose logs -f gateway
```

## YARP route ekleme

Yeni bir route eklemek için **sadece** `appsettings.json` düzenle:

```json
{
  "ReverseProxy": {
    "Routes": {
      "my-new-route": {
        "ClusterId": "my-cluster",
        "AuthorizationPolicy": "default",
        "Match": { "Path": "/api/v1/my-resource/{**catch-all}" },
        "Transforms": [
          { "PathPattern": "/v1/my-resource/{**catch-all}" }
        ]
      }
    },
    "Clusters": {
      "my-cluster": {
        "Destinations": {
          "instance-1": { "Address": "http://my-service:8090" }
        }
      }
    }
  }
}
```

## Tasarım kararları

### Why YARP (custom proxy değil)
Microsoft purpose-built: native AOT, dynamic config reload, transforms pipeline. Custom proxy yazmak DRY ihlali olurdu.

### Why JWT validation Gateway'de
Downstream servisler 5 farklı dilde. Her birinde JWT signature validate etmek karmaşıklık yaratır. Tek otorite → headers ile downstream'e propagate → KISS.

### Why per-tenant rate limit
Free tier user'lar enterprise tenant'ı yavaşlatamasın. Tenant'a göre bucket = adil paylaşım.
