# 🌐 Frontend

> **Next.js 15 (App Router) + Tailwind** — sohbet UI, doküman upload, real-time token stream.

## Yapı

```
app/
├── page.tsx                 ← landing
├── (chat)/page.tsx          ← sohbet ekranı (client component, SSE consume)
├── api/health/route.ts      ← /api/health
└── layout.tsx               ← root layout + globals.css

components/
├── chat-messages.tsx
├── chat-input.tsx
└── document-upload.tsx

lib/
├── api-client.ts            ← Gateway REST + SSE parsing
└── ws-client.ts             ← Realtime WebSocket
```

## Çalıştır

```bash
cd frontend
npm install
npm run dev      # http://localhost:3000
```

## Tasarım kararları

### Why App Router (Pages Router değil)
Streaming UI, RSC, typed routes — App Router 2026 standartı.

### Why Gateway proxy (direct service erişimi değil)
Frontend yalnızca **gateway:8080**'i bilir. Identity / Documents / AI Engine adresleri frontend'de **hiç yok**. Bu KISS + güvenlik.

### Why SSE (WebSocket değil — sohbet için)
Chat one-way streaming. SSE built-in, auto-reconnect var, JWT header geçişi kolay.

### Why WebSocket sadece status / token push
İki yönlü değil — sadece server→client. Ama farklı session'ları multiplexlemek için kanal mantığı gerek → WS uygun.
