#!/usr/bin/env bash
# PUT + enqueue gf-tr069-vlan1000 against one GenieACS device (lab first).
# Requires OLT service-port VLAN 1000 already present for that ONU.
#
#   ./scripts/genieacs/retag-tr069-vlan1000.sh --lab
#   ./scripts/genieacs/retag-tr069-vlan1000.sh --device-id B46415-V2804AX15T-12345B4641531C0B6
#   GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/retag-tr069-vlan1000.sh --lab
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT/scripts/genieacs/provisions/gf-tr069-vlan1000.js"
LAB_ID="B46415-V2804AX15T-12345B4641531C0B6"
DEVICE_ID=""
VLAN=1000
NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --lab) DEVICE_ID="$LAB_ID"; shift ;;
    --device-id) DEVICE_ID="${2:-}"; shift 2 ;;
    --vlan) VLAN="${2:-}"; shift 2 ;;
    --nbi) NBI_URL="${2:-}"; shift 2 ;;
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
if [[ ! -f "$SCRIPT" ]]; then
  echo "Falta $SCRIPT" >&2
  exit 1
fi

NBI_URL="${NBI_URL%/}"
export ROOT SCRIPT DEVICE_ID VLAN NBI_URL

python3 <<'PY'
import json, os, time, urllib.parse, urllib.request

nbi = os.environ["NBI_URL"]
device = os.environ["DEVICE_ID"]
vlan = int(os.environ["VLAN"])
script = open(os.environ["SCRIPT"], encoding="utf-8").read()

def http(method, path, body=None, content_type=None, timeout=80):
    data = None
    headers = {}
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        else:
            data = body if isinstance(body, bytes) else str(body).encode()
            headers["Content-Type"] = content_type or "text/plain; charset=utf-8"
    req = urllib.request.Request(nbi + path, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw and resp.headers.get_content_type() == "application/json" else raw)

st, _ = http("PUT", "/provisions/gf-tr069-vlan1000", script)
print("PUT provision HTTP", st)

enc = urllib.parse.quote(device, safe="")
st, body = http(
    "POST",
    f"/devices/{enc}/tasks?timeout=45000&connection_request",
    {"name": "provisions", "provisions": [["gf-tr069-vlan1000", str(vlan)]]},
)
print("ENQUEUE HTTP", st)
print(json.dumps(body, default=str)[:400] if not isinstance(body, bytes) else body[:200])
PY
