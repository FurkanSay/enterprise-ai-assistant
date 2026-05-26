# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --ignore-scripts

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json tsconfig.json nest-cli.json ./
RUN npm install --ignore-scripts
COPY src ./src
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
RUN addgroup -S realtime && adduser -S realtime -G realtime
COPY --from=deps  --chown=realtime:realtime /app/node_modules ./node_modules
COPY --from=build --chown=realtime:realtime /app/dist          ./dist
COPY --chown=realtime:realtime package.json .
ENV NODE_ENV=production PORT=8085
EXPOSE 8085
USER realtime
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:8085/health/live || exit 1
CMD ["node", "dist/main.js"]
