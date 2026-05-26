# syntax=docker/dockerfile:1.7
# Build context = repo root.
#
# Why musl + alpine:
#   The earlier debian:bookworm-slim runtime kept rejecting a perfectly
#   present binary with `no such file or directory` — the classic glibc
#   dynamic-linker mismatch that bites Rust binaries crossing distro
#   boundaries. Statically linking against musl removes every runtime
#   dependency; the binary is a single self-contained ELF that runs in
#   any Linux userspace, including the ~10MB Alpine base used at runtime.

FROM rust:1-alpine AS build
RUN apk add --no-cache musl-dev pkgconfig

WORKDIR /build
COPY services/processing  services/processing
COPY libs/generated/rust  libs/generated/rust

WORKDIR /build/services/processing
RUN rustup target add x86_64-unknown-linux-musl

# Keep concurrency low so the WSL2-backed Docker daemon does not buckle
# under cargo's default per-core parallelism (it has been ending pipes
# mid-build on this host).
ENV CARGO_BUILD_JOBS=2

RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/usr/local/cargo/git \
    cargo build --release --target x86_64-unknown-linux-musl --bin processing-server

FROM alpine:3.20 AS runtime
RUN apk add --no-cache ca-certificates curl
RUN addgroup -S processing && adduser -S processing -G processing
WORKDIR /app

COPY --from=build --chown=processing:processing \
     /build/services/processing/target/x86_64-unknown-linux-musl/release/processing-server \
     /app/processing-server

RUN mkdir -p /var/lib/processing/index && chown -R processing:processing /var/lib/processing
EXPOSE 8083
USER processing

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8083/health/live || exit 1

ENTRYPOINT ["/app/processing-server"]
