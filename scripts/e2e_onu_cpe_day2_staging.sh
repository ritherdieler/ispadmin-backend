#!/usr/bin/env bash
# Core day-2 via Gateway: 360, wifi-refresh, reboot. Does not call ACS WAR or GenieACS.
# Usage:
#   E2E_SUBSCRIPTION_ID=2360 ./scripts/e2e_onu_cpe_day2_staging.sh
#   E2E_ONU_SN=ZTEGDC47BFFD ./scripts/e2e_onu_cpe_day2_staging.sh
set -euo pipefail

API_BASE="${API_BASE:-https://api.gigafiberperu.cloud/ispadmin-staging}"
E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"
E2E_ONU_SN="${E2E_ONU_SN:-}"
E2E_SUBSCRIPTION_ID="${E2E_SUBSCRIPTION_ID:-}"

TOKEN="$(curl -sS -X POST "$API_BASE/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$E2E_USER\",\"password\":\"$E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken") or "")')"
[[ -n "$TOKEN" ]] || { echo "Staging login failed" >&2; exit 1; }

if [[ -z "$E2E_SUBSCRIPTION_ID" && -n "${E2E_DNI:-}" ]]; then
  export E2E_DNI
  E2E_SUBSCRIPTION_ID="$(curl -sS -H "Authorization: Bearer $TOKEN" "$API_BASE/subscription/find/dni?dni=$E2E_DNI" \
    | python3 -c "
import json,sys
data=json.load(sys.stdin)
items=data if isinstance(data,list) else data.get('data') or data.get('content') or []
print((items[0].get('id') if items else '') or '')
" )"
fi
[[ -n "$E2E_SUBSCRIPTION_ID" ]] || { echo "Need E2E_SUBSCRIPTION_ID or E2E_DNI (GET /subscription is POST-only)" >&2; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN")

echo "== GET service-health 360 =="
curl -sS "${auth[@]}" "$API_BASE/subscription/${E2E_SUBSCRIPTION_ID}/service-health" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("360 keys", sorted(d.keys())[:12] if isinstance(d,dict) else type(d))'

echo "== POST wifi-refresh =="
curl -sS -X POST "${auth[@]}" "$API_BASE/subscription/${E2E_SUBSCRIPTION_ID}/acs/wifi-refresh" | python3 -c 'import json,sys; print(json.load(sys.stdin))'

echo "== POST reboot (360) =="
curl -sS -X POST "${auth[@]}" "$API_BASE/subscription/${E2E_SUBSCRIPTION_ID}/service-health/reboot" | python3 -c 'import json,sys; print(json.load(sys.stdin))'

echo "E2E_ONU_CPE_DAY2_OK"
