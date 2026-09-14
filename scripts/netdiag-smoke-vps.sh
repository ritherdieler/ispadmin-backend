#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MK1_HOST="${ROUTEROS_MK1_HOST:-38.224.231.2}"
NETDIAG_BASE="${NETDIAG_BASE_URL:-http://127.0.0.1:8080/ispadmin/api/netdiag}"
NETDIAG_KEY="${NETDIAG_API_KEY:-dev-netdiag-key}"

echo "=== NetDiag smoke (VPS / allowlisted host) ==="
echo "MK1: $MK1_HOST"
echo "API: $NETDIAG_BASE"
echo

echo "--- 1. Puertos MK1 ---"
for port in 8728 443; do
  if nc -z -w 3 "$MK1_HOST" "$port" 2>/dev/null; then
    echo "  puerto $port: ABIERTO"
  else
    echo "  puerto $port: CERRADO (revisar firewall/allowlist)"
  fi
done
echo

if [[ -z "${ROUTEROS_MK1_USER:-}" || -z "${ROUTEROS_MK1_PASSWORD:-}" ]]; then
  echo "WARN: ROUTEROS_MK1_USER/PASSWORD no definidos — omitiendo live-mk1 Gradle"
else
  echo "--- 2. Contract tests live MK1 ---"
  ./gradlew :shared:test --tests "*LegrangeClassicAdapterTest" --tests "*RouterOs7RestAdapterTest"
  echo "  live-mk1: OK"
fi
echo

echo "--- 3. Suite NetDiag unitaria ---"
./gradlew test --tests "*NetDiag*" --tests "*RouterOs*" --tests "*Legrange*" --tests "*MikroTikConnection*"
echo "  netdiag unit: OK"
echo

echo "--- 4. Health API ---"
curl -sf "$NETDIAG_BASE/health" | head -c 200
echo
echo

if [[ -n "${NETDIAG_KEY:-}" ]]; then
  echo "--- 5. Incidents API (auth) ---"
  curl -sf -H "X-Netdiag-Key: $NETDIAG_KEY" "$NETDIAG_BASE/incidents" | head -c 400
  echo
fi

echo
echo "=== Smoke terminado ==="
echo "Prerrequisitos ops: net.diag.enabled=true, www-ssl en MK1, seed netdiag-mikrotik-seed.rsc"
echo "Docs: .agent-docs/netdiag-mikrotik-ros7.md"
