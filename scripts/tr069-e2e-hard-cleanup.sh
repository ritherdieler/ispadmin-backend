#!/usr/bin/env bash
# Hard cleanup TR-069 e2e lab (§4: MikroTik → ACS schema → Gateway/OLT → GenieACS NBI device delete → Firebase → Core subscription and provisioning journal → Gateway inventory → Traffic).
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
PRINT_FIREBASE_SA=0
GW_STATE="skip"
GW_NOTE="sin serial"
ACS_STATE="skip"
ACS_NOTE="sin serial ni device"
MK_STATE="skip"
MK_NOTE="sin IP ni PPPoE"
CORE_STATE="skip"
CORE_NOTE="sin suscripción"
MK_QUEUE_EC=0
MK_PPPOE_EC=0
ACS_EC=0
GW_EC=0
GW_INV_EC=0
CORE_EC=0

print_cleanup_summary() {
  echo
  printf '%s\n' "$(e2e_paint '\033[1m' 'Limpieza')" >&2
  _cleanup_row() {
    local name="$1" state="$2" note="$3" tag color
    case "$state" in
      ok) tag="LIMPIO"; color='\033[32m' ;;
      fail) tag="FALLO"; color='\033[31m' ;;
      *) tag="N/A"; color='\033[37m' ;;
    esac
    if [[ "$state" == "fail" ]]; then
      printf '  %-12s  %s\n' "$name" "$(e2e_paint "$color" "$(printf '%-6s' "$tag")")" >&2
      printf '               %s\n' "$note" >&2
    else
      printf '  %-12s  %s  %s\n' "$name" "$(e2e_paint "$color" "$(printf '%-6s' "$tag")")" "$note" >&2
    fi
  }
  _cleanup_row "gateway-olt" "$GW_STATE" "$GW_NOTE"
  _cleanup_row "acs" "$ACS_STATE" "$ACS_NOTE"
  _cleanup_row "mikrotik" "$MK_STATE" "$MK_NOTE"
  _cleanup_row "core" "$CORE_STATE" "$CORE_NOTE"
  printf '\n' >&2
}

section() {
  printf '\n== %s ==\n' "$*" >&2
}

ACS_CAUSE=""
GW_CAUSE=""
CAUSE_LOG=""

begin_cause_log() {
  CAUSE_LOG="$(mktemp)"
}

take_cause() {
  local line=""
  if [[ -n "${CAUSE_LOG:-}" && -f "$CAUSE_LOG" ]]; then
    line="$(grep -E 'ACS HTTP|Gateway rechazó|Gateway delete returned|Falta OLT_GATEWAY|Missing SmartOLT|HTTP Error' "$CAUSE_LOG" | tail -1 | sed 's/^[[:space:]]*//' || true)"
    rm -f "$CAUSE_LOG"
    CAUSE_LOG=""
  fi
  printf '%s' "$line"
}

resolve_firebase_sa() {
  local env_path="${FIREBASE_SERVICE_ACCOUNT_JSON:-}"
  if [[ -n "$env_path" && -f "$env_path" ]]; then
    printf '%s' "$env_path"
    return 0
  fi
  local prod="$BACKEND_ROOT/core/src/main/resources/firebase_service_account_prod.json"
  local staging="$BACKEND_ROOT/core/src/main/resources/firebase_service_account_staging.json"
  local first="$prod"
  local second="$staging"
  if [[ "${E2E_ENV:-}" == "staging" ]]; then
    first="$staging"
    second="$prod"
  fi
  if [[ -f "$first" ]]; then
    printf '%s' "$first"
    return 0
  fi
  if [[ -f "$second" ]]; then
    printf '%s' "$second"
    return 0
  fi
  if [[ -n "$env_path" ]]; then
    printf '%s' "$env_path"
    return 1
  fi
  printf '%s' "$prod"
  return 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --id) ID="${2:-}"; shift 2 ;;
    --sn) SN="${2:-}"; shift 2 ;;
    --dni) DNI="${2:-}"; shift 2 ;;
    --env) E2E_ENV="${2:-}"; shift 2 ;;
    --allow-empty) ALLOW_EMPTY=1; shift ;;
    --force) FORCE=1; shift ;;
    --print-firebase-sa) PRINT_FIREBASE_SA=1; shift ;;
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
    ACS_MYSQL_SCHEMA="prod_acs"
    ;;
  staging)
    MYSQL_SCHEMA="ispadmin_staging"
    OLT_GATEWAY_MYSQL_SCHEMA="stg_oltgateway"
    TRAFFIC_MYSQL_SCHEMA="stg_traffic"
    ACS_MYSQL_SCHEMA="stg_acs"
    ;;
  *) echo "Invalid --env $E2E_ENV (prod|staging)" >&2; exit 2 ;;
esac

if [[ "$PRINT_FIREBASE_SA" -eq 0 && -z "$ID" && -z "$SN" && -z "$DNI" ]]; then
  echo "Provide --id and/or --sn and/or --dni" >&2
  exit 2
fi

if [[ -f "$CONFIG_LOCAL" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$CONFIG_LOCAL"; set +a
fi
if [[ -n "${E2E_VPS_HOST:-}" ]]; then
  VPS_HOST="$E2E_VPS_HOST"
fi

FIREBASE_BUCKET="${FIREBASE_BUCKET:-ispadmin-687ca.appspot.com}"
FIREBASE_SA="$(resolve_firebase_sa || true)"
export FIREBASE_SERVICE_ACCOUNT_JSON="$FIREBASE_SA"
if [[ "$PRINT_FIREBASE_SA" -eq 1 ]]; then
  printf '%s\n' "$FIREBASE_SA"
  [[ -f "$FIREBASE_SA" ]]
  exit $?
fi

VPS_HOST="${VPS_HOST:?VPS_HOST required}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:?DEPLOY_SSH_PASSWORD required}"

export SSHPASS="$DEPLOY_SSH_PASSWORD"
ssh_ok() {
  ssh -n -o StrictHostKeyChecking=no -o BatchMode=yes -o ConnectTimeout=8 -p "$VPS_PORT" "$@" \
    "${VPS_USER}@${VPS_HOST}" true >/dev/null 2>&1
}

ssh_vps() {
  if ssh_ok -o PreferredAuthentications=publickey; then
    ssh -o StrictHostKeyChecking=no -o BatchMode=yes -p "$VPS_PORT" \
      "${VPS_USER}@${VPS_HOST}" "$@"
    return
  fi
  if [[ -n "${SSH_IDENTITY_FILE:-}" && -f "$SSH_IDENTITY_FILE" ]] && ssh_ok -i "$SSH_IDENTITY_FILE" -o IdentitiesOnly=yes; then
    ssh -i "$SSH_IDENTITY_FILE" -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o BatchMode=yes -p "$VPS_PORT" \
      "${VPS_USER}@${VPS_HOST}" "$@"
    return
  fi
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

acs_mysql_q() {
  mysql_schema_q "$ACS_MYSQL_SCHEMA" "$1"
}

e2e_step "hard cleanup env=$E2E_ENV schema=$MYSQL_SCHEMA"
e2e_doing "resolve subscription id=$ID sn=$SN dni=$DNI allow-empty=$ALLOW_EMPTY"
section "resolve subscription env=$E2E_ENV schema=$MYSQL_SCHEMA"
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
if [[ -z "$ACS_DEVICE" && -n "$SUB_SN" ]]; then
  ACS_DEVICE="$(acs_mysql_q "SELECT IFNULL(device_id,'') FROM cpe_record WHERE sn='${SUB_SN}' LIMIT 1;" 2>/dev/null || true)"
  ACS_DEVICE="$(echo "$ACS_DEVICE" | tr -d '\r')"
fi

if [[ -n "$SUB_IP" ]]; then
  section "MikroTik queue target ${SUB_IP}/32"
  set +e
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
def log_http(method, path, status, payload):
  print('', file=sys.stderr)
  color = not os.environ.get('NO_COLOR')
  def paint(code, text):
    n=str(code)
    c='37'
    if n.startswith('2'): c='32'
    elif n.startswith('3'): c='36'
    elif n.startswith('4'): c='33'
    elif n.startswith('5') or n=='000': c='31'
    return f'\\033[{c}m{text}\\033[0m' if color else str(text)
  ok = str(status).startswith(('2','3'))
  tag = 'OK' if ok else 'FAIL'
  line_c = '32' if ok else '31'
  title = f'[{tag}] HTTP  HTTP {method} {path}'
  print(f'\\033[{line_c}m{title}\\033[0m' if color else title, file=sys.stderr)
  print(f'  endpoint={method} {path}  status={paint(status, status)}', file=sys.stderr)
  if payload in (None, '', b''):
    return
  if isinstance(payload, (bytes, bytearray)):
    text = payload.decode('utf-8', errors='replace')
  elif isinstance(payload, (dict, list)):
    text = json.dumps(payload, ensure_ascii=False, indent=2)
  else:
    text = str(payload)
  try:
    text = json.dumps(json.loads(text), ensure_ascii=False, indent=2)
  except Exception:
    pass
  print('  body=', file=sys.stderr)
  for line in text.splitlines():
    print(f'    {line}', file=sys.stderr)
def call(method, path):
  req=urllib.request.Request(f'https://{mkip}{path}', method=method)
  req.add_header('Authorization', f'Basic {cred}')
  with urllib.request.urlopen(req, context=ctx, timeout=45) as r:
    return r.status, r.read()
st, body = call('GET', '/rest/queue/simple')
matches=[q for q in json.loads(body) if queue_matches_ip(q, ip)]
log_http('GET', '/rest/queue/simple', st, matches)
print('queue_matches', len(matches))
for q in matches:
  print('deleting', q.get('.id'), q.get('name'))
  dst=f\"/rest/queue/simple/{q['.id']}\"
  st, raw = call('DELETE', dst)
  log_http('DELETE', dst, st, raw)
st, body = call('GET', '/rest/ip/firewall/address-list')
deudores=[]
for a in json.loads(body):
  addr=str(a.get('address',''))
  host=addr.split('/',1)[0]
  if a.get('list')=='deudores' and host==ip:
    deudores.append(a)
log_http('GET', '/rest/ip/firewall/address-list', st, deudores)
for a in deudores:
  dst=f\"/rest/ip/firewall/address-list/{a['.id']}\"
  st, raw = call('DELETE', dst)
  log_http('DELETE', dst, st, raw)
  print('deudores_removed')
print('MK_DONE', file=sys.stderr)
PY"
  MK_QUEUE_EC=$?
  set -e
fi

if [[ -n "$SUB_PPPOE" ]]; then
  section "MikroTik PPPoE secret ${SUB_PPPOE}"
  set +e
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
mapfile -t ROW < <(docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e \"SELECT ip_address, username, password FROM network_device WHERE id=${HOST_DEVICE_ID};\")
MKIP=\$(echo \"\${ROW[0]}\" | awk '{print \$1}')
USER=\$(echo \"\${ROW[0]}\" | awk '{print \$2}')
PASS=\$(echo \"\${ROW[0]}\" | cut -f3)
export MKIP USER PASS PPPOE='$SUB_PPPOE'
python3 - <<'PY'
import json, os, sys, urllib.request, ssl, base64
mkip=os.environ['MKIP']; user=os.environ['USER']; password=os.environ['PASS']; name=os.environ['PPPOE']
ctx=ssl._create_unverified_context()
cred=base64.b64encode(f'{user}:{password}'.encode()).decode()
def log_http(method, path, status, payload):
  print('', file=sys.stderr)
  color = not os.environ.get('NO_COLOR')
  def paint(code, text):
    n=str(code)
    c='37'
    if n.startswith('2'): c='32'
    elif n.startswith('3'): c='36'
    elif n.startswith('4'): c='33'
    elif n.startswith('5') or n=='000': c='31'
    return f'\\033[{c}m{text}\\033[0m' if color else str(text)
  ok = str(status).startswith(('2','3'))
  tag = 'OK' if ok else 'FAIL'
  line_c = '32' if ok else '31'
  title = f'[{tag}] HTTP  HTTP {method} {path}'
  print(f'\\033[{line_c}m{title}\\033[0m' if color else title, file=sys.stderr)
  print(f'  endpoint={method} {path}  status={paint(status, status)}', file=sys.stderr)
  if payload in (None, '', b''):
    return
  if isinstance(payload, (bytes, bytearray)):
    text = payload.decode('utf-8', errors='replace')
  elif isinstance(payload, (dict, list)):
    text = json.dumps(payload, ensure_ascii=False, indent=2)
  else:
    text = str(payload)
  try:
    text = json.dumps(json.loads(text), ensure_ascii=False, indent=2)
  except Exception:
    pass
  print('  body=', file=sys.stderr)
  for line in text.splitlines():
    print(f'    {line}', file=sys.stderr)
def call(method, path):
  req=urllib.request.Request(f'https://{mkip}{path}', method=method)
  req.add_header('Authorization', f'Basic {cred}')
  with urllib.request.urlopen(req, context=ctx, timeout=45) as r:
    return r.status, r.read().decode('utf-8', errors='replace')
for path in ('/rest/ppp/active', '/rest/ppp/secret'):
  st, body = call('GET', path)
  matches=[item for item in json.loads(body) if item.get('name') == name]
  log_http('GET', path, st, matches)
  for item in matches:
    print('deleting', path, item.get('.id'), name)
    dst=f\"{path}/{item['.id']}\"
    st, raw = call('DELETE', dst)
    log_http('DELETE', dst, st, raw)
print('PPPOE_DONE', file=sys.stderr)
PY"
  MK_PPPOE_EC=$?
  set -e
fi

if [[ -n "$SUB_IP" || -n "$SUB_PPPOE" ]]; then
  MK_NOTE=""
  [[ -n "$SUB_IP" ]] && MK_NOTE="cola ${SUB_IP}/32"
  if [[ -n "$SUB_PPPOE" ]]; then
    if [[ -n "$MK_NOTE" ]]; then
      MK_NOTE="$MK_NOTE, PPPoE ${SUB_PPPOE}"
    else
      MK_NOTE="PPPoE ${SUB_PPPOE}"
    fi
  fi
  if [[ "$MK_QUEUE_EC" -eq 0 && "$MK_PPPOE_EC" -eq 0 ]]; then
    MK_STATE="ok"
  else
    MK_STATE="fail"
    MK_NOTE="$MK_NOTE —"
    [[ "$MK_QUEUE_EC" -ne 0 ]] && MK_NOTE="$MK_NOTE no se pudo borrar la cola"
    [[ "$MK_PPPOE_EC" -ne 0 ]] && MK_NOTE="$MK_NOTE no se pudo borrar el secreto PPPoE"
  fi
fi

if [[ -n "$SUB_SN" ]]; then
  printf '\n== ACS schema delete %s ==\n' "$SUB_SN" >&2
  acs_mysql_q "DELETE FROM acs_onboarding_v2_task WHERE sn='${SUB_SN}';" || { ACS_EC=1; ACS_CAUSE="no se pudo borrar tareas de onboarding"; }
  acs_mysql_q "DELETE FROM cpe_record WHERE sn='${SUB_SN}';" || { ACS_EC=1; ACS_CAUSE="no se pudo borrar el registro CPE"; }
fi

if [[ -n "$SUB_SN" || -n "$ACS_DEVICE" ]]; then
  printf '\n== ACS purge %s ==\n' "$SUB_SN" >&2
  e2e_doing "GenieACS drop subscription tags sn=$SUB_SN device=$ACS_DEVICE"
  set +e
  begin_cause_log
  ssh_vps "python3 - <<'PY'
import json, sys, urllib.request, urllib.error, urllib.parse
dev='''$ACS_DEVICE'''
sn='''$SUB_SN'''
base='http://127.0.0.1:7557'

def get(url):
  try:
    with urllib.request.urlopen(url, timeout=30) as r:
      return r.status, r.read()
  except urllib.error.HTTPError as err:
    print('ACS HTTP %s %s' % (err.code, url.split('?', 1)[0]), file=sys.stderr)
    return err.code, b''

ids=[]
if dev:
  ids.append(dev)
if sn:
  q=urllib.parse.quote(json.dumps({'_id': {'\$regex': sn}}))
  code, raw = get(base+'/devices/?query='+q+'&projection=_id')
  if code >= 400:
    raise SystemExit(1)
  for item in json.loads(raw or b'[]'):
    found=item.get('_id')
    if found and found not in ids:
      ids.append(found)
if not ids:
  print('sin device en GenieACS', file=sys.stderr)
  raise SystemExit(0)

def delete(url):
  req=urllib.request.Request(url, method='DELETE')
  try:
    urllib.request.urlopen(req, timeout=30).read()
    return 200
  except urllib.error.HTTPError as err:
    if err.code == 404:
      return 404
    print('ACS HTTP %s %s' % (err.code, url.split('?', 1)[0]), file=sys.stderr)
    return err.code

for dev in ids:
  encoded=urllib.parse.quote(dev, safe='')
  for kind in ('tasks', 'faults'):
    q=urllib.parse.quote(json.dumps({'device': dev}))
    code, raw = get(base+'/%s/?query=%s' % (kind, q))
    if code >= 400:
      raise SystemExit(1)
    for item in json.loads(raw or b'[]'):
      if delete(base+'/%s/%s' % (kind, urllib.parse.quote(item['_id'], safe=''))) >= 400:
        raise SystemExit(1)
  status=delete(base+'/devices/'+encoded)
  if status >= 400:
    raise SystemExit(1)
  print('device borrado %s' % dev, file=sys.stderr)
PY" 2>&1 | tee "$CAUSE_LOG"
  if [[ "${PIPESTATUS[0]}" -ne 0 ]]; then ACS_EC=1; fi
  line="$(take_cause)"
  [[ -n "$line" ]] && ACS_CAUSE="$line"
  set -e
fi

if [[ -n "$SUB_SN" ]]; then
  if [[ "$E2E_ENV" == "staging" ]]; then
    begin_cause_log
    section "clear Gateway activation journal $SUB_SN"
    e2e_doing "DELETE olt_activation_operation sn=$SUB_SN schema=$OLT_GATEWAY_MYSQL_SCHEMA"
    ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e \"DELETE FROM olt_activation_operation WHERE sn='${SUB_SN}';\"" || GW_EC=1
    printf '\n== OLT Gateway delete %s (ispadmin-staging) ==\n' "$SUB_SN" >&2
    e2e_doing "Gateway lookup+delete SN=$SUB_SN via VPS 127.0.0.1:8081/ispadmin-staging"
    {
      cat "$SCRIPT_DIR/e2e_console.sh"
      echo
      echo 'export E2E_FORCE_COLOR=1'
      echo "export E2E_ONU_SN=$(printf '%q' "$SUB_SN")"
      echo 'export E2E_PHASE=alta'
      cat <<EOF
set -euo pipefail
set -a
# shellcheck disable=SC1091
source /opt/gigafiber/.env
set +a
KEY="\${OLT_GATEWAY_STAGING_API_KEY:-}"
if [[ -z "\$KEY" ]]; then
  KEY="\$(docker exec tomcat-staging printenv OLT_GATEWAY_STAGING_API_KEY 2>/dev/null || true)"
  KEY="\${KEY//\$'\r'/}"
fi
if [[ -z "\$KEY" ]]; then
  echo "Falta OLT_GATEWAY_STAGING_API_KEY en el VPS" >&2
  exit 1
fi
GW="http://127.0.0.1:8081/ispadmin-staging"
SN=$(printf '%q' "$SUB_SN")
hdr=(-H "X-Olt-Gateway-Key: \$KEY" -H "Content-Type: application/json")
EXT=""
for path in \
  "/api/olt-gateway/onu/get_onus_details_by_sn/\$SN" \
  "/api/olt-gateway/onus/by-sn/\$SN"
do
  body="\$(e2e_http GET "\$GW\$path" "\${hdr[@]}" || true)"
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
  code="\${E2E_HTTP_CODE:-000}"
  if [[ "\$code" == "401" || "\$code" == "403" ]]; then
    echo "Gateway rechazó la clave (HTTP \$code)" >&2
    exit 1
  fi
  if [[ -n "\$EXT" ]]; then
    break
  fi
done
ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
docker exec -e MYSQL_PWD="\$ROOTPW" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e "DELETE FROM olt_activation_operation WHERE sn='\$SN';" || true
if [[ -z "\$EXT" ]]; then
  EXT="\$(docker exec -e MYSQL_PWD="\$ROOTPW" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -N -e "SELECT IFNULL(external_id,'') FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn)=UPPER('\$SN') AND external_id IS NOT NULL AND external_id<>'' LIMIT 1;" 2>/dev/null || true)"
  EXT="\${EXT//\$'\r'/}"
fi
if [[ -z "\$EXT" ]]; then
  docker exec -e MYSQL_PWD="\$ROOTPW" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e "DELETE FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn)=UPPER('\$SN'); DELETE FROM olt_activation_operation WHERE UPPER(sn)=UPPER('\$SN');" || true
  echo "ONU not authorized in OLT Gateway (ok)"
  exit 0
fi
echo "external_id=\$EXT"
e2e_http POST "\$GW/api/olt-gateway/onu/delete/\$EXT" "\${hdr[@]}" || true
code="\${E2E_HTTP_CODE:-000}"
docker exec -e MYSQL_PWD="\$ROOTPW" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e "DELETE FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn)=UPPER('\$SN'); DELETE FROM olt_activation_operation WHERE UPPER(sn)=UPPER('\$SN');" || true
if [[ "\$code" != "200" ]]; then
  echo "Gateway delete returned HTTP \$code" >&2
  exit 1
fi
echo "ONU was deleted"
EOF
    } | ssh_vps "bash -s" 2>&1 | tee "$CAUSE_LOG" || GW_EC=1
    line="$(take_cause)"
    [[ -n "$line" ]] && GW_CAUSE="$line"
  else
    section "clear Gateway activation journal $SUB_SN (prod)"
    ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $OLT_GATEWAY_MYSQL_SCHEMA -e \"DELETE FROM olt_activation_operation WHERE sn='${SUB_SN}';\" 2>/dev/null" || GW_EC=1
    section "SmartOLT delete $SUB_SN"
    e2e_doing "SmartOLT lookup+delete SN=$SUB_SN"
    SMARTOLT_KEY="${SMARTOLT_API_KEY:-}"
    if [[ -z "$SMARTOLT_KEY" ]]; then
      SMARTOLT_KEY="$(ssh_vps 'docker exec tomcat9027 bash -lc "grep -h olt.service.api-key /usr/local/tomcat/webapps/ispadmin/WEB-INF/classes/application-prod.properties | head -1 | cut -d= -f2-"' | tr -d '\r')"
    fi
    if [[ -z "$SMARTOLT_KEY" ]]; then
      echo "Missing SmartOLT API key" >&2
      GW_EC=1
      GW_CAUSE="falta la clave de SmartOLT"
    else
    E2E_PHASE=alta
    E2E_ONU_SN="$SUB_SN"
    EXT="$(e2e_http GET "https://gigafiberperu.smartolt.com/api/onu/get_onus_details_by_sn/$SUB_SN" \
      -H "X-Token: $SMARTOLT_KEY" \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print(((d.get("onus") or [{}])[0].get("unique_external_id") or ""))' || true)"
    if [[ -n "$EXT" ]]; then
      e2e_http POST "https://gigafiberperu.smartolt.com/api/onu/delete/$EXT" \
        -H "X-Token: $SMARTOLT_KEY" || GW_EC=1
      if [[ "${E2E_HTTP_CODE:-000}" != "200" ]]; then
        GW_EC=1
        GW_CAUSE="SmartOLT respondió HTTP ${E2E_HTTP_CODE:-000}"
      fi
    else
      echo "ONU not authorized in SmartOLT (ok)"
    fi
    fi
    unset E2E_PHASE E2E_ONU_SN
  fi
  if [[ "$E2E_ENV" == "staging" ]]; then
    GW_NOTE="OLT sn=$SUB_SN"
  else
    GW_NOTE="SmartOLT sn=$SUB_SN"
  fi
  if [[ "$GW_EC" -eq 0 ]]; then
    GW_STATE="ok"
  else
    GW_STATE="fail"
    GW_NOTE="$GW_NOTE — ${GW_CAUSE:-borrado de la ONU falló}"
  fi
fi

if [[ -n "$ACS_DEVICE" ]]; then
  section "GenieACS delete $ACS_DEVICE"
  e2e_doing "GenieACS NBI delete device=$ACS_DEVICE"
  set +e
  begin_cause_log
  ssh_vps "python3 - <<'PY'
import json, sys, urllib.request, urllib.error, urllib.parse
dev='''$ACS_DEVICE'''
encoded_id=urllib.parse.quote(dev, safe='')
for kind in ('tasks','faults'):
  q=urllib.parse.quote(json.dumps({'device':dev}))
  items=json.load(urllib.request.urlopen('http://127.0.0.1:7557/%s/?query=%s'%(kind,q)))
  print(kind, len(items))
  for it in items:
    req=urllib.request.Request('http://127.0.0.1:7557/%s/%s'%(kind,it['_id']), method='DELETE')
    urllib.request.urlopen(req).read()
req=urllib.request.Request('http://127.0.0.1:7557/devices/%s'%encoded_id, method='DELETE')
try:
  urllib.request.urlopen(req).read()
  print('device_deleted', dev)
except urllib.error.HTTPError as err:
  if err.code == 404:
    print('device_absent', dev)
  else:
    print('ACS HTTP %s al borrar device' % err.code, file=sys.stderr)
    raise SystemExit(1)
print('ACS_OK')
PY" 2>&1 | tee "$CAUSE_LOG"
  if [[ "${PIPESTATUS[0]}" -ne 0 ]]; then ACS_EC=1; fi
  line="$(take_cause)"
  [[ -n "$line" ]] && ACS_CAUSE="$line"
  set -e
fi

if [[ -n "$SUB_SN" || -n "$ACS_DEVICE" ]]; then
  ACS_NOTE=""
  [[ -n "$SUB_SN" ]] && ACS_NOTE="esquema sn=$SUB_SN"
  if [[ -n "$ACS_DEVICE" ]]; then
    if [[ -n "$ACS_NOTE" ]]; then
      ACS_NOTE="$ACS_NOTE, device"
    else
      ACS_NOTE="device"
    fi
  fi
  if [[ "$ACS_EC" -eq 0 ]]; then
    ACS_STATE="ok"
  else
    ACS_STATE="fail"
    ACS_NOTE="$ACS_NOTE — ${ACS_CAUSE:-falló la limpieza ACS}"
  fi
fi

FIREBASE_EXIT=0
FIREBASE_PY="$SCRIPT_DIR/tr069_e2e_firebase_delete.py"
if [[ -n "$FACADE_URL" && "$FACADE_URL" != "NULL" ]]; then
  section "Firebase Storage delete"
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

if [[ -n "$SUB_ID" || -n "$SUB_SN" ]]; then
  printf '\n== MySQL delete id=%s ==\n' "$SUB_ID" >&2
  if [[ -n "$SUB_ID" ]]; then
  for table in \
    acs_wifi_station_sample acs_wifi_count_sample acs_wifi_station_hourly acs_wifi_status_current \
    olt_mgr_onu_optical_sample olt_mgr_onu_optical_daily service_onu_state_event identity_link \
    service_health_event service_health_current service_incident_subscription service_remote_action \
    service_traffic_evidence subscription_access_migration subscription_reconnection \
    collection_visit_log assistance_ticket subscription_log subscription_acs payment
  do
    mysql_q "DELETE FROM ${table} WHERE subscription_id = ${SUB_ID};" || CORE_EC=1
  done
  mysql_q "
UPDATE subscription SET fiber_onu_sn = NULL WHERE id = ${SUB_ID};
DELETE FROM subscription WHERE id = ${SUB_ID};
" || CORE_EC=1
  for table in \
    subscription_traffic_sample subscription_traffic_hourly subscription_traffic_daily \
    subscription_traffic_monthly subscription_traffic_five_minute subscription_traffic_counter_state
  do
    traffic_mysql_q "DELETE FROM ${table} WHERE subscription_id = ${SUB_ID};" || CORE_EC=1
  done
  if [[ -n "$SUB_IP" ]]; then
    traffic_mysql_q "DELETE FROM subscription_traffic_sample WHERE client_ip = '${SUB_IP}';" || CORE_EC=1
  fi
  if [[ -n "$SUB_PPPOE" ]]; then
    traffic_mysql_q "DELETE FROM subscription_traffic_sample WHERE client_ip = 'pppoe:${SUB_PPPOE}';" || CORE_EC=1
  fi
  fi
  JOURNAL_WHERE="1=0"
  [[ -n "$SUB_ID" ]] && JOURNAL_WHERE="$JOURNAL_WHERE OR subscription_id=${SUB_ID}"
  [[ -n "$SUB_SN" ]] && JOURNAL_WHERE="$JOURNAL_WHERE OR UPPER(serial)=UPPER('${SUB_SN}')"
  if [[ -n "$SUB_SN" ]]; then
    mysql_q "UPDATE subscription SET fiber_onu_sn = NULL WHERE UPPER(fiber_onu_sn)=UPPER('${SUB_SN}');" || CORE_EC=1
  fi
  mysql_q "DELETE FROM provisioning_v2_event WHERE operation_id IN (SELECT operation_id FROM (SELECT operation_id FROM provisioning_v2_operation WHERE ${JOURNAL_WHERE}) t);" || CORE_EC=1
  mysql_q "DELETE FROM provisioning_v2_resource WHERE operation_id IN (SELECT operation_id FROM (SELECT operation_id FROM provisioning_v2_operation WHERE ${JOURNAL_WHERE}) t);" || CORE_EC=1
  mysql_q "DELETE FROM provisioning_v2_operation WHERE ${JOURNAL_WHERE};" || CORE_EC=1
fi

if [[ -n "$SUB_SN" ]]; then
  printf '\n== Gateway inventory delete %s ==\n' "$SUB_SN" >&2
  mysql_q "DELETE FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn) = UPPER('${SUB_SN}');" || true
  gateway_mysql_q "
DELETE FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn) = UPPER('${SUB_SN}');
DELETE FROM olt_activation_operation WHERE UPPER(sn) = UPPER('${SUB_SN}');
DELETE FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn) = UPPER('${SUB_SN}');
UPDATE olt_mgr_task SET onu_id = NULL WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
UPDATE olt_mgr_audit_log SET onu_id = NULL WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu_status_current WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu_service_port WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu_extra_vlan WHERE onu_id IN (SELECT id FROM (SELECT id FROM olt_mgr_onu WHERE sn = '${SUB_SN}' OR sn LIKE '${SUB_SN}#del#%') t);
DELETE FROM olt_mgr_onu WHERE UPPER(sn) = UPPER('${SUB_SN}') OR sn LIKE '${SUB_SN}#del#%';
" || GW_INV_EC=1
  LEFT_FENCE="$(gateway_mysql_q "SELECT COUNT(*) FROM olt_provisioning_v2_onu_operation WHERE UPPER(sn)=UPPER('${SUB_SN}');" || true)"
  LEFT_FENCE="$(printf '%s' "$LEFT_FENCE" | tr -d '[:space:]')"
  echo "remaining_onu_reservation=$LEFT_FENCE"
  if [[ "${LEFT_FENCE:-1}" != "0" ]]; then
    GW_INV_EC=1
    GW_CAUSE="sigue la reserva de otra alta"
  fi
  if [[ "$GW_INV_EC" -ne 0 ]]; then
    GW_STATE="fail"
    GW_NOTE="$GW_NOTE — no se pudo borrar el inventario de la ONU"
  elif [[ "$GW_STATE" == "ok" ]]; then
    GW_NOTE="$GW_NOTE, inventario"
  fi
fi

printf '\n== verify ==\n' >&2
e2e_doing "verify leftover subscription rows"
if [[ -n "$SUB_ID" ]]; then
  LEFT="$(mysql_q "SELECT COUNT(*) FROM subscription WHERE id=${SUB_ID};" || true)"
  LEFT="$(printf '%s' "$LEFT" | tr -d '[:space:]')"
  echo "remaining_sub_id=$LEFT"
fi
if [[ -n "$SUB_SN" ]]; then
  LEFT_SN="$(mysql_q "SELECT COUNT(*) FROM subscription WHERE fiber_onu_sn='${SUB_SN}';" || true)"
  LEFT_SN="$(printf '%s' "$LEFT_SN" | tr -d '[:space:]')"
  echo "remaining_sub_sn=$LEFT_SN"
fi
if [[ -n "$SUB_ID" || -n "$SUB_SN" ]]; then
  CORE_NOTE=""
  [[ -n "$SUB_ID" ]] && CORE_NOTE="id=$SUB_ID"
  if [[ -n "$SUB_SN" ]]; then
    if [[ -n "$CORE_NOTE" ]]; then
      CORE_NOTE="$CORE_NOTE sn=$SUB_SN"
    else
      CORE_NOTE="sn=$SUB_SN"
    fi
  fi
  if [[ "$CORE_EC" -ne 0 || ( -n "$SUB_ID" && "${LEFT:-1}" != "0" ) || ( -n "$SUB_SN" && "${LEFT_SN:-1}" != "0" ) ]]; then
    CORE_STATE="fail"
    CORE_NOTE="$CORE_NOTE (quedó fila en core)"
  else
    CORE_STATE="ok"
  fi
fi
print_cleanup_summary
echo "CLEANUP_DONE"
CLEANUP_EXIT=0
if [[ "$GW_STATE" == "fail" || "$ACS_STATE" == "fail" || "$MK_STATE" == "fail" || "$CORE_STATE" == "fail" ]]; then
  CLEANUP_EXIT=1
fi
if [[ "${FIREBASE_EXIT:-0}" -ne 0 ]]; then
  echo "cleanup finished but Firebase delete failed exit=$FIREBASE_EXIT" >&2
  exit "$FIREBASE_EXIT"
fi
exit "$CLEANUP_EXIT"
