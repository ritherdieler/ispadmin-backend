#!/usr/bin/env bash
# Ping the subscription IP from its MikroTik host (lab MK2 = network_device id 8).
# Must run AFTER the e2e alta and BEFORE hard cleanup (queue + IP still live).
#
# Usage:
#   ./scripts/tr069-e2e-mk-ping.sh --dni 91234567
#   ./scripts/tr069-e2e-mk-ping.sh --id 2320
#   ./scripts/tr069-e2e-mk-ping.sh --ip 192.168.30.233 --host-device-id 8
#   ./scripts/tr069-e2e-mk-ping.sh --env staging --dni 91234567
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_LOCAL="${DEPLOY_CONFIG_LOCAL:-$SCRIPT_DIR/deploy.config.local}"
PING_PY="$SCRIPT_DIR/tr069_e2e_mk_ping.py"

ID=""
DNI=""
IP=""
HOST_DEVICE_ID=""
COUNT="${PING_COUNT:-4}"
ATTEMPTS="${PING_ATTEMPTS:-12}"
SLEEP_SECS="${PING_SLEEP_SECS:-10}"
E2E_ENV="prod"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --id) ID="${2:-}"; shift 2 ;;
    --dni) DNI="${2:-}"; shift 2 ;;
    --ip) IP="${2:-}"; shift 2 ;;
    --host-device-id) HOST_DEVICE_ID="${2:-}"; shift 2 ;;
    --env) E2E_ENV="${2:-}"; shift 2 ;;
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

if [[ -z "$IP" && -z "$ID" && -z "$DNI" ]]; then
  echo "Provide --ip, and/or --id / --dni" >&2
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

export SSHPASS="$DEPLOY_SSH_PASSWORD"
ssh_vps() {
  sshpass -e ssh -o StrictHostKeyChecking=no -p "$VPS_PORT" \
    -o PreferredAuthentications=password -o PubkeyAuthentication=no \
    "${VPS_USER}@${VPS_HOST}" "$@"
}

mysql_q() {
  local sql="$1"
  ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD); docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e $(printf '%q' "$sql")"
}

WIFI_SSID_24=""
WIFI_SSID_5=""

echo "== resolve subscription for ping env=$E2E_ENV schema=$MYSQL_SCHEMA =="
if [[ -z "$IP" || -z "$HOST_DEVICE_ID" ]]; then
  WHERE="1=0"
  [[ -n "$ID" ]] && WHERE="$WHERE OR id=$ID"
  [[ -n "$DNI" ]] && WHERE="$WHERE OR dni='${DNI}'"
  ROW="$(mysql_q "SELECT CONCAT_WS('|', id, IFNULL(ip,''), IFNULL(host_device_id,8), IFNULL(fiber_onu_sn,''), IFNULL(first_name,''), IFNULL(last_name,''), IFNULL(dni,''), IFNULL(wifi_ssid_24,''), IFNULL(wifi_ssid_5,'')) FROM subscription WHERE $WHERE ORDER BY id DESC LIMIT 1;" || true)"
  if [[ -z "${ROW// }" ]]; then
    echo "No subscription for id=$ID dni=$DNI" >&2
    exit 1
  fi
  IFS='|' read -r SUB_ID SUB_IP SUB_HOST SUB_SN FIRST_NAME LAST_NAME ROW_DNI WIFI_SSID_24 WIFI_SSID_5 <<<"$ROW"
  IP="${IP:-$SUB_IP}"
  HOST_DEVICE_ID="${HOST_DEVICE_ID:-$SUB_HOST}"
  echo "id=$SUB_ID sn=$SUB_SN ip=$IP host=$HOST_DEVICE_ID name=$FIRST_NAME $LAST_NAME dni=$ROW_DNI"
  if [[ -n "${WIFI_SSID_24:-}" ]]; then
    echo "wifi_24 ssid=$WIFI_SSID_24"
    echo "wifi_5 ssid=${WIFI_SSID_5:-}"
  fi
fi

[[ -n "$IP" && "$IP" != "NULL" ]] || { echo "Missing subscription IP" >&2; exit 1; }
HOST_DEVICE_ID="${HOST_DEVICE_ID:-8}"
[[ -f "$PING_PY" ]] || { echo "Missing $PING_PY" >&2; exit 1; }

echo "== MikroTik ping from host_device_id=$HOST_DEVICE_ID → $IP (count=$COUNT attempts=$ATTEMPTS sleep=${SLEEP_SECS}s) =="

set +e
RESULT="$(ssh_vps "ROOTPW=\$(docker exec mysql8033 printenv MYSQL_ROOT_PASSWORD)
mapfile -t ROW < <(docker exec -e MYSQL_PWD=\"\$ROOTPW\" mysql8033 mysql -uroot $MYSQL_SCHEMA -N -e \"SELECT ip_address, username, password FROM network_device WHERE id=${HOST_DEVICE_ID};\")
MKIP=\$(echo \"\${ROW[0]}\" | awk '{print \$1}')
USER=\$(echo \"\${ROW[0]}\" | awk '{print \$2}')
PASS=\$(echo \"\${ROW[0]}\" | cut -f3-)
export MKIP USER PASS TARGET_IP='$IP' COUNT='$COUNT' ATTEMPTS='$ATTEMPTS' SLEEP_SECS='$SLEEP_SECS'
python3 - <<'PY'
import json, os, time, urllib.request, ssl, base64, sys
mkip=os.environ['MKIP']; user=os.environ['USER']; password=os.environ['PASS']
ip=os.environ['TARGET_IP']; count=os.environ['COUNT']
attempts=int(os.environ['ATTEMPTS']); sleep_secs=float(os.environ['SLEEP_SECS'])
ctx=ssl._create_unverified_context()
cred=base64.b64encode(f'{user}:{password}'.encode()).decode()

def call(method, path, data=None):
  req=urllib.request.Request(
    f'https://{mkip}{path}',
    data=None if data is None else json.dumps(data).encode(),
    method=method,
    headers={'Authorization': f'Basic {cred}', 'Content-Type': 'application/json'},
  )
  with urllib.request.urlopen(req, context=ctx, timeout=60) as r:
    return json.loads(r.read().decode())

def ping_once():
  return call('POST', '/rest/ping', {'address': ip, 'count': str(count)})

def arp_dump():
  try:
    rows=call('GET', '/rest/ip/arp')
  except Exception as e:
    print(f'arp_error={e}', file=sys.stderr)
    return
  hits=[r for r in rows if isinstance(r, dict) and str(r.get('address') or '')==ip]
  print('arp_hits=' + json.dumps(hits))
  print('arp_sample=' + json.dumps(rows[:8] if isinstance(rows, list) else rows))

last=None
for i in range(attempts):
  try:
    last=ping_once()
  except Exception as e:
    print(f'attempt={i+1} error={e}', file=sys.stderr)
    time.sleep(sleep_secs)
    continue
  received=0
  for row in last if isinstance(last, list) else []:
    if isinstance(row, dict):
      try:
        received=max(received, int(row.get('received') or 0))
      except Exception:
        pass
      if str(row.get('status') or '').lower()=='alive':
        received=max(received, 1)
  print(f'attempt={i+1} received={received} raw_tail={json.dumps(last[-1] if last else None)}')
  if received >= 1:
    print('PING_OK')
    print('PING_JSON=' + json.dumps(last))
    sys.exit(0)
  time.sleep(sleep_secs)
print('PING_FAIL')
arp_dump()
if last is not None:
  print('PING_JSON=' + json.dumps(last))
sys.exit(1)
PY")"
REMOTE_EXIT=$?
set -e

echo "$RESULT"

JSON_LINE="$(echo "$RESULT" | grep '^PING_JSON=' | tail -n 1 | sed 's/^PING_JSON=//')"
if [[ -n "$JSON_LINE" ]]; then
  TMP_JSON="$(mktemp)"
  printf '%s' "$JSON_LINE" >"$TMP_JSON"
  python3 - "$SCRIPT_DIR" "$TMP_JSON" <<'PY' || REMOTE_EXIT=1
import json, sys
sys.path.insert(0, sys.argv[1])
from tr069_e2e_mk_ping import is_ping_successful, summarize_ping
body = json.load(open(sys.argv[2]))
ok = is_ping_successful(body)
print("local_parser", "OK" if ok else "FAIL", summarize_ping(body))
sys.exit(0 if ok else 1)
PY
  rm -f "$TMP_JSON"
fi

if [[ "$REMOTE_EXIT" -eq 0 ]] && echo "$RESULT" | grep -q '^PING_OK$'; then
  echo "MK_PING_OK ip=$IP host_device_id=$HOST_DEVICE_ID"
  exit 0
fi

echo "MK_PING_FAIL ip=$IP host_device_id=$HOST_DEVICE_ID" >&2
exit 1
