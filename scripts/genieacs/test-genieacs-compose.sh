#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.genieacs.yml"
FAIL=0

pass() { echo "OK  $1"; }
fail() { echo "FAIL $1"; FAIL=1; }

echo "==> Test compose piloto GenieACS (aislamiento Docker)"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  fail "existe docker-compose.genieacs.yml"
  exit 1
fi
pass "existe docker-compose.genieacs.yml"

CONTENT="$(cat "$COMPOSE_FILE")"

if grep -q 'name: genieacs-pilot' "$COMPOSE_FILE"; then
  pass "proyecto compose dedicado genieacs-pilot"
else
  fail "proyecto compose dedicado genieacs-pilot"
fi

if grep -q '127.0.0.1:7547:7547' "$COMPOSE_FILE"; then
  pass "CWMP bind localhost"
else
  fail "CWMP bind localhost"
fi

if grep -q '127.0.0.1:7557:7557' "$COMPOSE_FILE"; then
  pass "NBI bind localhost"
else
  fail "NBI bind localhost"
fi

if grep -q '127.0.0.1:7567:7567' "$COMPOSE_FILE"; then
  pass "UI bind localhost"
else
  fail "UI bind localhost"
fi

if ! grep -E '^[[:space:]]*-[[:space:]]*"27017:27017"' "$COMPOSE_FILE" >/dev/null; then
  pass "Mongo sin puerto publicado al host"
else
  fail "Mongo sin puerto publicado al host"
fi

if grep -q 'name: genieacs_pilot_internal' "$COMPOSE_FILE"; then
  pass "red interna dedicada"
else
  fail "red interna dedicada"
fi

if grep -q 'MONGO_INITDB_ROOT_USERNAME' "$COMPOSE_FILE"; then
  pass "Mongo con autenticacion"
else
  fail "Mongo con autenticacion"
fi

if grep -q 'memory: 1536M' "$COMPOSE_FILE"; then
  pass "limite memoria genieacs"
else
  fail "limite memoria genieacs"
fi

if grep -q 'memory: 768M' "$COMPOSE_FILE"; then
  pass "limite memoria mongo"
else
  fail "limite memoria mongo"
fi

if MONGO_ROOT_USER=genieacs MONGO_ROOT_PASSWORD=test GENIEACS_UI_JWT_SECRET=test \
  docker compose -f "$COMPOSE_FILE" config >/dev/null 2>&1; then
  pass "docker compose config valido"
else
  fail "docker compose config valido"
fi

exit "$FAIL"
