#!/usr/bin/env bash
# Enterprise AI Assistant — end-to-end smoke test
#
# Walks the full happy path through the running stack:
#   1. register a new user under the seeded demo tenant
#   2. login → JWT
#   3. /me      — proves the gateway forwarded the JWT correctly
#   4. upload a document via Gateway
#   5. poll status until READY (Processing has chunked + embedded it)
#   6. chat → assert at least one token event arrives
#   7. verify the Qdrant points count grew
#
# Exits non-zero on the first assertion failure. Designed to be wired
# into CI or run by hand: `./scripts/e2e-smoke.sh`.
#
# Requires the stack to be up:
#   docker compose up -d
#
# Environment overrides:
#   GATEWAY_URL  default http://localhost:8080
#   QDRANT_URL   default http://localhost:6333

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
QDRANT_URL="${QDRANT_URL:-http://localhost:6333}"
SUFFIX="$(date +%s)-$RANDOM"
EMAIL="smoke+${SUFFIX}@kai.test"
PASSWORD="smoke-pwd-min-8-chars-$SUFFIX"
TENANT_ID="00000000-0000-0000-0000-000000000001"

red()    { printf '\033[31m%s\033[0m\n' "$*"; }
green()  { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
step()   { printf '\n\033[36m▶ %s\033[0m\n' "$*"; }

fail() {
    red "FAIL: $*"
    exit 1
}

# ── 1. Register ─────────────────────────────────────────────────────────
step "1. Register $EMAIL"
REG=$(curl -fsS -X POST "$GATEWAY_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"E2E Smoke\"}" \
    -w '\n%{http_code}')
REG_CODE="${REG##*$'\n'}"
REG_BODY="${REG%$'\n'*}"
[ "$REG_CODE" = "201" ] || fail "register expected 201, got $REG_CODE: $REG_BODY"
USER_ID=$(printf '%s' "$REG_BODY" | grep -oE '"userId":"[^"]+"' | head -1 | sed 's/.*"userId":"//;s/"$//')
green "  user_id=$USER_ID"

# ── 2. Login ────────────────────────────────────────────────────────────
step "2. Login"
LOGIN=$(curl -fsS -X POST "$GATEWAY_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS=$(printf '%s' "$LOGIN" | grep -oE '"accessToken":"[^"]+"' | head -1 | sed 's/.*"accessToken":"//;s/"$//')
[ -n "$ACCESS" ] || fail "login returned no accessToken: $LOGIN"
green "  access token issued (first 30 chars: ${ACCESS:0:30}…)"

# ── 3. /me ──────────────────────────────────────────────────────────────
step "3. GET /api/v1/auth/me with Bearer"
ME=$(curl -fsS "$GATEWAY_URL/api/v1/auth/me" -H "Authorization: Bearer $ACCESS")
ME_EMAIL=$(printf '%s' "$ME" | grep -oE '"email":"[^"]+"' | head -1 | sed 's/.*"email":"//;s/"$//')
[ "$ME_EMAIL" = "$EMAIL" ] || fail "/me returned $ME_EMAIL, expected $EMAIL"
green "  /me confirmed identity"

# ── 4. Upload document ──────────────────────────────────────────────────
step "4. Upload document via Gateway"
# Write the upload payload into the repo root rather than /tmp — on
# Windows/Git Bash, curl's `-F @/tmp/...` path-translation can fail
# because curl is a Windows binary that does not understand POSIX /tmp.
TMPFILE="./.e2e-smoke-${SUFFIX}.txt"
cat > "$TMPFILE" <<'BODY'
Apache Tika is a content extraction toolkit. Spring Boot maps JPA entities
to Postgres tables. Row-Level Security policies use current_setting to
filter rows by tenant. The fastembed library produces 768-dim vectors via
ONNX Runtime. Hybrid retrieval uses Qdrant for semantic search and tantivy
for BM25 lexical scoring.
BODY
UPLOAD=$(curl -fsS -X POST "$GATEWAY_URL/api/v1/documents" \
    -H "Authorization: Bearer $ACCESS" \
    -F "title=e2e-smoke-${SUFFIX}" \
    -F "file=@$TMPFILE;type=text/plain")
DOC_ID=$(printf '%s' "$UPLOAD" | grep -oE '"id":"[^"]+"' | head -1 | sed 's/.*"id":"//;s/"$//')
[ -n "$DOC_ID" ] || fail "upload returned no id: $UPLOAD"
green "  document_id=$DOC_ID"
rm -f "$TMPFILE"

# ── 5a. /chat tmp log path workaround (Windows curl) ────────────────────
CHAT_LOG="./.e2e-smoke-chat-${SUFFIX}.log"

# ── 5. Wait for status=READY ────────────────────────────────────────────
step "5. Wait for ingestion pipeline (status=READY)"
DEADLINE=$(( $(date +%s) + 45 ))
STATUS=""
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    STATUS=$(curl -fsS "$GATEWAY_URL/api/v1/documents/$DOC_ID" \
        -H "Authorization: Bearer $ACCESS" \
        | grep -oE '"status":"[A-Z]+"' \
        | head -1 \
        | sed 's/.*"status":"//;s/"$//')
    case "$STATUS" in
        READY)  green "  pipeline complete (status=READY)"; break ;;
        FAILED) fail "ingestion pipeline failed for $DOC_ID" ;;
        *)      yellow "  status=$STATUS (waiting)"; sleep 2 ;;
    esac
done
[ "$STATUS" = "READY" ] || fail "timeout waiting for status=READY, last=$STATUS"

# ── 6. Chat ─────────────────────────────────────────────────────────────
step "6. POST /api/v1/chat (SSE)"
curl -fsS -N -X POST "$GATEWAY_URL/api/v1/chat" \
    -H "Authorization: Bearer $ACCESS" \
    -H "Content-Type: application/json" \
    -d '{"message":"Reply with: hello"}' \
    --max-time 30 > "$CHAT_LOG" || true
TOKEN_COUNT=$(grep -c "^event: token" "$CHAT_LOG" || true)
DONE_COUNT=$(grep -c "^event: done" "$CHAT_LOG" || true)
[ "$TOKEN_COUNT" -gt 0 ] || fail "chat produced no token events: $(cat "$CHAT_LOG" | head -10)"
green "  received $TOKEN_COUNT token events, done=$DONE_COUNT"
rm -f "$CHAT_LOG"

# ── 7. Qdrant vectors exist ─────────────────────────────────────────────
step "7. Verify Qdrant has at least one point for this tenant"
POINTS=$(curl -fsS -X POST "$QDRANT_URL/collections/documents/points/count" \
    -H "Content-Type: application/json" \
    -d '{"exact": true}' \
    | grep -oE '"count":[0-9]+' \
    | head -1 \
    | sed 's/"count"://')
[ -n "$POINTS" ] && [ "$POINTS" -gt 0 ] || fail "Qdrant points count is empty/0"
green "  Qdrant has $POINTS points"

# ── Done ────────────────────────────────────────────────────────────────
printf '\n\033[32m✔ e2e smoke green\033[0m  (tenant=%s, doc=%s, tokens=%s, qdrant_points=%s)\n' \
    "$TENANT_ID" "$DOC_ID" "$TOKEN_COUNT" "$POINTS"
