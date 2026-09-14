#!/usr/bin/env bash
set -euo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/gigafiber/genieacs}"
FAIL=0

pass() { echo "OK  $1"; }
fail() { echo "FAIL $1"; FAIL=1; }

echo "==> Verificacion piloto GenieACS (aislado Docker)"

if docker compose -f "${REMOTE_DIR}/docker-compose.yml" ps --status running 2>/dev/null | grep -q gigafiber-genieacs; then
  pass "contenedor genieacs running"
else
  fail "contenedor genieacs running"
fi

if docker compose -f "${REMOTE_DIR}/docker-compose.yml" ps --status running 2>/dev/null | grep -q gigafiber-genieacs-mongo; then
  pass "contenedor mongo running"
else
  fail "contenedor mongo running"
fi

if ss -tlnp 2>/dev/null | grep -q '127.0.0.1:7547'; then
  pass "CWMP solo en 127.0.0.1:7547"
else
  fail "CWMP solo en 127.0.0.1:7547"
fi

if ss -tlnp 2>/dev/null | grep -E '0.0.0.0:27017|\[::\]:27017' >/dev/null; then
  fail "MongoDB no expuesto al host"
else
  pass "MongoDB no expuesto al host"
fi

if docker network inspect genieacs_pilot_internal >/dev/null 2>&1; then
  pass "red Docker dedicada genieacs_pilot_internal"
else
  fail "red Docker dedicada genieacs_pilot_internal"
fi

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:7547/ || true)
if [[ "$HTTP_CODE" =~ ^(400|405|404)$ ]]; then
  pass "CWMP responde local (HTTP ${HTTP_CODE})"
else
  fail "CWMP responde local (HTTP ${HTTP_CODE})"
fi

if curl -sf -o /dev/null http://127.0.0.1:3000; then
  pass "health UI interno :3000"
else
  fail "health UI interno :3000"
fi

if [[ -f /etc/nginx/sites-enabled/acs.gigafiberperu.cloud.conf ]]; then
  pass "vhost nginx ACS habilitado"
else
  fail "vhost nginx ACS habilitado"
fi

HTTPS_CODE=$(curl -s -o /dev/null -w "%{http_code}" https://acs.gigafiberperu.cloud/ || true)
if [[ "$HTTPS_CODE" =~ ^(200|400|405|404)$ ]]; then
  pass "HTTPS acs.gigafiberperu.cloud (HTTP ${HTTPS_CODE})"
else
  fail "HTTPS acs.gigafiberperu.cloud (HTTP ${HTTPS_CODE})"
fi

if [[ -f /etc/nginx/sites-enabled/genieacs-ui-on-acs.conf ]]; then
  pass "vhost nginx UI GenieACS habilitado"
  UI_CODE=$(curl -sk -o /dev/null -w "%{http_code}" https://127.0.0.1:8443/ --resolve acs.gigafiberperu.cloud:8443:127.0.0.1 || true)
  if [[ "$UI_CODE" =~ ^(200|403)$ ]]; then
    pass "UI GenieACS :8443 responde local (HTTP ${UI_CODE})"
  else
    fail "UI GenieACS :8443 responde local (HTTP ${UI_CODE})"
  fi
else
  fail "vhost nginx UI GenieACS habilitado"
fi

exit "$FAIL"
