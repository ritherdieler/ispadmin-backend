#!/usr/bin/env bash
# Pre-register OLT VLAN 1000 (via Core prod), ping internet WAN, then PUT + enqueue gf-tr069-vlan1000.
#
# Parque (misma OLT; Core prod tiene el inventario; staging no aísla):
#   CORE_BASE=https://api.gigafiberperu.cloud/ispadmin GENIEACS_NBI_URL=http://127.0.0.1:7557 \
#     ./scripts/genieacs/retag-tr069-vlan1000.sh --device-id ... --sn VSOL...
#   ./scripts/genieacs/retag-tr069-vlan1000.sh --prod --device-id ... --sn VSOL...
# Lab local:
#   CORE_BASE=http://127.0.0.1:8082/ispadmin GENIEACS_NBI_URL=http://127.0.0.1:7557 \
#     ./scripts/genieacs/retag-tr069-vlan1000.sh --lab
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/genieacs/provisions/gf-tr069-vlan1000.js"
HELPER="$ROOT/scripts/genieacs/retag_internet_check.py"
LAB_ID="B46415-V2804AX15T-12345B4641531C0B6"
LAB_SN="VSOL0031C0B6"
DEVICE_ID=""
ONU_SN=""
VLAN=1000
NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"
CORE_BASE="${CORE_BASE:-http://127.0.0.1:8082/ispadmin}"
E2E_USER="${E2E_USER:-${CORE_USER:-dscorp}}"
E2E_PASSWORD="${E2E_PASSWORD:-${CORE_PASSWORD:-nohacker}}"
CONFIG_LOCAL="${DEPLOY_CONFIG_LOCAL:-$ROOT/scripts/deploy.config.local}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --lab) DEVICE_ID="$LAB_ID"; ONU_SN="$LAB_SN"; shift ;;
    --prod) CORE_BASE="https://api.gigafiberperu.cloud/ispadmin"; shift ;;
    --device-id) DEVICE_ID="${2:-}"; shift 2 ;;
    --sn) ONU_SN="${2:-}"; shift 2 ;;
    --vlan) VLAN="${2:-}"; shift 2 ;;
    --nbi) NBI_URL="${2:-}"; shift 2 ;;
    --core) CORE_BASE="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
    *) echo "Arg desconocido: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$DEVICE_ID" ]]; then
  echo "Usa --lab o --device-id" >&2
  exit 1
fi
if [[ -z "$ONU_SN" ]]; then
  ONU_SN="${DEVICE_ID##*-}"
fi
if [[ ! -f "$SCRIPT" ]]; then
  echo "Falta $SCRIPT" >&2
  exit 1
fi
if [[ ! -f "$HELPER" ]]; then
  echo "Falta $HELPER" >&2
  exit 1
fi

if [[ -f "$CONFIG_LOCAL" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_LOCAL"
  set +a
fi

NBI_URL="${NBI_URL%/}"
CORE_BASE="${CORE_BASE%/}"
export ROOT SCRIPT HELPER DEVICE_ID ONU_SN VLAN NBI_URL CORE_BASE E2E_USER E2E_PASSWORD
export VPS_HOST="${VPS_HOST:-}"
export VPS_USER="${VPS_USER:-root}"
export VPS_PORT="${VPS_PORT:-22}"
if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
fi

python3 <<'PY'
import json, os, ssl, sys, time, urllib.error, urllib.parse, urllib.request

sys.path.insert(0, os.path.join(os.environ["ROOT"], "scripts/genieacs"))
from retag_internet_check import classify_wans, ping_ip, snapshot_label, check_not_lost

nbi = os.environ["NBI_URL"]
core = os.environ["CORE_BASE"]
device = os.environ["DEVICE_ID"]
sn = os.environ["ONU_SN"]
vlan = int(os.environ["VLAN"])
script = open(os.environ["SCRIPT"], encoding="utf-8").read()
user = os.environ["E2E_USER"]
password = os.environ["E2E_PASSWORD"]

def ssl_context():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()

SSL_CTX = ssl_context()

def http(base, method, path, body=None, content_type=None, token=None, timeout=80):
    data = None
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        else:
            data = body if isinstance(body, bytes) else str(body).encode()
            headers["Content-Type"] = content_type or "text/plain; charset=utf-8"
    req = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    last = None
    for _ in range(4):
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
                raw = resp.read()
                parsed = json.loads(raw) if raw and resp.headers.get_content_type() == "application/json" else raw
                return resp.status, parsed
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            raise SystemExit("HTTP %s %s%s %s" % (exc.code, base, path, raw[:400].decode("utf-8", "replace"))) from exc
        except urllib.error.URLError as exc:
            last = exc
            time.sleep(2)
    raise SystemExit("HTTP URLError %s %s%s" % (last, base, path))

def load_device():
    q = urllib.parse.quote(json.dumps({"_id": device}))
    st, body = http(nbi, "GET", "/devices/?query=%s" % q, timeout=30)
    if isinstance(body, list):
        return body[0] if body else {}
    return body if isinstance(body, dict) else {}

def internet_snapshot(tag):
    classified = classify_wans(load_device())
    inet = classified["internet"][0] if classified["internet"] else None
    reachable = False
    ping_out = "no-internet-ip"
    if inet and inet.get("ip"):
        reachable, ping_out = ping_ip(inet["ip"])
    print(snapshot_label(tag, classified, reachable, ping_out))
    return reachable

st, login = http(core, "POST", "/users/login", {"username": user, "password": password}, timeout=20)
token = ""
if isinstance(login, dict):
    token = str(login.get("accessToken") or "")
if not token:
    raise SystemExit("Core login failed HTTP %s on %s" % (st, core))
print("CORE login HTTP", st, "base", core)

before_up = internet_snapshot("INTERNET_BEFORE")

enc_sn = urllib.parse.quote(sn, safe="")
st, ports = http(
    core,
    "POST",
    "/onu/%s/service-port/ensure-mgmt" % enc_sn,
    {"vlan": vlan},
    token=token,
    timeout=80,
)
print("OLT ensure-mgmt HTTP", st)
print(json.dumps(ports, default=str)[:400] if not isinstance(ports, bytes) else ports[:200])
vlans = []
if isinstance(ports, dict):
    raw_vlans = ports.get("vlans") or []
    vlans = [int(v) for v in raw_vlans]
if vlan not in vlans:
    raise SystemExit("OLT VLAN %s missing after ensure-mgmt vlans=%s; aborting CPE retag" % (vlan, vlans))
print("OLT VLAN", vlan, "ok sn=%s f/s/p=%s/%s/%s" % (
    ports.get("sn") if isinstance(ports, dict) else sn,
    ports.get("board") if isinstance(ports, dict) else "?",
    ports.get("port") if isinstance(ports, dict) else "?",
    ports.get("ontId") if isinstance(ports, dict) else "?",
))

after_olt_up = internet_snapshot("INTERNET_AFTER_OLT")
check_not_lost(before_up, after_olt_up, "ensure-mgmt")

st, _ = http(nbi, "PUT", "/provisions/gf-tr069-vlan1000", script)
print("PUT provision HTTP", st)

enc = urllib.parse.quote(device, safe="")
st, body = http(
    nbi,
    "POST",
    "/devices/%s/tasks?timeout=45000&connection_request" % enc,
    {"name": "provisions", "provisions": [["gf-tr069-vlan1000", str(vlan)]]},
)
print("ENQUEUE HTTP", st)
print(json.dumps(body, default=str)[:400] if not isinstance(body, bytes) else body[:200])

after_cpe_up = internet_snapshot("INTERNET_AFTER_CPE")
check_not_lost(before_up, after_cpe_up, "CPE retag")
if before_up:
    print("INTERNET_OK still reachable")
else:
    print("INTERNET_WAS_DOWN_BEFORE skip-success-claim")
PY
