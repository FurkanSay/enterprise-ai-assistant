# syntax=docker/dockerfile:1.7

FROM rust:1.82 AS build
WORKDIR /build

# Cache deps
COPY Cargo.toml .
COPY crates/processing-server/Cargo.toml crates/processing-server/
COPY crates/chunker/Cargo.toml crates/chunker/
COPY crates/bm25-index/Cargo.toml crates/bm25-index/
RUN mkdir -p crates/processing-server/src crates/chunker/src crates/bm25-index/src && \
    echo "fn main() {}" > crates/processing-server/src/main.rs && \
    echo "" > crates/chunker/src/lib.rs && \
    echo "" > crates/bm25-index/src/lib.rs && \
    cargo build --release --workspace || true

# Real build
COPY crates/ crates/
RUN cargo build --release --bin processing-server

FROM debian:bookworm-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && \
    rm -rf /var/lib/apt/lists/*
RUN groupadd -r processing && useradd -r -g processing -d /app -s /sbin/nologin processing
WORKDIR /app
COPY --from=build --chown=processing:processing /build/target/release/processing-server /app/processing-server
RUN mkdir -p /var/lib/processing/index && chown -R processing:processing /var/lib/processing
EXPOSE 8083
USER processing
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8083/health/live || exit 1
ENTRYPOINT ["/app/processing-server"]
