# syntax=docker/dockerfile:1.7
# Build context = repo root.
#
# Phase B: nginx-served static landing page. The Next.js + React + Tailwind
# implementation lives committed in this folder (package.json, next.config.js,
# src/, tsconfig.json) and is picked up again in Phase H when the chat surface
# is built. The decision is recorded in ROADMAP.md.

FROM nginx:1.27-alpine

# Drop the default nginx site, install ours, and copy the static landing page.
RUN rm -f /etc/nginx/conf.d/default.conf
COPY frontend/nginx.conf            /etc/nginx/nginx.conf
COPY frontend/public/index.html     /usr/share/nginx/html/index.html

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:3000/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
