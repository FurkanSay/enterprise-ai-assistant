# syntax=docker/dockerfile:1.7
# Build context = repo root.

FROM rust:1 AS build
WORKDIR /build

# Bring in everything the workspace needs to resolve before sources.
COPY services/processing/Cargo.toml                                services/processing/
COPY services/processing/crates/processing-server/Cargo.toml       services/processing/crates/processing-server/
COPY services/processing/crates/chunker/Cargo.toml                 services/processing/crates/chunker/
COPY services/processing/crates/bm25-index/Cargo.toml              services/processing/crates/bm25-index/
COPY libs/generated/rust/Cargo.toml                                libs/generated/rust/

# Stub sources so cargo can resolve before real code arrives.
RUN mkdir -p services/processing/crates/processing-server/src \
             services/processing/crates/chunker/src \
             services/processing/crates/bm25-index/src \
             libs/generated/rust/src && \
    echo "fn main() {}" > services/processing/crates/processing-server/src/main.rs && \
    : > services/processing/crates/chunker/src/lib.rs && \
    : > services/processing/crates/bm25-index/src/lib.rs && \
    : > libs/generated/rust/src/lib.rs

WORKDIR /build/services/processing
RUN cargo build --release --workspace || true

# Real sources.
WORKDIR /build
COPY services/processing/crates  services/processing/crates
COPY libs/generated/rust         libs/generated/rust

WORKDIR /build/services/processing
RUN cargo build --release --bin processing-server

FROM debian:bookworm-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && \
    rm -rf /var/lib/apt/lists/*
RUN groupadd -r processing && useradd -r -g processing -d /app -s /sbin/nologin processing
WORKDIR /app
COPY --from=build --chown=processing:processing /build/services/processing/target/release/processing-server /app/processing-server
RUN mkdir -p /var/lib/processing/index && chown -R processing:processing /var/lib/processing
EXPOSE 8083
USER processing
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8083/health/live || exit 1
ENTRYPOINT ["/app/processing-server"]
