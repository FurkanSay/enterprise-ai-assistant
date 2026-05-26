# Frontend (Next.js 15)

Chat UI, document upload, and live token streaming for the platform.

## Responsibilities

- Login flow (redirects to Identity for OAuth, or basic form)
- Document upload (drag-and-drop) and ingestion progress feedback
- Chat (message list + streaming token render via SSE and WebSocket)
- Source citations (clickable references back to document chunks)

## Stack

- Next.js 15 (App Router) + React 19
- Tailwind CSS v3 with CSS-variable design tokens
- shadcn/ui for component primitives (Radix UI + Tailwind, source-checked-in)
- Inter (sans, UI) + Source Serif 4 (serif, chat content) — Google Fonts
- TanStack Query for server state
- Server Actions for mutations

See [ADR-005](../docs/architecture/005-ui-design-system.md) for the design-system rationale.

## Layout

```
src/
├── app/                              Next.js App Router
│   ├── layout.tsx                    root layout, fonts wired in
│   ├── page.tsx                      landing
│   ├── globals.css                   CSS-variable design tokens
│   ├── chat/[sessionId]/             Phase H: chat surface
│   ├── documents/                    Phase H: upload + list
│   └── login/                        Phase H: auth flow
├── components/
│   └── ui/                           shadcn components (added via `npx shadcn add`)
└── lib/
    └── utils.ts                      `cn()` Tailwind merge helper
```

## Run

```bash
cd frontend
pnpm install
pnpm dev
```

Open <http://localhost:3000>.

## Adding components

```bash
# from frontend/
npx shadcn@latest add button
npx shadcn@latest add input
npx shadcn@latest add card dialog
```

Components are copied into `src/components/ui/`. They are source files; edit freely.

## Theme tokens

All color and radius tokens live in `src/app/globals.css` as HSL CSS variables, then are exposed to Tailwind in `tailwind.config.ts`. Changing a token updates the entire app without touching component code.

The light theme is claude.ai–adjacent: warm neutral background, coral accent. Dark mode tokens are placeholders, finalised in Phase H.4.

## Design decisions

- **App Router** (not Pages Router) — streaming UI, RSC, typed routes.
- **Gateway-only API** — frontend knows only `gateway:8080`. Identity / Documents / AI Engine addresses never appear in client code.
- **SSE for chat** — one-way streaming, built-in auto-reconnect, JWT header is straightforward.
- **WebSocket for status push** — server-to-client only, but a single connection multiplexes many session channels.
