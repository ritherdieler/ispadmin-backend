#!/usr/bin/env bash
# Hard cleanup TR-069 e2e lab (§4 runbook: MikroTik → SmartOLT → Firebase → MySQL → ACS).
# Usage:
#   ./scripts/tr069-e2e-hard-cleanup.sh --dni 91234567
#   ./scripts/tr069-e2e-hard-cleanup.sh --sn HWTCC6FBA6AA
#   ./scripts/tr069-e2e-hard-cleanup.sh --id 2318
#   ./scripts/tr069-e2e-hard-cleanup.sh --env staging --sn ZTEGDC47BFFD
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_LOCAL="${DEPLOY_CONFIG_LOCAL:-$SCRIPT_DIR/deploy.config.local}"

ID=""
SN=""
DNI=""
ALLOW_EMPTY=0
FORCE=0
E2E_ENV="prod"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --id) ID="${2:-}"; shift 2 ;;
    --sn) SN="${2:-}"; shift 2 ;;
    --dni) DNI="${2:-}"; shift 2 ;;
    --env) E2E_ENV="${2:-}"; shift 2 ;;
    --allow-empty) ALLOW_EMPTY=1; shift ;;
    --force) FORCE=1; shift ;;
    -h|--help)
      sed -n '1,13p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

case "$E2E_ENV" in
  prod) MYSQL_SCHEMA="ispadmin" ;;
  staging) MYSQL_SCHEMA="ispadmin_staging" ;;
  *) echo "Invalid --env $E2E_ENV (prod|staging)" >&2; exit 2 ;;
esac

if [[ -z "$ID" && -z "$SN" && -z "$DNI" ]]; then
  echo "Provide --id and/or --sn and/or --dni" >&2
  exit 2
fi

if [[ -f "$CONFIG_LOCAL" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$CONFIG_LOCAL"; set +a
fi

VPS_HOST="${VPS_HOST:?VPS_HOST required}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:?DEPLOY_SSH_PASSWORD required}"
FIREBASE_BUCKET="${FIREBASE_BUCKET:-ispadmin-687ca.appspot.com}"
FIREBASE_SA="${FIREBASE_SERVICE_ACCOUNT_JSON:-$BACKEND_ROOT/src/main/resources/firebase_service_account_prod.json}"

ssh_vps() {
  sshpass -p "$DEPLOY_SSH_PASSWORD" ssh -o StrictHostKeyChecking=no -p "$VPS_PORT" \
    -o PreferredAuthentications=password -o PubkeyAuthentication=no \
    "${VPS_USER}@${VPS_HOST}" "$@"
}

mysql_q() {
  local sql="$1"
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e $(printf '%q' "$sql")"
}

echo "== resolve subscription env=$E2E_ENV schema=$MYSQL_SCHEMA =="
WHERE="1=0"
[[ -n "$ID" ]] && WHERE="$WHERE OR id=$ID"
[[ -n "$SN" ]] && WHERE="$WHERE OR fiber_onu_sn='${SN}'"
[[ -n "$DNI" ]] && WHERE="$WHERE OR dni='${DNI}'"

ROW="$(mysql_q "SELECT CONCAT_WS('|', id, IFNULL(fiber_onu_sn,''), IFNULL(ip,''), IFNULL(facade_photo_url,''), IFNULL(host_device_id,8), IFNULL(first_name,''), IFNULL(last_name,''), IFNULL(dni,'')) FROM subscription WHERE $WHERE ORDER BY id DESC LIMIT 1;" || true)"
if [[ -z "${ROW// }" ]]; then
  echo "No subscription row for id=$ID sn=$SN dni=$DNI"
  if [[ "$ALLOW_EMPTY" -eq 1 ]]; then
    exit 0
  fi
  if [[ -z "$SN" ]]; then
    exit 0
  fi
  SUB_ID=""
  SUB_SN="$SN"
  SUB_IP=""
  FACADE_URL=""
  HOST_DEVICE_ID=8
  FIRST_NAME=""
  LAST_NAME=""
  ROW_DNI=""
else
  IFS='|' read -r SUB_ID SUB_SN SUB_IP FACADE_URL HOST_DEVICE_ID FIRST_NAME LAST_NAME ROW_DNI <<<"$ROW"
  echo "id=$SUB_ID sn=$SUB_SN ip=$SUB_IP host=$HOST_DEVICE_ID name=$FIRST_NAME $LAST_NAME dni=$ROW_DNI"
fi

# Safety: only delete e2e lab rows unless --force
is_lab_row() {
  [[ "${FIRST_NAME}" == E2e* || "${FIRST_NAME}" == E2E* ]] && return 0
  [[ "${LAST_NAME}" == Prueba* || "${LAST_NAME}" == Mimi* ]] && return 0
  [[ "${ROW_DNI}" =~ ^9[0-9]{7}$ ]] && return 0
  return 1
}
if [[ -n "$SUB_ID" ]]; then
  if ! is_lab_row && [[ "$FORCE" -ne 1 ]]; then
    echo "Refusing cleanup of non-lab subscription id=$SUB_ID (name=$FIRST_NAME $LAST_NAME dni=$ROW_DNI). Pass --force only if intentional." >&2
    exit 3
  fi
  TICKETS="$(mysql_q "SELECT COUNT(*) FROM assistance_ticket WHERE subscription_id=${SUB_ID};" || echo 0)"
  if [[ "${TICKETS:-0}" -gt 0 && "$FORCE" -ne 1 ]]; then
    echo "Refusing cleanup: subscription $SUB_ID has $TICKETS assistance_ticket row(s). Use --force to override." >&2
    exit 3
  fi
fi

ACS_DEVICE="$(mysql_q "SELECT IFNULL(genieacs_device_id,'') FROM subscription_acs WHERE subscription_id=${SUB_ID:-0} LIMIT 1;" 2>/dev/null || true)"
ACS_DEVICE="$(echo "$ACS_DEVICE" | tr -d '\r')"

# SmartOLT API key from WAR on VPS
SMARTOLT_KEY="${SMARTOLT_API_KEY:-}"
if [[ -z "$SMARTOLT_KEY" ]]; then
  SMARTOLT_KEY="$(ssh_vps 'docker exec tomcat9027 bash -lc "grep -h olt.service.api-key /usr/local/tomcat/webapps/ispadmin/WEB-INF/classes/application-prod.properties | head -1 | cut -d= -f2-"' | tr -d '\r')"
fi
[[ -n "$SMARTOLT_KEY" ]] || { echo "Missing SmartOLT API key" >&2; exit 1; }

# --- 4.1 MikroTik ---
if [[ -n "$SUB_IP" ]]; then
  echo "== MikroTik queue target ${SUB_IP}/32 =="
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
mapfile -t ROW < <(docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e \"SELECT ip_address, username, password FROM network_device WHERE id=${HOST_DEVICE_ID};\")
MKIP=\$(echo \"\${ROW[0]}\" | awk '{print \$1}')
USER=\$(echo \"\${ROW[0]}\" | awk '{print \$2}')
PASS=\$(echo \"\${ROW[0]}\" | cut -f3)
export MKIP USER PASS IP='$SUB_IP'
python3 - <<'PY'
import json, os, urllib.request, ssl, base64
mkip=os.environ['MKIP']; user=os.environ['USER']; password=os.environ['PASS']; ip=os.environ['IP']
ctx=ssl._create_unverified_context()
cred=base64.b64encode(f'{user}:{password}'.encode()).decode()
def call(method, path):
  req=urllib.request.Request(f'https://{mkip}{path}', method=method)
  req.add_header('Authorization', f'Basic {cred}')
  with urllib.request.urlopen(req, context=ctx, timeout=45) as r:
    return r.status, r.read()
st, body = call('GET', '/rest/queue/simple')
matches=[q for q in json.loads(body) if ip in str(q.get('target','')) or ip in str(q.get('name',''))]
print('queue_matches', len(matches))
for q in matches:
  print('deleting', q.get('.id'), q.get('name'))
  call('DELETE', f\"/rest/queue/simple/{q['.id']}\")
st, body = call('GET', '/rest/ip/firewall/address-list')
for a in json.loads(body):
  if a.get('list')=='deudores' and ip in str(a.get('address','')):
    call('DELETE', f\"/rest/ip/firewall/address-list/{a['.id']}\")
    print('deudores_removed')
print('MK_DONE')
PY"
fi

# --- 4.2 SmartOLT ---
if [[ -n "$SUB_SN" ]]; then
  echo "== SmartOLT delete $SUB_SN =="
  EXT="$(curl -sS -H "X-Token: $SMARTOLT_KEY" \
    "https://gigafiberperu.smartolt.com/api/onu/get_onus_details_by_sn/$SUB_SN" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print(((d.get("onus") or [{}])[0].get("unique_external_id") or ""))' || true)"
  if [[ -n "$EXT" ]]; then
    curl -sS -X POST -H "X-Token: $SMARTOLT_KEY" \
      "https://gigafiberperu.smartolt.com/api/onu/delete/$EXT" \
      | python3 -c 'import json,sys; print(json.load(sys.stdin).get("response","?"))' || true
  else
    echo "ONU not authorized in SmartOLT (ok)"
  fi
fi

# --- 4.3.1 Firebase Storage ---
FIREBASE_EXIT=0
FIREBASE_PY="$SCRIPT_DIR/tr069_e2e_firebase_delete.py"
if [[ -n "$FACADE_URL" && "$FACADE_URL" != "NULL" ]]; then
  echo "== Firebase Storage delete =="
  if [[ ! -f "$FIREBASE_SA" ]]; then
    echo "FIREBASE_SERVICE_ACCOUNT_JSON not found: $FIREBASE_SA" >&2
    FIREBASE_EXIT=1
  elif [[ ! -f "$FIREBASE_PY" ]]; then
    echo "Missing $FIREBASE_PY" >&2
    FIREBASE_EXIT=1
  else
    OBJECT_PATH="$(python3 - <<PY
import urllib.parse
url = """$FACADE_URL"""
marker = "/o/"
i = url.find(marker)
if i < 0:
    raise SystemExit("cannot parse facade url")
rest = url[i+len(marker):]
obj = rest.split("?", 1)[0]
print(urllib.parse.unquote(obj))
PY
)"
    echo "object=$OBJECT_PATH"
    set +e
    python3 "$FIREBASE_PY" "$FIREBASE_SA" "$FIREBASE_BUCKET" "$OBJECT_PATH"
    FIREBASE_EXIT=$?
    set -e
    if [[ "$FIREBASE_EXIT" -ne 0 ]]; then
      echo "Firebase delete failed exit=$FIREBASE_EXIT (MySQL/ACS cleanup continues)" >&2
    fi
  fi
fi

# --- 4.3 MySQL ---
if [[ -n "$SUB_ID" ]]; then
  echo "== MySQL delete id=$SUB_ID =="
  SN_SQL="${SUB_SN:-__none__}"
  mysql_q "
DELETE FROM subscription_log WHERE subscription_id = ${SUB_ID};
DELETE FROM subscription_acs WHERE subscription_id = ${SUB_ID};
DELETE FROM payment WHERE subscription_id = ${SUB_ID};
UPDATE subscription SET fiber_onu_sn = NULL WHERE id = ${SUB_ID};
DELETE FROM subscription WHERE id = ${SUB_ID};
DELETE FROM onu WHERE sn IN ('${SN_SQL}')
  AND NOT EXISTS (SELECT 1 FROM subscription s WHERE s.fiber_onu_sn IN ('${SN_SQL}'));
SET @onu_id := (SELECT id FROM olt_mgr_onu WHERE sn IN ('${SN_SQL}') LIMIT 1);
DELETE FROM olt_mgr_onu_status_current WHERE onu_id = @onu_id;
DELETE FROM olt_mgr_onu_service_port WHERE onu_id = @onu_id;
DELETE FROM olt_mgr_onu_extra_vlan WHERE onu_id = @onu_id;
DELETE FROM olt_mgr_audit_log WHERE onu_id = @onu_id;
DELETE FROM olt_mgr_task WHERE onu_id = @onu_id;
DELETE FROM olt_mgr_onu WHERE id = @onu_id;
"
fi

# --- 4.4 GenieACS ---
if [[ -n "$ACS_DEVICE" ]]; then
  echo "== ACS purge $ACS_DEVICE =="
  ssh_vps "python3 - <<'PY'
import json,urllib.request,urllib.parse
dev='''$ACS_DEVICE'''
for kind in ('tasks','faults'):
  q=urllib.parse.quote(json.dumps({'device':dev}))
  items=json.load(urllib.request.urlopen('http://127.0.0.1:7557/%s/?query=%s'%(kind,q)))
  print(kind, len(items))
  for it in items:
    req=urllib.request.Request('http://127.0.0.1:7557/%s/%s'%(kind,it['_id']), method='DELETE')
    urllib.request.urlopen(req).read()
print('ACS_OK')
PY"
fi

echo "== verify =="
if [[ -n "$SUB_ID" ]]; then
  LEFT="$(mysql_q "SELECT COUNT(*) FROM subscription WHERE id=${SUB_ID};")"
  echo "remaining_sub_id=$LEFT"
fi
if [[ -n "$SUB_SN" ]]; then
  LEFT_SN="$(mysql_q "SELECT COUNT(*) FROM subscription WHERE fiber_onu_sn='${SUB_SN}';")"
  echo "remaining_sub_sn=$LEFT_SN"
fi
echo "CLEANUP_DONE"
if [[ "${FIREBASE_EXIT:-0}" -ne 0 ]]; then
  echo "cleanup finished but Firebase delete failed exit=$FIREBASE_EXIT" >&2
  exit "$FIREBASE_EXIT"
fi
