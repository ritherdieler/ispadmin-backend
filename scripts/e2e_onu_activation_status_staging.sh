#!/usr/bin/env bash
# Poll Core flags after a FIBER alta (olt COMPLETE, cpe PENDING then COMPLETE/FAILED).
# Usage:
#   E2E_ONU_SN=ZTEGDC47BFFD ./scripts/e2e_onu_activation_status_staging.sh
#   E2E_SUBSCRIPTION_ID=2360 ./scripts/e2e_onu_activation_status_staging.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e_console.sh"

API_BASE="${API_BASE:-https://api.gigafiberperu.cloud/ispadmin-staging}"
E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"
E2E_ONU_SN="${E2E_ONU_SN:-}"
E2E_SUBSCRIPTION_ID="${E2E_SUBSCRIPTION_ID:-}"
POLL_SECONDS="${POLL_SECONDS:-90}"

e2e_step "login staging status poll"
TOKEN="$(e2e_http POST "$API_BASE/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$E2E_USER\",\"password\":\"$E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken") or "")')"
[[ -n "$TOKEN" ]] || { echo "Staging login failed" >&2; exit 1; }
e2e_doing "login ok user=$E2E_USER"

if [[ -z "$E2E_SUBSCRIPTION_ID" && -n "${E2E_DNI:-}" ]]; then
  export E2E_DNI
  export E2E_ONU_SN
  e2e_step "resolve subscription by dni"
  E2E_SUBSCRIPTION_ID="$(e2e_http GET "$API_BASE/subscription/find/dni?dni=$E2E_DNI" \
    -H "Authorization: Bearer $TOKEN" \
    | python3 -c "
import json,sys,os
sn=(os.environ.get('E2E_ONU_SN') or '').upper()
data=json.load(sys.stdin)
items=data if isinstance(data,list) else data.get('data') or data.get('content') or []
for s in items:
    onu=(s.get('fiberOnu') or s.get('onu') or {})
    if not sn or str(onu.get('sn') or '').upper()==sn or str(s.get('dni') or '')==os.environ.get('E2E_DNI',''):
        print(s.get('id') or '')
        break
" )"
fi
if [[ -z "$E2E_SUBSCRIPTION_ID" && -n "$E2E_ONU_SN" ]]; then
  echo "Need E2E_SUBSCRIPTION_ID or E2E_DNI (GET /subscription is POST-only)" >&2
  exit 1
fi

e2e_step "poll TR-069 COMPLETE subscription=$E2E_SUBSCRIPTION_ID"
python3 - "$API_BASE" "$TOKEN" "$E2E_SUBSCRIPTION_ID" "$POLL_SECONDS" <<'PY'
import json, sys, time, urllib.error, urllib.request

base, token, sub_id, timeout = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
deadline = time.time() + timeout
olt = cpe = None
attempt = 0
url = f"{base}/subscription/{sub_id}"
while time.time() < deadline:
    attempt += 1
    print(f"RETRY: TR-069 poll {attempt} GET {url}", file=sys.stderr)
    print(f"DOING: HTTP GET {url}", file=sys.stderr)
    print(f"URL: GET {url}", file=sys.stderr)
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            code = resp.status
            body = json.loads(raw.decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read()
        code = e.code
        print(f"HTTP: {code}", file=sys.stderr)
        print(f"BODY: {raw[:1200]!r}", file=sys.stderr)
        print(f"HTTP_FAIL: GET {url} code={code}", file=sys.stderr)
        time.sleep(5)
        continue
    except Exception as e:
        print("HTTP: 000", file=sys.stderr)
        print(f"HTTP_FAIL: GET {url} {e}", file=sys.stderr)
        time.sleep(5)
        continue
    print(f"HTTP: {code}", file=sys.stderr)
    clip = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    if len(clip) > 1200:
        clip = clip[:1200] + "...<clipped>"
    print(f"BODY: {clip}", file=sys.stderr)
    data = body.get("data") if isinstance(body, dict) else body
    olt = (data or {}).get("oltProvisionStatus")
    cpe = (data or {}).get("tr069ProvisionStatus")
    ext = ((data or {}).get("fiberOnu") or {}).get("uniqueExternalId")
    print(f"TR069: subscription={sub_id} olt={olt} cpe={cpe} unique_external_id={ext} attempt={attempt}")
    if olt == "COMPLETE" and cpe in ("PENDING", "COMPLETE", "FAILED"):
        if cpe == "PENDING":
            print(f"RETRY: TR-069 still PENDING subscription={sub_id}", file=sys.stderr)
            time.sleep(5)
            continue
        break
    print(f"RETRY: waiting olt COMPLETE / cpe terminal last olt={olt} cpe={cpe}", file=sys.stderr)
    time.sleep(5)
else:
    raise SystemExit("timeout waiting olt COMPLETE and cpe terminal/pending")

if olt != "COMPLETE":
    raise SystemExit(f"expected olt COMPLETE, got {olt}")
print("E2E_ONU_ACTIVATION_STATUS_OK")
PY
