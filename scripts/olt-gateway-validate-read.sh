#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OLT_GATEWAY_BASE_URL:-http://localhost:8080/ispadmin/api/olt-gateway}"
API_KEY="${OLT_GATEWAY_API_KEY:-dev-olt-gateway-key}"
SAMPLE_SN="${OLT_GATEWAY_SAMPLE_SN:-ZTEGDC47DF15}"
SAMPLE_SLOT="${OLT_GATEWAY_SAMPLE_SLOT:-1}"
SAMPLE_PORT="${OLT_GATEWAY_SAMPLE_PORT:-7}"
SAMPLE_ONT_ID="${OLT_GATEWAY_SAMPLE_ONT_ID:-1}"
CONFIGURED_PAGE_SIZE="${OLT_GATEWAY_CONFIGURED_PAGE_SIZE:-50}"

PASS=0
FAIL=0
QUICK=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick) QUICK=true; shift ;;
    -h|--help)
      cat <<'EOF'
Usage: olt-gateway-validate-read.sh [--quick]

  --quick   Omite GET /onus (~15-60s SSH). Resto de lectura en ~5s.

Env:
  OLT_GATEWAY_BASE_URL, OLT_GATEWAY_API_KEY
  OLT_GATEWAY_SAMPLE_SN (default ZTEGDC47DF15)
  OLT_GATEWAY_SAMPLE_SLOT/PORT/ONT_ID (default 1/7/1)
EOF
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

header() {
  printf '\n━━━ %s ━━━\n' "$1"
}

check_http() {
  local name="$1"
  local method="$2"
  local path="$3"
  local expect="${4:-200}"
  local auth="${5:-yes}"
  local tmp
  tmp="$(mktemp)"
  local curl_args=(-sS -o "$tmp" -w "%{http_code} %{time_total}")
  if [[ "$method" == "POST" ]]; then
    curl_args+=(-X POST)
  fi
  if [[ "$auth" == "yes" ]]; then
    curl_args+=(-H "X-Olt-Gateway-Key: ${API_KEY}")
  fi
  local result
  if ! result="$(curl "${curl_args[@]}" "${BASE_URL}${path}")"; then
    printf '  ✗ %s — curl error\n' "$name"
    FAIL=$((FAIL + 1))
    rm -f "$tmp"
    return
  fi
  local code time_total
  code="${result%% *}"
  time_total="${result#* }"
  if [[ "$code" != "$expect" ]]; then
    printf '  ✗ %s — HTTP %s (expected %s) in %ss\n' "$name" "$code" "$expect" "$time_total"
    head -c 300 "$tmp" | tr '\n' ' '
    printf '\n'
    FAIL=$((FAIL + 1))
    rm -f "$tmp"
    return
  fi
  local summary
  summary="$(python3 - "$tmp" "$name" <<'PY'
import json, sys
path, name = sys.argv[1], sys.argv[2]
try:
    with open(path) as f:
        data = json.load(f)
except json.JSONDecodeError:
    print("invalid JSON")
    sys.exit(0)

def pick(d):
    if name == "health":
        return f"status={d.get('status')} reachable={d.get('oltReachable')} latencyMs={d.get('latencyMs')}"
    if name == "olt-info":
        boards = d.get("boards") or []
        return f"product={d.get('product')} boards={len(boards)} model={d.get('modelCode')}"
    if name == "autofind":
        items = d.get("response") or d.get("items") or []
        if isinstance(items, dict):
            items = items.get("onus") or []
        return f"items={len(items)}"
    if name == "by-sn":
        onus = d.get("onus") or []
        sn = onus[0].get("sn") if onus else d.get("sn")
        return f"sn={sn} status={d.get('status')}"
    if name == "list-onus":
        return f"total={d.get('total')}"
    if name == "configured":
        items = d.get("items") or []
        with_signal = sum(1 for i in items if i.get("oltRxDbm") is not None)
        return f"totalElements={d.get('totalElements')} pageItems={len(items)} withSignal={with_signal}"
    if name == "onu-detail":
        return f"sn={d.get('sn')} runState={d.get('runState')} slot={d.get('slot')}/{d.get('port')}/{d.get('ontId')}"
    if name == "optical":
        return f"rx={d.get('rxPowerDbm')} oltRx={d.get('oltRxPowerDbm')} temp={d.get('temperatureC')}"
    if name == "sync-status":
        inv = d.get("lastResult") or {}
        return (
            f"invRunning={d.get('running')} invUnchanged={inv.get('unchanged')} "
            f"sigRunning={d.get('signalRunning')} busDepth={d.get('busQueueDepth')}"
        )
    if name == "unconfigured-alias":
        items = d.get("response") or []
        return f"items={len(items)}"
    if name == "details-by-sn-alias":
        onus = d.get("onus") or []
        return f"onus={len(onus)} status={d.get('status')}"
    return "ok"
print(pick(data))
PY
)"
  printf '  ✓ %s — HTTP %s in %ss — %s\n' "$name" "$code" "$time_total" "$summary"
  PASS=$((PASS + 1))
  rm -f "$tmp"
}

main() {
  printf 'OLT Gateway read validation\n'
  printf '  BASE_URL=%s\n' "$BASE_URL"
  printf '  SAMPLE_SN=%s position=%s/%s/%s\n' "$SAMPLE_SN" "$SAMPLE_SLOT" "$SAMPLE_PORT" "$SAMPLE_ONT_ID"

  header "Health (sin API key)"
  check_http "health" "GET" "/health" "200" "no"

  header "Lectura nativa"
  check_http "olt-info" "GET" "/olt/info"
  check_http "autofind" "GET" "/onus/autofind"
  check_http "by-sn" "GET" "/onus/by-sn/${SAMPLE_SN}"
  if [[ "$QUICK" == "true" ]]; then
    printf '  ⊘ list-onus — skipped (--quick)\n'
  else
    check_http "list-onus" "GET" "/onus"
  fi
  check_http "configured" "GET" "/onus/configured?page=0&size=${CONFIGURED_PAGE_SIZE}"
  check_http "onu-detail" "GET" "/onus/${SAMPLE_SLOT}/${SAMPLE_PORT}/${SAMPLE_ONT_ID}"
  check_http "optical" "GET" "/onus/${SAMPLE_SLOT}/${SAMPLE_PORT}/${SAMPLE_ONT_ID}/optical"
  check_http "sync-status" "GET" "/admin/sync/status"

  header "Aliases SmartOLT (lectura)"
  check_http "unconfigured-alias" "GET" "/onu/unconfigured_onus"
  check_http "details-by-sn-alias" "GET" "/onu/get_onus_details_by_sn/${SAMPLE_SN}"

  header "Resumen"
  printf '  PASS=%s FAIL=%s\n' "$PASS" "$FAIL"
  if [[ "$FAIL" -gt 0 ]]; then
    exit 1
  fi
}

main "$@"
