#!/bin/sh
# One-shot bucket bootstrap. Runs from a separate `minio-init` container in compose.
# Idempotent — `mb` is a no-op if bucket exists.

set -eu

MC_HOST="${MC_HOST:-http://minio:9000}"
MC_USER="${MC_USER:-kai_admin}"
MC_PASSWORD="${MC_PASSWORD:-kai_minio_pwd}"

mc alias set local "$MC_HOST" "$MC_USER" "$MC_PASSWORD"

mc mb --ignore-existing local/documents
mc mb --ignore-existing local/generated

echo "MinIO buckets ready."
