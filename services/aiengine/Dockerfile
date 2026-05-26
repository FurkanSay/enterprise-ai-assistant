# syntax=docker/dockerfile:1.7
# Build context = repo root.

# ─── Build stage ─────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder

# Install uv (fast Python package manager)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/

WORKDIR /app

# Bring in everything uv needs to resolve dependencies (including the
# path-editable kai-protos at libs/generated/python/).
COPY services/aiengine/pyproject.toml services/aiengine/
COPY libs/generated/python/           libs/generated/python/

WORKDIR /app/services/aiengine
# No uv.lock committed yet — resolve fresh during the image build.
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --no-install-project

# Copy real source and finalise install.
WORKDIR /app
COPY services/aiengine/src services/aiengine/src

WORKDIR /app/services/aiengine
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync

# ─── Runtime stage ───────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN groupadd -r aiengine && useradd -r -g aiengine -d /app -s /sbin/nologin aiengine
WORKDIR /app

COPY --from=builder --chown=aiengine:aiengine /app/services/aiengine/.venv /app/.venv
COPY --from=builder --chown=aiengine:aiengine /app/services/aiengine/src   /app/src
COPY --from=builder --chown=aiengine:aiengine /app/libs/generated/python   /app/libs/generated/python
COPY --chown=aiengine:aiengine services/aiengine/pyproject.toml            /app/pyproject.toml

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONPATH="/app/src" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

USER aiengine
EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8084/health/live').read()" || exit 1

CMD ["uvicorn", "aiengine.main:app", "--host", "0.0.0.0", "--port", "8084"]
