# 🟨 Realtime

> **TypeScript + NestJS + Fastify + ws** — WebSocket gateway. Browser bağlantılarını tutar, AI Engine'in Redis pub/sub'a bastığı token stream'lerini iletim hattına aktarır.

## Sorumluluk

- **WebSocket gateway** — browser per-session bağlantısı (`/ws?session=<id>`)
- **Redis pub/sub consumer** — `stream.<tenantId>.<sessionId>` kanalları
- **Fanout** — gelen mesajları doğru socket'lere ilet
- **Doküman status events** — `doc.ready.v1` event'ini WebSocket'e çevir
- **Health endpoints** — `/health/live`, `/health/ready`

## Sorumluluk dışı

- LLM iletişim (AI Engine)
- Authentication (Gateway — JWT validate ediyor, biz header'a güveniyoruz)
- Persistence (servis stateless)

## Yapı

```
src/
├── main.ts                          ← bootstrap + OTel
├── telemetry.ts                     ← OpenTelemetry init
├── app.module.ts                    ← root module
├── realtime/
│   ├── realtime.module.ts
│   ├── realtime.gateway.ts          ← @WebSocketGateway, connection lifecycle
│   └── token-stream.consumer.ts     ← Redis PSUBSCRIBE → gateway.fanout()
├── redis/
│   └── redis.module.ts              ← 2 client (commands + dedicated subscriber)
└── health/
    ├── health.module.ts
    └── health.controller.ts
```

## Çalıştır

```bash
# Local dev
cd services/realtime
npm install
npm run start:dev

# Test
npm test

# Docker
make up
docker compose logs -f realtime
```

## Tasarım kararları

### Why Fastify (Express değil)
Fastify event-loop performansı 2-3x daha iyi WebSocket workload'larında. NestJS native destekliyor.

### Why iki Redis client
ioredis subscriber connection bloke olur — subscribe komutu sonrası başka command alamaz. İki client: biri `psubscribe`, biri `ping` ve diğer komutlar için.

### Why PSUBSCRIBE pattern
`stream.*` ile bir kerede tüm session'ları subscribe ediyoruz. Tek bir socket kapatıldığında subscription'ı eklemek/çıkarmak gerekmiyor — N tenant × M session için stateless.

### Why session-keyed fanout (user-keyed değil)
Aynı user iki tab açtıysa, iki ayrı session var → iki ayrı LLM stream. Bunları karıştırmamak için key = `tenant:session`.
