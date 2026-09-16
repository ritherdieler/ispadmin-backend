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
deadline=$((SECONDS + POLL_SECONDS))
olt=""
cpe=""
attempt=0
url="$API_BASE/subscription/$E2E_SUBSCRIPTION_ID"
while (( SECONDS < deadline )); do
  attempt=$((attempt + 1))
  e2e_retry "TR-069 poll $attempt GET $url"
  set +e
  body="$(e2e_http GET "$url" --max-time 60 -H "Authorization: Bearer $TOKEN")"
  http_ec=$?
  set -e
  if [[ "$http_ec" -ne 0 ]]; then
    sleep 5
    continue
  fi
  parsed="$(E2E_ONU_BODY="$body" python3 -c '
import json, os
raw = os.environ.get("E2E_ONU_BODY") or ""
try:
    payload = json.loads(raw)
except Exception:
    print("||")
    raise SystemExit
data = payload.get("data") if isinstance(payload, dict) and "data" in payload else payload
if not isinstance(data, dict):
    data = {}
onu = data.get("fiberOnu") if isinstance(data.get("fiberOnu"), dict) else {}
print("%s|%s|%s" % (
    data.get("oltProvisionStatus") or "",
    data.get("tr069ProvisionStatus") or "",
    onu.get("uniqueExternalId") or "",
))
')"
  olt="${parsed%%|*}"
  rest="${parsed#*|}"
  cpe="${rest%%|*}"
  ext="${rest#*|}"
  echo "TR069: subscription=$E2E_SUBSCRIPTION_ID olt=$olt cpe=$cpe unique_external_id=$ext attempt=$attempt"
  if [[ "$olt" == "COMPLETE" && ( "$cpe" == "PENDING" || "$cpe" == "COMPLETE" || "$cpe" == "FAILED" ) ]]; then
    if [[ "$cpe" == "PENDING" ]]; then
      e2e_retry "TR-069 still PENDING subscription=$E2E_SUBSCRIPTION_ID"
      sleep 5
      continue
    fi
    break
  fi
  e2e_retry "waiting olt COMPLETE / cpe terminal last olt=$olt cpe=$cpe"
  sleep 5
done
if [[ "$olt" != "COMPLETE" || ( "$cpe" != "COMPLETE" && "$cpe" != "FAILED" ) ]]; then
  echo "timeout waiting olt COMPLETE and cpe terminal/pending last olt=$olt cpe=$cpe" >&2
  exit 1
fi
echo "E2E_ONU_ACTIVATION_STATUS_OK"
