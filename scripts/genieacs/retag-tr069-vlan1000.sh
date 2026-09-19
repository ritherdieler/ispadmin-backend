#!/usr/bin/env bash
# Pre-register OLT VLAN 1000 (via Core), then PUT + enqueue gf-tr069-vlan1000.
#
#   ./scripts/genieacs/retag-tr069-vlan1000.sh --lab
#   ./scripts/genieacs/retag-tr069-vlan1000.sh --device-id B46415-V2804AX15T-12345B4641531C0B6 --sn VSOL0031C0B6
#   CORE_BASE=http://127.0.0.1:8082/ispadmin GENIEACS_NBI_URL=http://127.0.0.1:7557 \
#     ./scripts/genieacs/retag-tr069-vlan1000.sh --lab
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/genieacs/provisions/gf-tr069-vlan1000.js"
LAB_ID="B46415-V2804AX15T-12345B4641531C0B6"
LAB_SN="VSOL0031C0B6"
DEVICE_ID=""
ONU_SN=""
VLAN=1000
NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"
CORE_BASE="${CORE_BASE:-http://127.0.0.1:8082/ispadmin}"
E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --lab) DEVICE_ID="$LAB_ID"; ONU_SN="$LAB_SN"; shift ;;
    --device-id) DEVICE_ID="${2:-}"; shift 2 ;;
    --sn) ONU_SN="${2:-}"; shift 2 ;;
    --vlan) VLAN="${2:-}"; shift 2 ;;
    --nbi) NBI_URL="${2:-}"; shift 2 ;;
    --core) CORE_BASE="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '2,10p' "$0"
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

NBI_URL="${NBI_URL%/}"
CORE_BASE="${CORE_BASE%/}"
export ROOT SCRIPT DEVICE_ID ONU_SN VLAN NBI_URL CORE_BASE E2E_USER E2E_PASSWORD

python3 <<'PY'
import json, os, urllib.error, urllib.parse, urllib.request

nbi = os.environ["NBI_URL"]
core = os.environ["CORE_BASE"]
device = os.environ["DEVICE_ID"]
sn = os.environ["ONU_SN"]
vlan = int(os.environ["VLAN"])
script = open(os.environ["SCRIPT"], encoding="utf-8").read()
user = os.environ["E2E_USER"]
password = os.environ["E2E_PASSWORD"]

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
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            parsed = json.loads(raw) if raw and resp.headers.get_content_type() == "application/json" else raw
            return resp.status, parsed
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        raise SystemExit("HTTP %s %s%s %s" % (exc.code, base, path, raw[:400].decode("utf-8", "replace"))) from exc

st, login = http(core, "POST", "/users/login", {"username": user, "password": password}, timeout=20)
token = ""
if isinstance(login, dict):
    token = str(login.get("accessToken") or "")
if not token:
    raise SystemExit("Core login failed HTTP %s on %s" % (st, core))
print("CORE login HTTP", st)

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
PY
