# syntax=docker/dockerfile:1.7
# Build context = repo root.
#
# Phase H: real Next.js build replacing the earlier nginx static placeholder.
# Three stages:
#   1) deps   — install full dependency tree once, cache it
#   2) build  — copy source, run `next build` (uses output: 'standalone')
#   3) runtime — only the bundled .next/standalone + .next/static + public

FROM node:22-alpine AS deps
WORKDIR /app
COPY frontend/package.json ./
# `--ignore-scripts` skips any postinstall steps from transitive deps that
# would try to run during the docker layer (e.g. native build hooks).
RUN npm install --ignore-scripts

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY frontend/package.json frontend/tsconfig.json frontend/next.config.js ./
# Tailwind / PostCSS configuration is inferred from the files below if any
# of them exist. globals.css contains the @tailwind directives.
COPY frontend/postcss.config.js* frontend/tailwind.config.js* ./
COPY frontend/app           ./app
COPY frontend/components    ./components
COPY frontend/lib           ./lib
COPY frontend/public        ./public
# A `src/` folder exists in the repo but is not part of the active Next.js
# tree — Next picks `app/` at the project root first. We skip copying src/
# to keep the build context lean.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
RUN addgroup -S frontend && adduser -S frontend -G frontend

# `output: 'standalone'` produces:
#   .next/standalone   — minimal server.js + the route bundle, no node_modules
#   .next/static       — hashed static assets the server.js refers to
# We also need public/ alongside the standalone tree.
COPY --from=build --chown=frontend:frontend /app/.next/standalone ./
COPY --from=build --chown=frontend:frontend /app/.next/static     ./.next/static
COPY --from=build --chown=frontend:frontend /app/public           ./public

ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000 \
    NEXT_TELEMETRY_DISABLED=1
EXPOSE 3000
USER frontend

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
