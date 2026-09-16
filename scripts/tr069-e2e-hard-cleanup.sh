#!/usr/bin/env bash
# Hard cleanup TR-069 e2e lab (§4 runbook: MikroTik → OLT → Firebase → MySQL → ACS).
# Staging deletes ONU via local OLT Gateway WAR; prod still uses SmartOLT cloud.
# Usage:
#   ./scripts/tr069-e2e-hard-cleanup.sh --dni 91234567
#   ./scripts/tr069-e2e-hard-cleanup.sh --sn HWTCC6FBA6AA
#   ./scripts/tr069-e2e-hard-cleanup.sh --id 2318
#   ./scripts/tr069-e2e-hard-cleanup.sh --env staging --sn ZTEGDC47BFFD
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_LOCAL="${DEPLOY_CONFIG_LOCAL:-$SCRIPT_DIR/deploy.config.local}"
source "$SCRIPT_DIR/e2e_console.sh"

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
      sed -n '1,14p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

case "$E2E_ENV" in
  prod)
    MYSQL_SCHEMA="ispadmin"
    OLT_GATEWAY_MYSQL_SCHEMA="prod_oltgateway"
    TRAFFIC_MYSQL_SCHEMA="prod_traffic"
    ;;
  staging)
    MYSQL_SCHEMA="ispadmin_staging"
    OLT_GATEWAY_MYSQL_SCHEMA="stg_oltgateway"
    TRAFFIC_MYSQL_SCHEMA="stg_traffic"
    ;;
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

export SSHPASS="$DEPLOY_SSH_PASSWORD"
ssh_vps() {
  sshpass -e ssh -o StrictHostKeyChecking=no -p "$VPS_PORT" \
    -o PreferredAuthentications=password -o PubkeyAuthentication=no \
    "${VPS_USER}@${VPS_HOST}" "$@"
}

mysql_schema_q() {
  local schema="$1"
  local sql="$2"
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $schema -N -e $(printf '%q' "$sql")"
}

mysql_q() {
  mysql_schema_q "$MYSQL_SCHEMA" "$1"
}

gateway_mysql_q() {
  mysql_schema_q "$OLT_GATEWAY_MYSQL_SCHEMA" "$1"
}

traffic_mysql_q() {
  mysql_schema_q "$TRAFFIC_MYSQL_SCHEMA" "$1"
}

e2e_step "hard cleanup env=$E2E_ENV schema=$MYSQL_SCHEMA"
e2e_doing "resolve subscription id=$ID sn=$SN dni=$DNI allow-empty=$ALLOW_EMPTY"
echo "== resolve subscription env=$E2E_ENV schema=$MYSQL_SCHEMA =="
WHERE="1=0"
[[ -n "$ID" ]] && WHERE="$WHERE OR id=$ID"
[[ -n "$SN" ]] && WHERE="$WHERE OR fiber_onu_sn='${SN}'"
[[ -n "$DNI" ]] && WHERE="$WHERE OR dni='${DNI}'"

ROW="$(mysql_q "SELECT CONCAT_WS('|', id, IFNULL(fiber_onu_sn,''), IFNULL(ip,''), IFNULL(facade_photo_url,''), IFNULL(host_device_id,8), IFNULL(first_name,''), IFNULL(last_name,''), IFNULL(dni,''), IFNULL(pppoe_username,'')) FROM subscription WHERE $WHERE ORDER BY id DESC LIMIT 1;" || true)"
if [[ -z "${ROW// }" ]]; then
  echo "No subscription row for id=$ID sn=$SN dni=$DNI"
  if [[ -z "$SN" ]]; then
    exit 0
  fi
  if [[ "$ALLOW_EMPTY" -eq 1 ]]; then
    echo "allow-empty with SN=$SN: continue OLT delete only"
  fi
  SUB_ID=""
  SUB_SN="$SN"
  SUB_IP=""
  FACADE_URL=""
  HOST_DEVICE_ID=8
  FIRST_NAME=""
  LAST_NAME=""
  ROW_DNI=""
  SUB_PPPOE=""
else
  IFS='|' read -r SUB_ID SUB_SN SUB_IP FACADE_URL HOST_DEVICE_ID FIRST_NAME LAST_NAME ROW_DNI SUB_PPPOE <<<"$ROW"
  echo "id=$SUB_ID sn=$SUB_SN ip=$SUB_IP pppoe=$SUB_PPPOE host=$HOST_DEVICE_ID name=$FIRST_NAME $LAST_NAME dni=$ROW_DNI"
fi

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

if [[ -n "$SUB_IP" ]]; then
  echo "== MikroTik queue target ${SUB_IP}/32 =="
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
mapfile -t ROW < <(docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e \"SELECT ip_address, username, password FROM network_device WHERE id=${HOST_DEVICE_ID};\")
MKIP=\$(echo \"\${ROW[0]}\" | awk '{print \$1}')
USER=\$(echo \"\${ROW[0]}\" | awk '{print \$2}')
PASS=\$(echo \"\${ROW[0]}\" | cut -f3)
export MKIP USER PASS IP='$SUB_IP'
python3 - <<'PY'
import json, os, urllib.request, ssl, base64, sys
from pathlib import Path
sys.path.insert(0, str(Path('/opt/gigafiber/scripts/lib') if Path('/opt/gigafiber/scripts/lib').exists() else Path('.')))
# fallback: inline exact match (no substring) if helper missing on VPS
def queue_targets_host(target):
    hosts=[]
    for part in str(target or '').replace(' ','').split(','):
        if part: hosts.append(part.split('/',1)[0])
    return hosts
def queue_matches_ip(queue, ip):
    return bool(ip) and ip in queue_targets_host(str(queue.get('target','')))
mkip=os.environ['MKIP']; user=os.environ['USER']; password=os.environ['PASS']; ip=os.environ['IP']
ctx=ssl._create_unverified_context()
cred=base64.b64encode(f'{user}:{password}'.encode()).decode()
def call(method, path):
  req=urllib.request.Request(f'https://{mkip}{path}', method=method)
  req.add_header('Authorization', f'Basic {cred}')
  with urllib.request.urlopen(req, context=ctx, timeout=45) as r:
    return r.status, r.read()
st, body = call('GET', '/rest/queue/simple')
matches=[q for q in json.loads(body) if queue_matches_ip(q, ip)]
print('queue_matches', len(matches))
for q in matches:
  print('deleting', q.get('.id'), q.get('name'))
  call('DELETE', f\"/rest/queue/simple/{q['.id']}\")
st, body = call('GET', '/rest/ip/firewall/address-list')
for a in json.loads(body):
  addr=str(a.get('address',''))
  host=addr.split('/',1)[0]
  if a.get('list')=='deudores' and host==ip:
    call('DELETE', f\"/rest/ip/firewall/address-list/{a['.id']}\")
    print('deudores_removed')
print('MK_DONE')
PY"
fi

if [[ -n "$SUB_PPPOE" ]]; then
  echo "== MikroTik PPPoE secret ${SUB_PPPOE} =="
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
mapfile -t ROW < <(docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e \"SELECT ip_address, username, password FROM network_device WHERE id=${HOST_DEVICE_ID};\")
MKIP=\$(echo \"\${ROW[0]}\" | awk '{print \$1}')
USER=\$(echo \"\${ROW[0]}\" | awk '{print \$2}')
PASS=\$(echo \"\${ROW[0]}\" | cut -f3)
export MKIP USER PASS PPPOE='$SUB_PPPOE'
python3 - <<'PY'
import json, os, urllib.request, ssl, base64
mkip=os.environ['MKIP']; user=os.environ['USER']; password=os.environ['PASS']; name=os.environ['PPPOE']
ctx=ssl._create_unverified_context()
cred=base64.b64encode(f'{user}:{password}'.encode()).decode()
def call(method, path):
  req=urllib.request.Request(f'https://{mkip}{path}', method=method)
  req.add_header('Authorization', f'Basic {cred}')
  with urllib.request.urlopen(req, context=ctx, timeout=45) as r:
    return r.status, r.read().decode('utf-8', errors='replace')
for path in ('/rest/ppp/active', '/rest/ppp/secret'):
  st, body = call('GET', path)
  for item in json.loads(body):
    if item.get('name') == name:
      print('deleting', path, item.get('.id'), name)
      call('DELETE', f\"{path}/{item['.id']}\")
print('PPPOE_DONE')
PY"
fi

if [[ -n "$SUB_SN" ]]; then
  if [[ "$E2E_ENV" == "staging" ]]; then
    echo "== clear Gateway activation journal $SUB_SN =="
    e2e_doing "DELETE olt_activation_operation sn=$SUB_SN schema=$OLT_GATEWAY_MYSQL_SCHEMA"
    ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e \"DELETE FROM olt_activation_operation WHERE sn='${SUB_SN}';\"" || true
    echo "== OLT Gateway delete $SUB_SN (ispadmin-staging) =="
    e2e_doing "Gateway lookup+delete SN=$SUB_SN via VPS 127.0.0.1:8081/ispadmin-staging"
    ssh_vps "bash -s" <<EOF
set -euo pipefail
set -a
# shellcheck disable=SC1091
source /opt/gigafiber/.env
set +a
KEY="\${OLT_GATEWAY_API_KEY:?OLT_GATEWAY_API_KEY missing on VPS}"
GW="http://127.0.0.1:8081/ispadmin-staging"
SN=$(printf '%q' "$SUB_SN")
hdr=(-H "X-Olt-Gateway-Key: \$KEY" -H "Content-Type: application/json")
EXT=""
for path in \
  "/api/olt-gateway/onu/get_onus_details_by_sn/\$SN" \
  "/api/olt-gateway/onus/by-sn/\$SN"
do
  echo "DOING: HTTP GET \$GW\$path"
  echo "URL: GET \$GW\$path"
  body=\$(curl -sS -w "\\nHTTP:%{http_code}" "\${hdr[@]}" "\$GW\$path" || true)
  code=\$(echo "\$body" | sed -n 's/^HTTP://p' | tail -1)
  body=\$(echo "\$body" | sed '/^HTTP:/d')
  echo "HTTP: \${code:-000}"
  echo "BODY: \$(echo "\$body" | head -c 1200)"
  if [[ -n "\$code" && "\$code" != 2* ]]; then
    echo "HTTP_FAIL: GET \$GW\$path code=\$code"
  fi
  EXT=\$(python3 -c 'import json,sys
raw=sys.stdin.read().strip()
if not raw:
  raise SystemExit
d=json.loads(raw)
onus=d.get("onus") if isinstance(d,dict) else None
if isinstance(onus,list) and onus:
  print(onus[0].get("unique_external_id") or "")
elif isinstance(d,dict):
  print(d.get("unique_external_id") or d.get("uniqueExternalId") or "")
' <<<"\$body" 2>/dev/null || true)
  if [[ -n "\$EXT" ]]; then
    break
  fi
done
ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
docker exec -e MYSQL_PWD="\$ROOTPW" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e "DELETE FROM olt_activation_operation WHERE sn='\$SN';" || true
if [[ -z "\$EXT" ]]; then
  echo "ONU not authorized in OLT Gateway (ok)"
  exit 0
fi
echo "external_id=\$EXT"
echo "DOING: HTTP POST \$GW/api/olt-gateway/onu/delete/\$EXT"
echo "URL: POST \$GW/api/olt-gateway/onu/delete/\$EXT"
resp=\$(curl -sS -w "\\nHTTP:%{http_code}" -X POST "\${hdr[@]}" "\$GW/api/olt-gateway/onu/delete/\$EXT" || true)
echo "\$resp"
code=\$(echo "\$resp" | sed -n 's/^HTTP://p' | tail -1)
echo "HTTP: \${code:-000}"
if [[ "\$code" != "200" ]]; then
  echo "HTTP_FAIL: POST \$GW/api/olt-gateway/onu/delete/\$EXT code=\$code"
fi
docker exec -e MYSQL_PWD="\$ROOTPW" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e "DELETE FROM olt_activation_operation WHERE sn='\$SN';" || true
if [[ "\$code" != "200" ]]; then
  echo "Gateway delete returned HTTP \$code" >&2
  exit 1
fi
echo "ONU was deleted"
EOF
  else
    echo "== clear Gateway activation journal $SUB_SN (prod) =="
    ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e \"DELETE FROM olt_activation_operation WHERE sn='${SUB_SN}';\" 2>/dev/null" || true
    echo "== SmartOLT delete $SUB_SN =="
    e2e_doing "SmartOLT lookup+delete SN=$SUB_SN"
    SMARTOLT_KEY="${SMARTOLT_API_KEY:-}"
    if [[ -z "$SMARTOLT_KEY" ]]; then
      SMARTOLT_KEY="$(ssh_vps 'docker exec tomcat9027 bash -lc "grep -h olt.service.api-key /usr/local/tomcat/webapps/ispadmin/WEB-INF/classes/application-prod.properties | head -1 | cut -d= -f2-"' | tr -d '\r')"
    fi
    [[ -n "$SMARTOLT_KEY" ]] || { echo "Missing SmartOLT API key" >&2; exit 1; }
    EXT="$(e2e_http GET "https://gigafiberperu.smartolt.com/api/onu/get_onus_details_by_sn/$SUB_SN" \
      -H "X-Token: $SMARTOLT_KEY" \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print(((d.get("onus") or [{}])[0].get("unique_external_id") or ""))' || true)"
    if [[ -n "$EXT" ]]; then
      e2e_http POST "https://gigafiberperu.smartolt.com/api/onu/delete/$EXT" \
        -H "X-Token: $SMARTOLT_KEY" || true
    else
      echo "ONU not authorized in SmartOLT (ok)"
    fi
  fi
fi

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

if [[ -n "$SUB_ID" ]]; then
  echo "== MySQL delete id=$SUB_ID =="
  for table in \
    acs_wifi_station_sample acs_wifi_count_sample acs_wifi_station_hourly acs_wifi_status_current \
    olt_mgr_onu_optical_sample olt_mgr_onu_optical_daily service_onu_state_event identity_link \
    service_health_event service_health_current service_incident_subscription service_remote_action \
    service_traffic_evidence subscription_access_migration subscription_reconnection \
    collection_visit_log assistance_ticket subscription_log subscription_acs payment
  do
    mysql_q "DELETE FROM ${table} WHERE subscription_id = ${SUB_ID};" || true
  done
  mysql_q "
UPDATE subscription SET fiber_onu_sn = NULL WHERE id = ${SUB_ID};
DELETE FROM subscription WHERE id = ${SUB_ID};
"
  for table in \
    subscription_traffic_sample subscription_traffic_hourly subscription_traffic_daily \
    subscription_traffic_monthly subscription_traffic_five_minute subscription_traffic_counter_state
  do
    traffic_mysql_q "DELETE FROM ${table} WHERE subscription_id = ${SUB_ID};" || true
  done
  if [[ -n "$SUB_IP" ]]; then
    traffic_mysql_q "DELETE FROM subscription_traffic_sample WHERE client_ip = '${SUB_IP}';" || true
  fi
  if [[ -n "$SUB_PPPOE" ]]; then
    traffic_mysql_q "DELETE FROM subscription_traffic_sample WHERE client_ip = 'pppoe:${SUB_PPPOE}';" || true
  fi
fi

if [[ -n "$SUB_SN" ]]; then
  echo "== Gateway inventory delete $SUB_SN =="
  gateway_mysql_q "
DELETE FROM olt_activation_operation WHERE sn = '${SUB_SN}';
UPDATE olt_mgr_task SET onu_id = NULL WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
UPDATE olt_mgr_audit_log SET onu_id = NULL WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu_status_current WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu_service_port WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu_extra_vlan WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%';
" || true
fi

if [[ -n "$ACS_DEVICE" ]]; then
  echo "== ACS purge $ACS_DEVICE =="
  e2e_doing "GenieACS NBI purge device=$ACS_DEVICE"
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
e2e_doing "verify leftover subscription rows"
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
