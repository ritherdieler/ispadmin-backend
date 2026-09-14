#!/usr/bin/env bash
# Live smoke del cliente SNMP RO (Kotlin) contra la MA5608T.
# No imprime ni guarda la community.
#
# Uso:
#   export OLT_GATEWAY_SNMP_RO_COMMUNITY='...'   # display snmp-agent community read
#   ./scripts/olt-snmp-client-smoke.sh
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${OLT_GATEWAY_SNMP_RO_COMMUNITY:-}" ]]; then
  echo "ERROR: export OLT_GATEWAY_SNMP_RO_COMMUNITY first (do not commit it)"
  exit 1
fi

HOST="${OLT_GATEWAY_HOST:-10.11.104.2}"
export OLT_SNMP_LIVE=true
export OLT_GATEWAY_HOST="$HOST"

echo "== CLI ping SNMP $HOST =="
snmpget -v2c -c "$OLT_GATEWAY_SNMP_RO_COMMUNITY" -t 3 -r 1 -On -OQ "$HOST" 1.3.6.1.2.1.1.2.0

echo "== Kotlin Snmp4jOltSnmpClientLiveSmokeTest =="
./gradlew :oltgateway:test --tests "*Snmp4jOltSnmpClientLiveSmokeTest"

echo "DONE"
