#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [[ -z "${OLT_GATEWAY_SNMP_RO_COMMUNITY:-}" ]]; then
  echo "Set OLT_GATEWAY_SNMP_RO_COMMUNITY" >&2
  exit 1
fi

export OLT_SNMP_MAXREP_AB="${OLT_SNMP_MAXREP_AB:-true}"
export OLT_GATEWAY_HOST="${OLT_GATEWAY_HOST:-10.11.104.2}"
export OLT_GATEWAY_SNMP_PORT="${OLT_GATEWAY_SNMP_PORT:-161}"
export OLT_GATEWAY_SNMP_POLL_LOCK_KEY="${OLT_GATEWAY_SNMP_POLL_LOCK_KEY:-olt-snmp-poll}"

echo "A/B GETBULK port 1/6 maxRep 15 vs 10 timeout 15s retries 1 lock=${OLT_GATEWAY_SNMP_POLL_LOCK_KEY} parallel=1"
echo "REDIS_HOST=${REDIS_HOST:-unset} (shared key, no 3-lane)"

exec ./gradlew :oltgateway:test --tests "OpticalMaxRepAbLiveSmokeTest"
