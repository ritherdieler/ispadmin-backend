#!/usr/bin/env bash
# Poll Core flags after a FIBER alta (olt COMPLETE, cpe PENDING then COMPLETE/FAILED).
# Usage:
#   E2E_ONU_SN=ZTEGDC47BFFD ./scripts/e2e_onu_activation_status_staging.sh
#   E2E_SUBSCRIPTION_ID=2360 ./scripts/e2e_onu_activation_status_staging.sh
set -euo pipefail

API_BASE="${API_BASE:-https://api.gigafiberperu.cloud/ispadmin-staging}"
E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"
E2E_ONU_SN="${E2E_ONU_SN:-}"
E2E_SUBSCRIPTION_ID="${E2E_SUBSCRIPTION_ID:-}"
POLL_SECONDS="${POLL_SECONDS:-90}"

TOKEN="$(curl -sS -X POST "$API_BASE/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$E2E_USER\",\"password\":\"$E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken") or "")')"
[[ -n "$TOKEN" ]] || { echo "Staging login failed" >&2; exit 1; }

if [[ -z "$E2E_SUBSCRIPTION_ID" && -n "${E2E_DNI:-}" ]]; then
  export E2E_DNI
  export E2E_ONU_SN
  E2E_SUBSCRIPTION_ID="$(curl -sS -H "Authorization: Bearer $TOKEN" "$API_BASE/subscription/find/dni?dni=$E2E_DNI" \
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

python3 - "$API_BASE" "$TOKEN" "$E2E_SUBSCRIPTION_ID" "$POLL_SECONDS" <<'PY'
import json, sys, time, urllib.request

base, token, sub_id, timeout = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
deadline = time.time() + timeout
olt = cpe = None
while time.time() < deadline:
    req = urllib.request.Request(
        f"{base}/subscription/{sub_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(req) as resp:
        body = json.load(resp)
    data = body.get("data") if isinstance(body, dict) else body
    olt = (data or {}).get("oltProvisionStatus")
    cpe = (data or {}).get("tr069ProvisionStatus")
    ext = ((data or {}).get("fiberOnu") or {}).get("uniqueExternalId")
    print(f"subscription={sub_id} olt={olt} cpe={cpe} unique_external_id={ext}")
    if olt == "COMPLETE" and cpe in ("PENDING", "COMPLETE", "FAILED"):
        if cpe == "PENDING":
            time.sleep(5)
            continue
        break
    time.sleep(5)
else:
    raise SystemExit("timeout waiting olt COMPLETE and cpe terminal/pending")

if olt != "COMPLETE":
    raise SystemExit(f"expected olt COMPLETE, got {olt}")
print("E2E_ONU_ACTIVATION_STATUS_OK")
PY
