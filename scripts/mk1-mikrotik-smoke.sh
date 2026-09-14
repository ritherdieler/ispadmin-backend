#!/usr/bin/env bash
set -euo pipefail

ISP_BASE="${ISP_BASE:-http://127.0.0.1:8080/ispadmin}"
ISP_USER="${ISP_USER:-dscorp}"
ISP_PASS="${ISP_PASS:-}"
DEVICE_ID="${DEVICE_ID:-1}"
MUTATE_ENTRY_ID="${MUTATE_ENTRY_ID:-}"
RUN_MUTATE="${RUN_MUTATE:-false}"

if [[ -z "$ISP_PASS" ]]; then
  echo "ISP_PASS is required (API login password)." >&2
  exit 1
fi

login_json="$(curl -sf -X POST "$ISP_BASE/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ISP_USER\",\"password\":\"$ISP_PASS\"}")"
TOKEN="$(printf '%s' "$login_json" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")"
AUTH=(-H "Authorization: Bearer $TOKEN")

pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*" >&2; exit 1; }

http_code() {
  curl -sf -o /dev/null -w '%{http_code}' "${AUTH[@]}" "$@"
}

echo "=== Mikrotik smoke (device $DEVICE_ID) ==="

for path in \
  "/api/filter-rules/debt-cut/$DEVICE_ID" \
  "/networkDevice/connection/$DEVICE_ID/system-info" \
  "/networkDevice/connection/$DEVICE_ID/interfaces" \
  "/networkDevice/connection/$DEVICE_ID/resources" \
  "/networkDevice/connection/$DEVICE_ID/info" \
  "/networkDevice/connection/cloud-core-routers"; do
  code="$(http_code "$ISP_BASE$path")"
  [[ "$code" == "200" ]] && pass "GET $path ($code)" || fail "GET $path ($code)"
done

code="$(http_code "$ISP_BASE/api/netdiag/health")"
[[ "$code" == "200" ]] && pass "GET /api/netdiag/health ($code)" || fail "GET /api/netdiag/health ($code)"

if [[ "$RUN_MUTATE" == "true" ]]; then
  if [[ -z "$MUTATE_ENTRY_ID" ]]; then
    MUTATE_ENTRY_ID="$(curl -sf "${AUTH[@]}" "$ISP_BASE/api/filter-rules/debt-cut/$DEVICE_ID" \
      | python3 -c "import sys,json; rows=json.load(sys.stdin); print(rows[0]['id'] if rows else '')")"
  fi
  if [[ -z "$MUTATE_ENTRY_ID" ]]; then
    fail "RUN_MUTATE=true but no address-list entry id available"
  fi
  body="{\"deviceId\":$DEVICE_ID,\"ruleIds\":[\"$MUTATE_ENTRY_ID\"]}"
  curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$ISP_BASE/api/filter-rules/disable" -d "$body" >/dev/null
  disabled="$(curl -sf "${AUTH[@]}" "$ISP_BASE/api/filter-rules/debt-cut/$DEVICE_ID" \
    | python3 -c "import sys,json; eid=sys.argv[1]; rows=json.load(sys.stdin); m={r['id']:r['disabled'] for r in rows}; print(m.get(eid))" "$MUTATE_ENTRY_ID")"
  [[ "$disabled" == "True" ]] && pass "disable $MUTATE_ENTRY_ID" || fail "disable $MUTATE_ENTRY_ID"
  curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$ISP_BASE/api/filter-rules/enable" -d "$body" >/dev/null
  disabled="$(curl -sf "${AUTH[@]}" "$ISP_BASE/api/filter-rules/debt-cut/$DEVICE_ID" \
    | python3 -c "import sys,json; eid=sys.argv[1]; rows=json.load(sys.stdin); m={r['id']:r['disabled'] for r in rows}; print(m.get(eid))" "$MUTATE_ENTRY_ID")"
  [[ "$disabled" == "False" ]] && pass "enable $MUTATE_ENTRY_ID (restored)" || fail "enable $MUTATE_ENTRY_ID"
fi

if [[ -n "${MK1_USER:-}" && -n "${MK1_PASS:-}" && -n "${MK1_HOST:-}" ]]; then
  python3 - "$MK1_HOST" "$MK1_USER" "$MK1_PASS" "$ISP_BASE" "$TOKEN" "$DEVICE_ID" <<'PY'
import base64, json, ssl, sys, urllib.request
host, user, pwd, base, token, device_id = sys.argv[1:7]
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()
req = urllib.request.Request(
    f"https://{host}/rest/ip/firewall/address-list?list=deudores",
    headers={"Authorization": f"Basic {auth}"},
)
mk = json.loads(urllib.request.urlopen(req, context=ctx, timeout=30).read())
api_req = urllib.request.Request(
    f"{base}/api/filter-rules/debt-cut/{device_id}",
    headers={"Authorization": f"Bearer {token}"},
)
api = json.loads(urllib.request.urlopen(api_req, timeout=30).read())
mk_ids = {r[".id"] for r in mk}
api_ids = {r["id"] for r in api}
if mk_ids == api_ids and len(mk) == len(api):
    print(f"PASS  MK1 REST vs API deudores ({len(api)} entries, ids match)")
else:
    print(f"FAIL  MK1 REST vs API deudores (mk={len(mk)} api={len(api)})", file=sys.stderr)
    sys.exit(1)
PY
fi

echo "=== done ==="
