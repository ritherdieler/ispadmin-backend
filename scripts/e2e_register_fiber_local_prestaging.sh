#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties"
EXAMPLE="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties.example"
MYSQL="${MYSQL_BIN:-/opt/homebrew/opt/mysql-client/bin/mysql}"

CORE="${CORE_BASE:-http://127.0.0.1:8082/ispadmin}"
GW="${GATEWAY_BASE:-http://127.0.0.1:8082/ispadmin}"
ACS="${ACS_BASE:-http://127.0.0.1:8082/ispadmin}"
NBI="${NBI_BASE:-http://127.0.0.1:7557}"
export CORE GW ACS NBI
OLT_HOST="${OLT_HOST:-10.11.104.2}"
HOST_DEVICE_ID="${HOST_DEVICE_ID:-8}"
VLAN="${VLAN:-100}"
NAP_BOX_ID="${NAP_BOX_ID:-42}"
POLL_SECONDS="${POLL_SECONDS:-180}"

E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"
E2E_ONU_SN="${E2E_ONU_SN:-ZTEGDC47BFFD}"
E2E_DNI="${E2E_DNI:-$(python3 -c 'import time; print("9"+("%07d"%(time.time()%10000000)))')}"
E2E_FIRST_NAME="${E2E_FIRST_NAME:-EeeFiber}"
E2E_LAST_NAME="${E2E_LAST_NAME:-Prestaging}"
E2E_PHONE="${E2E_PHONE:-999000042}"
E2E_ADDRESS="${E2E_ADDRESS:-lab prestaging e2e}"

CLI_WIFI_SSID=""
CLI_WIFI_PASS=""
CLEANUP_MODE="${CLEANUP_MODE:-skip}"
RUN_CHECK_ONLY=0

usage() {
  cat <<EOF
Alta FIBER lab contra Core local-prestaging (no Gateway directo, no ACS VPS).

  $(basename "$0") --wifi-ssid 'mimiwifi' --wifi-pass 'MimiWifi24pass'
  $(basename "$0") --wifi-ssid 'mimiwifi' --wifi-pass 'MimiWifi24pass' --cleanup-mode ask
  $(basename "$0") check

Prerrequisitos (WAR único ya arriba):
  ./scripts/run-local-prestaging.sh check
  ./scripts/run-local-prestaging.sh start

Stack: WAR único :8082 /ispadmin (Core+Gateway+ACS), NBI 127.0.0.1:7557,
OLT $OLT_HOST, ONU lab $E2E_ONU_SN, MK2 hostDeviceId $HOST_DEVICE_ID, VLAN $VLAN, NAP $NAP_BOX_ID
(board 1 / port 6). Schema ispadmin_prestaging. 5 GHz SSID = "{ssid} - 5G".

--cleanup-mode ask|auto|skip (default skip). Aliases: --ask-cleanup, --auto-cleanup, --no-cleanup.
Solo limpia la ONU lab. Carpeta /scripts/ está gitignored: al commitear usa git add -f.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    check|--check)
      RUN_CHECK_ONLY=1
      shift
      ;;
    --wifi-ssid)
      [[ $# -ge 2 ]] || { echo "--wifi-ssid requires a value" >&2; exit 2; }
      CLI_WIFI_SSID="$2"
      shift 2
      ;;
    --wifi-pass)
      [[ $# -ge 2 ]] || { echo "--wifi-pass requires a value" >&2; exit 2; }
      CLI_WIFI_PASS="$2"
      shift 2
      ;;
    --cleanup-mode)
      [[ $# -ge 2 ]] || { echo "--cleanup-mode requires ask, auto, or skip" >&2; exit 2; }
      case "$2" in
        ask|auto|skip) CLEANUP_MODE="$2" ;;
        *) echo "--cleanup-mode must be ask, auto, or skip" >&2; exit 2 ;;
      esac
      shift 2
      ;;
    --ask-cleanup)
      CLEANUP_MODE=ask
      shift
      ;;
    --cleanup|--auto-cleanup)
      CLEANUP_MODE=auto
      shift
      ;;
    --no-cleanup)
      CLEANUP_MODE=skip
      SKIP_POST_CLEANUP=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

_E2E_WIFI_SSID_DEFAULT="mimiwifi"
_E2E_WIFI_PASS_DEFAULT="MimiWifi24pass"
E2E_WIFI_SSID="${CLI_WIFI_SSID:-${E2E_WIFI_SSID:-$_E2E_WIFI_SSID_DEFAULT}}"
E2E_WIFI_PASS="${CLI_WIFI_PASS:-${E2E_WIFI_PASS:-$_E2E_WIFI_PASS_DEFAULT}}"
if [[ ${#E2E_WIFI_SSID} -lt 1 || ${#E2E_WIFI_SSID} -gt 27 ]]; then
  echo "SSID WiFi must be 1-27 characters (5 GHz adds ' - 5G')" >&2
  exit 2
fi
if [[ ${#E2E_WIFI_PASS} -lt 8 || ${#E2E_WIFI_PASS} -gt 63 ]]; then
  echo "WiFi password must be 8-63 characters (same for 2.4 and 5)" >&2
  exit 2
fi
E2E_WIFI_SSID_5="${E2E_WIFI_SSID} - 5G"

if [[ "$E2E_ONU_SN" != "ZTEGDC47BFFD" ]]; then
  echo "Only lab ONU ZTEGDC47BFFD is allowed (got $E2E_ONU_SN)" >&2
  exit 2
fi

prop() {
  local key="$1"
  [[ -f "$SECRETS" ]] || return 0
  grep "^${key}=" "$SECRETS" | cut -d= -f2-
}

if [[ -z "${GKEY:-}" ]]; then
  GKEY="$(prop olt.gateway.api-key || true)"
fi
if [[ -z "${ACS_API_KEY:-}" ]]; then
  ACS_API_KEY="$(prop acs.api-key || true)"
fi
if [[ -z "${MYSQL_PWD:-}" ]]; then
  MYSQL_PWD="$(prop spring.datasource.password || true)"
  export MYSQL_PWD
fi

run_check() {
  echo "== health Core + Gateway + ACS + NBI + OLT =="
  curl -sS --max-time 15 -o /dev/null -w 'core:%{http_code}\n' "$CORE/actuator/health" | grep -q 'core:200' \
    || { echo "Core no responde en $CORE (arranca ./scripts/run-local-prestaging.sh core)" >&2; exit 1; }
  echo "core:200 $CORE"

  GW_HEALTH="$(curl -sS --max-time 20 -H "X-Olt-Gateway-Key: ${GKEY:-}" "$GW/api/olt-gateway/health")"
  echo "$GW_HEALTH" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d.get("oltReachable") is True, d' \
    || { echo "Gateway oltReachable!=true: $GW_HEALTH" >&2; exit 1; }
  echo "gateway oltReachable=true $GW"

  ACS_HEALTH="$(curl -sS --max-time 15 -H "X-Acs-Key: ${ACS_API_KEY:-}" "$ACS/api/acs/v1/health")"
  echo "$ACS_HEALTH" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d.get("status")=="UP", d' \
    || { echo "ACS no UP en $ACS: $ACS_HEALTH" >&2; exit 1; }
  echo "acs:UP $ACS"

  nc -z 127.0.0.1 7557 >/dev/null 2>&1 \
    || { echo "NBI 127.0.0.1:7557 down (start-genieacs-tunnel.sh)" >&2; exit 1; }
  echo "nbi=up $NBI"

  ping -c 1 -W 2 "$OLT_HOST" >/dev/null \
    || { echo "ping OLT $OLT_HOST failed" >&2; exit 1; }
  echo "olt ping ok $OLT_HOST"
}

core_login() {
  TOKEN="$(curl -sS --max-time 20 -X POST "$CORE/users/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$E2E_USER\",\"password\":\"$E2E_PASSWORD\"}" \
    | python3 -c 'import json,sys
d=json.load(sys.stdin)
print((d.get("accessToken") or ""))
print(d.get("id") or "")
')"
  TECH_ID="$(echo "$TOKEN" | sed -n '2p')"
  TOKEN="$(echo "$TOKEN" | sed -n '1p')"
  [[ -n "$TOKEN" ]] || { echo "Core login failed user=$E2E_USER on $CORE (schema ispadmin_prestaging)" >&2; exit 1; }
}

ensure_e2e_user() {
  [[ -n "${MYSQL_PWD:-}" && -x "$MYSQL" ]] || return 0
  export E2E_USER E2E_PASSWORD MYSQL_BIN="$MYSQL"
  HASH="$(python3 - <<'PY'
import hashlib, os, base64
raw = os.environ["E2E_PASSWORD"]
sha = hashlib.sha384(raw.encode("utf-8")).hexdigest()
salt = os.urandom(16)
dk = hashlib.pbkdf2_hmac("sha256", sha.encode("utf-8"), salt, 120000, dklen=32)
print("pbkdf2$120000$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(dk).decode())
PY
)"
  export HASH
  python3 - <<'PY'
import os, subprocess
user = os.environ["E2E_USER"]
hashed = os.environ["HASH"]
mysql = os.environ.get("MYSQL_BIN", "/opt/homebrew/opt/mysql-client/bin/mysql")
sql = f"""
INSERT INTO user (name, last_name, username, password, type, verified)
SELECT 'Sergio', 'Carrillo Diestra', '{user}', '{hashed}', 'ADMIN', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = '{user}');
UPDATE user
SET password = '{hashed}', type = 'ADMIN', verified = 1
WHERE username = '{user}';
"""
subprocess.check_call([mysql, "-uroot", "ispadmin_prestaging", "-e", sql])
print("ok user", user, "ispadmin_prestaging")
PY
}

lab_tag_ok() {
  local raw
  raw="$(curl -sS --max-time 20 "$NBI/devices/?query=%7B%22_id%22%3A%7B%22%24regex%22%3A%22${E2E_ONU_SN}%22%7D%7D&projection=_id%2C_tags" || true)"
  echo "$raw" | python3 -c 'import json,sys,os
wanted=os.environ["E2E_ONU_SN"]
raw=sys.stdin.read().strip()
if not raw:
    raise SystemExit("NBI empty")
data=json.loads(raw)
items=data if isinstance(data, list) else []
if not items:
    raise SystemExit("ONU not in GenieACS NBI")
tags=items[0].get("_tags") or []
if "lab" not in tags:
    raise SystemExit("_tags missing lab: %s" % tags)
print("ok genieacs", items[0].get("_id"), "_tags", tags)
' || { echo "ONU $E2E_ONU_SN sin tag lab en NBI" >&2; exit 1; }
}

core_onu_external_id() {
  curl -sS --max-time 45 -H "Authorization: Bearer $TOKEN" \
    "$CORE/onu/getBySn?onuSn=$E2E_ONU_SN" | python3 -c '
import json,sys
raw=sys.stdin.read()
try:
  d=json.loads(raw)
except Exception:
  print(""); sys.exit(0)
data=d.get("data") or d
onus=data.get("onus") if isinstance(data, dict) else []
if isinstance(onus, dict):
  onus=onus.get("onus") or []
print((onus[0] or {}).get("unique_external_id") or "" if isinstance(onus, list) and onus else "")
'
}

lab_clean_onu() {
  local sn="$1"
  [[ "$sn" == "ZTEGDC47BFFD" ]] || { echo "refusing cleanup of non-lab sn=$sn" >&2; return 1; }
  local ext
  ext="$(core_onu_external_id || true)"
  if [[ -n "$ext" ]]; then
    echo "delete Core $ext"
    curl -sS --max-time 60 -X DELETE -H "Authorization: Bearer $TOKEN" \
      "$CORE/onu/configured/$ext" || true
    echo
  fi
  if [[ -n "${MYSQL_PWD:-}" && -x "$MYSQL" ]]; then
    "$MYSQL" -uroot -e "DELETE FROM prestaging_oltgateway.olt_activation_operation WHERE sn='$sn';" || true
    "$MYSQL" -uroot ispadmin_prestaging -e "UPDATE subscription SET fiber_onu_sn=NULL WHERE fiber_onu_sn='$sn';" || true
  fi
}

wait_unconfigured() {
  echo "== wait autofind lab ONU via Core =="
  local ok=0
  for _ in $(seq 1 18); do
    if curl -sS --max-time 30 -H "Authorization: Bearer $TOKEN" "$CORE/onu/unconfigured_onus" \
      | python3 -c 'import json,sys,os,re
wanted=re.sub(r"[^A-Z0-9]","",os.environ["E2E_ONU_SN"].upper())
wanted_hex="5A544547"+wanted[4:] if wanted.startswith("ZTEG") else wanted
data=json.load(sys.stdin)
items=data if isinstance(data, list) else (data.get("response") or data.get("onus") or data.get("data") or [])
if isinstance(items, dict):
  items=items.get("onus") or items.get("data") or []
def ok(item):
  disp=re.sub(r"[^A-Z0-9]","",str((item or {}).get("sn","")).upper())
  return wanted in disp or disp in wanted or wanted_hex in disp
sys.exit(0 if any(ok(i) for i in items) else 1)'; then
      echo "ONU $E2E_ONU_SN is unconfigured"
      ok=1
      break
    fi
    echo "waiting for unconfigured $E2E_ONU_SN ..."
    sleep 5
  done
  if [[ "$ok" -ne 1 ]]; then
    echo "ONU lab $E2E_ONU_SN no en Core /onu/unconfigured_onus" >&2
    exit 1
  fi
}

pick_onu_json() {
  curl -sS --max-time 30 -H "Authorization: Bearer $TOKEN" "$CORE/onu/unconfigured_onus" \
    | python3 -c 'import json,sys,os,re
wanted=re.sub(r"[^A-Z0-9]","",os.environ["E2E_ONU_SN"].upper())
wanted_hex="5A544547"+wanted[4:] if wanted.startswith("ZTEG") else wanted
data=json.load(sys.stdin)
items=data if isinstance(data, list) else (data.get("response") or data.get("onus") or data.get("data") or [])
if isinstance(items, dict):
  items=items.get("onus") or items.get("data") or []
def compact(item):
  return re.sub(r"[^A-Z0-9]","",str((item or {}).get("sn","")).upper())
hit=next((i for i in items if wanted in compact(i) or compact(i) in wanted or wanted_hex in compact(i)), None)
if not hit:
    raise SystemExit("lab ONU missing in unconfigured list")
print(json.dumps({
  "board": str(hit.get("board") or ""),
  "olt_id": str(hit.get("olt_id") or ""),
  "onu": str(hit.get("onu") or ""),
  "onu_type_id": str(hit.get("onu_type_id") or ""),
  "onu_type_name": str(hit.get("onu_type_name") or ""),
  "pon_type": str(hit.get("pon_type") or ""),
  "port": str(hit.get("port") or ""),
  "sn": str(hit.get("sn") or os.environ["E2E_ONU_SN"]),
}))
'
}

resolve_catalog() {
  echo "== catalog via Core (ispadmin_prestaging) =="
  PLAN_ID="$(curl -sS --max-time 20 -H "Authorization: Bearer $TOKEN" "$CORE/plan" | python3 -c '
import json,sys
data=json.load(sys.stdin)
items=data if isinstance(data, list) else []
hit=next((p for p in items if str((p or {}).get("type") or "").upper()=="FIBER" and (p.get("isActive") is True or p.get("active") is True or p.get("isActive") is None)), None)
if not hit or not hit.get("id"):
    raise SystemExit("no active FIBER plan in Core catalog")
print(hit["id"])
')"
  NAP_JSON="$(curl -sS --max-time 20 -H "Authorization: Bearer $TOKEN" "$CORE/napbox" | python3 -c '
import json,sys,os
wanted=int(os.environ["NAP_BOX_ID"])
data=json.load(sys.stdin)
items=data if isinstance(data, list) else []
hit=next((n for n in items if int((n or {}).get("id") or 0)==wanted), None)
if not hit:
    raise SystemExit("napBoxId %s missing" % wanted)
board=hit.get("oltBoard")
port=hit.get("oltPort")
if board not in (1, "1") or port not in (6, "6"):
    raise SystemExit("NAP %s must be board 1 / port 6 (got board=%s port=%s)" % (wanted, board, port))
place=hit.get("placeId") or ""
print("%s %s %s %s" % (hit["id"], place, hit.get("latitude") or "", hit.get("longitude") or ""))
')"
  PLACE_ID="$(echo "$NAP_JSON" | awk '{print $2}')"
  GEO_LAT_NAP="$(echo "$NAP_JSON" | awk '{print $3}')"
  GEO_LON_NAP="$(echo "$NAP_JSON" | awk '{print $4}')"
  if [[ -z "$PLACE_ID" || "$PLACE_ID" == "" ]]; then
    if [[ -n "${MYSQL_PWD:-}" && -x "$MYSQL" ]]; then
      PLACE_ID="$("$MYSQL" -uroot ispadmin_prestaging -N -e "SELECT place_id FROM nap_box WHERE id=${NAP_BOX_ID} LIMIT 1;" || true)"
    fi
  fi
  if [[ -z "$PLACE_ID" ]]; then
    PLACE_ID="$(curl -sS --max-time 20 -H "Authorization: Bearer $TOKEN" "$CORE/place" | python3 -c '
import json,sys
data=json.load(sys.stdin)
items=data if isinstance(data, list) else []
if not items or not items[0].get("id"):
    raise SystemExit("no place in Core catalog")
print(items[0]["id"])
')"
  fi
  if [[ -z "${TECH_ID:-}" || "$TECH_ID" == "None" ]]; then
    TECH_ID="$(curl -sS --max-time 20 -H "Authorization: Bearer $TOKEN" "$CORE/technician" | python3 -c '
import json,sys
data=json.load(sys.stdin)
items=data if isinstance(data, list) else []
hit=next((u for u in items if u.get("verified") is True or u.get("verified") is None), None)
if not hit or not hit.get("id"):
    raise SystemExit("no technician in Core catalog")
print(hit["id"])
')"
  fi
  GEO_LAT="${GEO_LAT:-${GEO_LAT_NAP:-0}}"
  GEO_LON="${GEO_LON:-${GEO_LON_NAP:-0}}"
  [[ -n "$PLAN_ID" ]] || { echo "planId missing" >&2; exit 1; }
  [[ -n "$PLACE_ID" ]] || { echo "placeId missing" >&2; exit 1; }
  [[ -n "$TECH_ID" ]] || { echo "technicianId missing" >&2; exit 1; }
  echo "planId=$PLAN_ID napBoxId=$NAP_BOX_ID placeId=$PLACE_ID hostDeviceId=$HOST_DEVICE_ID technicianId=$TECH_ID vlan=$VLAN"
}

post_subscription() {
  local onu_json="$1"
  export PLAN_ID PLACE_ID TECH_ID HOST_DEVICE_ID VLAN NAP_BOX_ID
  export E2E_FIRST_NAME E2E_LAST_NAME E2E_DNI E2E_ADDRESS E2E_PHONE
  export E2E_WIFI_SSID E2E_WIFI_PASS E2E_WIFI_SSID_5 GEO_LAT GEO_LON
  export ONU_JSON="$onu_json"
  python3 - <<'PY'
import json, os, sys, time, urllib.request, urllib.error
core=os.environ["CORE"].rstrip("/")
token=os.environ["TOKEN"]
onu=json.loads(os.environ["ONU_JSON"])
body={
  "firstName": os.environ["E2E_FIRST_NAME"],
  "lastName": os.environ["E2E_LAST_NAME"],
  "dni": os.environ["E2E_DNI"],
  "password": os.environ["E2E_DNI"],
  "address": os.environ["E2E_ADDRESS"],
  "phone": os.environ["E2E_PHONE"],
  "subscriptionDate": int(time.time()*1000),
  "planId": int(os.environ["PLAN_ID"]),
  "additionalDeviceIds": [],
  "placeId": int(os.environ["PLACE_ID"]),
  "location": {"latitude": float(os.environ.get("GEO_LAT") or 0), "longitude": float(os.environ.get("GEO_LON") or 0)},
  "technicianId": int(os.environ["TECH_ID"]),
  "napBoxId": int(os.environ["NAP_BOX_ID"]),
  "hostDeviceId": int(os.environ["HOST_DEVICE_ID"]),
  "onu": onu,
  "installationType": "FIBER",
  "equipmentCondition": "LOAN",
  "autoCut": True,
  "wifiSsid24": os.environ["E2E_WIFI_SSID"],
  "wifiPassword24": os.environ["E2E_WIFI_PASS"],
  "wifiSsid5": os.environ["E2E_WIFI_SSID_5"],
  "wifiPassword5": os.environ["E2E_WIFI_PASS"],
  "vlan": os.environ["VLAN"],
}
req=urllib.request.Request(
  f"{core}/subscription",
  data=json.dumps(body).encode(),
  headers={"Content-Type":"application/json","Authorization":f"Bearer {token}"},
  method="POST",
)
try:
  with urllib.request.urlopen(req, timeout=120) as r:
    raw=r.read().decode()
    status=r.status
except urllib.error.HTTPError as e:
    raw=e.read().decode()
    print(raw, file=sys.stderr)
    raise SystemExit(f"POST /subscription HTTP {e.code}")
payload=json.loads(raw)
if payload.get("status") not in (200, None) and payload.get("error"):
    raise SystemExit("POST /subscription error %s" % payload)
data=payload.get("data") or payload
sub_id=(data or {}).get("id")
if not sub_id:
    raise SystemExit("POST /subscription missing id: %s" % raw[:500])
print(sub_id)
print("POST /subscription id=%s status=%s" % (sub_id, payload.get("status")), file=sys.stderr)
PY
}

poll_progress() {
  local sub_id="$1"
  python3 - "$CORE" "$TOKEN" "$sub_id" "$POLL_SECONDS" <<'PY'
import json, sys, time, urllib.request, urllib.error
base, token, sub_id, timeout = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3], int(sys.argv[4])
deadline = time.time() + timeout
last = None
while time.time() < deadline:
    req = urllib.request.Request(
        f"{base}/subscription/{sub_id}/registration-progress",
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.load(resp)
    except urllib.error.HTTPError as e:
        print(f"registration-progress HTTP {e.code}", file=sys.stderr)
        time.sleep(5)
        continue
    tr069 = body.get("tr069ProvisionStatus")
    olt = body.get("oltProvisionStatus")
    mk = body.get("mikrotikProvisionStatus")
    step = body.get("step")
    print(f"subscription={sub_id} step={step} olt={olt} mk={mk} tr069={tr069}")
    last = tr069
    if tr069 == "COMPLETE":
        sys.exit(0)
    if tr069 in ("FAILED", "MANUAL_REQUIRED"):
        raise SystemExit(f"tr069ProvisionStatus={tr069} message={body.get('tr069Message') or body.get('message')}")
    time.sleep(5)
raise SystemExit(f"timeout waiting tr069 COMPLETE last={last}")
PY
}

if [[ ! -f "$SECRETS" ]]; then
  echo "Missing $SECRETS" >&2
  echo "Copy $EXAMPLE and fill passwords. Required by ./scripts/run-local-prestaging.sh too." >&2
  exit 1
fi

export CORE TOKEN E2E_ONU_SN NAP_BOX_ID

if [[ "$RUN_CHECK_ONLY" -eq 1 ]]; then
  run_check
  echo "E2E_FIBER_LOCAL_PRESTAGING_CHECK_OK"
  exit 0
fi

run_check
ensure_e2e_user
core_login
export TOKEN
lab_tag_ok

echo "== pre cleanup local ONU (lab only) =="
lab_clean_onu "$E2E_ONU_SN"
wait_unconfigured
resolve_catalog
ONU_JSON="$(pick_onu_json)"

echo "== POST /subscription Core =="
SUB_ID="$(
  post_subscription "$ONU_JSON" | tail -n 1
)"
[[ -n "$SUB_ID" ]] || { echo "POST /subscription did not return id" >&2; exit 1; }

echo "== poll registration-progress =="
TEST_EXIT=0
set +e
poll_progress "$SUB_ID"
TEST_EXIT=$?
set -e

if [[ "$TEST_EXIT" -eq 0 ]]; then
  echo "== WiFi credentials (before cleanup) =="
  echo "wifi_24 ssid=$E2E_WIFI_SSID password=$E2E_WIFI_PASS"
  echo "wifi_5 ssid=$E2E_WIFI_SSID_5 password=$E2E_WIFI_PASS"
fi

should_run_post_cleanup() {
  if [[ "${CLEANUP_MODE}" == "skip" || "${SKIP_POST_CLEANUP:-0}" == "1" ]]; then
    echo "== post cleanup skipped (SKIP_POST_CLEANUP=1) dni=$E2E_DNI sn=$E2E_ONU_SN =="
    return 1
  fi
  if [[ "${CLEANUP_MODE}" == "ask" ]]; then
    local reply=""
    echo "dni=$E2E_DNI sn=$E2E_ONU_SN"
    if [[ -r /dev/tty ]]; then
      if ! read -r -p "¿Ejecutar hard cleanup ahora? [s/N] " reply </dev/tty; then
        reply=""
      fi
    else
      echo "== post cleanup skipped (user) dni=$E2E_DNI sn=$E2E_ONU_SN =="
      return 1
    fi
    case "$reply" in
      s|S|y|Y|si|sí|Si|SI) return 0 ;;
      *)
        echo "== post cleanup skipped (user) dni=$E2E_DNI sn=$E2E_ONU_SN =="
        return 1
        ;;
    esac
  fi
  return 0
}

CLEAN_EXIT=0
if should_run_post_cleanup; then
  echo "== post cleanup =="
  set +e
  lab_clean_onu "$E2E_ONU_SN"
  CLEAN_EXIT=$?
  set -e
fi

if [[ "$TEST_EXIT" -ne 0 ]]; then
  echo "E2E prestaging failed exit=$TEST_EXIT subscription=$SUB_ID" >&2
  exit "$TEST_EXIT"
fi
if [[ "$CLEAN_EXIT" -ne 0 ]]; then
  echo "Post cleanup failed exit=$CLEAN_EXIT" >&2
  exit "$CLEAN_EXIT"
fi
echo "E2E_FIBER_LOCAL_PRESTAGING_OK dni=$E2E_DNI sn=$E2E_ONU_SN id=$SUB_ID wifi_24=${E2E_WIFI_SSID}/${E2E_WIFI_PASS} wifi_5=${E2E_WIFI_SSID_5}/${E2E_WIFI_PASS}"
