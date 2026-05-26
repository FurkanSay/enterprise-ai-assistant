# ADR-005: UI Design System

**Status:** Accepted
**Date:** 2026-05-26
**Related:** Phase H (Frontend)

## Context

The frontend needs a component layer and a visual language. The product is a document-and-chat workspace, so the UI must feel like a reading surface: calm, generous whitespace, neutral backgrounds, low chrome. The visual language of `claude.ai` is a good reference for that intent.

Two questions need to be settled before Phase H starts:
1. Which component library do we use, if any?
2. Which typography and color palette do we ship with?

## Decision

### Component layer: **shadcn/ui**

`shadcn/ui` is not a runtime dependency — it is a code-distribution mechanism. Components are copied into `frontend/src/components/ui/` and become source files we own. They are built on top of Radix UI primitives (accessibility, keyboard handling) and styled with Tailwind. This means:

- No vendor lock-in to a versioned npm package.
- Every component is editable in place; no "fork the library" pain.
- Wide industry adoption — most modern React codebases either use it or recognize the patterns.

Setup is one-time:
```bash
cd frontend
npx shadcn@latest init
# then per-component:
npx shadcn@latest add button card input dialog ...
```

### Color palette: claude.ai–adjacent neutral with coral accent

| Token | Light | Dark (later) | Use |
|---|---|---|---|
| `--background` | `30 33% 96%` (#F9F4EE) | TBD | page background |
| `--foreground` | `30 11% 16%` (#2C2925) | TBD | body text |
| `--card` | `0 0% 100%` | TBD | message bubbles, panels |
| `--card-foreground` | `30 11% 16%` | TBD | text on cards |
| `--muted` | `30 20% 92%` | TBD | secondary surfaces |
| `--muted-foreground` | `30 4% 43%` (#6F6E6A) | TBD | secondary text |
| `--border` | `30 25% 86%` (#E5DDD0) | TBD | dividers |
| `--input` | `30 25% 86%` | TBD | input borders |
| `--ring` | `16 51% 58%` (#CC785C) | TBD | focus ring |
| `--primary` | `16 51% 58%` (#CC785C) | TBD | primary actions |
| `--primary-foreground` | `0 0% 100%` | TBD | text on primary |
| `--accent` | `30 33% 90%` | TBD | hovers, highlights |
| `--accent-foreground` | `30 11% 16%` | TBD | text on accent |
| `--destructive` | `0 70% 50%` | TBD | dangerous actions |
| `--destructive-foreground` | `0 0% 100%` | TBD | text on destructive |

Dark mode tokens are deferred until Phase H.4; the platform launches light-first because the document-reading use case is primarily light-mode in enterprise.

### Typography: Inter + Source Serif 4 (Google Fonts)

- **Sans (UI, buttons, headings):** Inter
- **Serif (long-form body, chat content):** Source Serif 4

These are close-enough free analogs of claude.ai's Söhne and Tiempos respectively. Söhne + Tiempos are licensed fonts (Klim Type, ~$100 combined); we do not justify that license cost during phases A–I. If polishing for final showcase, the license can be bought later and the `--font-sans` / `--font-serif` CSS variables swapped — no component rewrites needed.

### Tailwind setup

Tailwind v3 with CSS variables. The variables are declared once in `globals.css` and consumed by `tailwind.config.ts`'s `theme.extend.colors` block as `hsl(var(--background))` references. This is the standard shadcn pattern and lets us swap themes without touching component code.

### What is NOT used

- **No CSS-in-JS** (styled-components, emotion). Tailwind class strings are sufficient.
- **No global UI state library** (Redux, Zustand). TanStack Query + Server Actions + URL state cover the cases we expect.
- **No icon font**. lucide-react (tree-shakeable, already standard with shadcn).
- **No drag-and-drop library yet**. If file upload needs richer DnD than the native `<input type="file">` allows, react-dropzone is added at that time.

## Consequences

### Positive
- Components live as source files, owned and editable in place.
- Tailwind classes mean style and structure live next to each other; refactors are local.
- CSS variables make a future dark mode or a font-license swap a one-file change.
- No runtime UI library dependency to keep up-to-date.

### Negative
- shadcn's `npx` flow assumes a developer machine; CI never runs `shadcn add`. Components are checked in.
- Tailwind class strings can grow long. Mitigated with `clsx` + `tailwind-merge` (the `cn()` helper).

### Neutral
- The decision can be revisited if the project ever ships a public-facing marketing site that needs different aesthetics; that would live in a separate Next.js project, not contaminate the app.

## References
- shadcn/ui: <https://ui.shadcn.com>
- Radix UI primitives: <https://www.radix-ui.com>
- Tailwind CSS: <https://tailwindcss.com>
- Inter font: <https://rsms.me/inter/>
- Source Serif 4: <https://fonts.google.com/specimen/Source+Serif+4>
