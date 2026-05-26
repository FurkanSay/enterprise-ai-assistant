# syntax=docker/dockerfile:1.7

# ─── Build stage ─────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder

# Install uv (fast Python package manager)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/

WORKDIR /app

# Cache dependencies
COPY pyproject.toml ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --no-install-project --frozen || uv sync --no-install-project

# Copy source
COPY src/ ./src/

# Install project itself
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen || uv sync

# ─── Runtime stage ───────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

# Non-root user
RUN groupadd -r aiengine && useradd -r -g aiengine -d /app -s /sbin/nologin aiengine

WORKDIR /app

# Copy virtualenv from builder
COPY --from=builder --chown=aiengine:aiengine /app/.venv /app/.venv
COPY --from=builder --chown=aiengine:aiengine /app/src /app/src
COPY --chown=aiengine:aiengine pyproject.toml ./

# Make virtualenv available
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONPATH="/app/src" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

USER aiengine

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8084/health/live').read()" || exit 1

CMD ["uvicorn", "aiengine.main:app", "--host", "0.0.0.0", "--port", "8084"]
