# syntax=docker/dockerfile:1.7
# Build context = repo root.
#
# Phase E switched back to glibc:
#   musl + alpine carried Phase B5 nicely (no native deps), but Phase E
#   pulls in fastembed-rs, which links against ONNX Runtime — and ORT only
#   ships glibc binaries. Statically linking ORT into a musl target would
#   require a custom build that takes hours. Using rust:1-bookworm for the
#   builder and bookworm-slim for the runtime keeps glibc on both sides
#   (same Debian release ⇒ same glibc version ⇒ no dynamic-linker mismatch).

FROM rust:1-bookworm AS build
RUN apt-get update && apt-get install -y --no-install-recommends \
        pkg-config libssl-dev ca-certificates && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY services/processing  services/processing
COPY libs/generated/rust  libs/generated/rust

WORKDIR /build/services/processing

# Keep concurrency low so the WSL2-backed Docker daemon does not buckle
# under cargo's default per-core parallelism (ONNX Runtime + tantivy
# compile units are heavy).
ENV CARGO_BUILD_JOBS=2

RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/usr/local/cargo/git \
    cargo build --release --bin processing-server

FROM debian:bookworm-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl libssl3 && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r processing && useradd -r -g processing -d /app -s /usr/sbin/nologin processing
WORKDIR /app

COPY --from=build --chown=processing:processing \
     /build/services/processing/target/release/processing-server \
     /app/processing-server

RUN mkdir -p /var/lib/processing/index /var/lib/processing/models && \
    chown -R processing:processing /var/lib/processing
EXPOSE 8083
USER processing

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8083/health/ready || exit 1

ENTRYPOINT ["/app/processing-server"]
