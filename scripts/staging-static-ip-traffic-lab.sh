#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export CORE_BASE="${CORE_BASE:-https://api.gigafiberperu.cloud/ispadmin-staging}"
export HOST_DEVICE_ID="${HOST_DEVICE_ID:-8}"
export E2E_ADDRESS="${E2E_ADDRESS:-lab staging STATIC_IP traffic}"
exec "$ROOT/scripts/prestaging-static-ip-traffic-lab.sh" "$@"
