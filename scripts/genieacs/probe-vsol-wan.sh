#!/usr/bin/env bash
# Inventaria (y opcionalmente escribe) VLAN/IP/máscara/gateway TR-069 de un VSOL
# V2804AX15T en el GenieACS del VPS (NBI localhost:7557).
#
# Uso:
#   ./scripts/genieacs/probe-vsol-wan.sh
#   ./scripts/genieacs/probe-vsol-wan.sh --serial VSOLXXXXXXXX
#   ./scripts/genieacs/probe-vsol-wan.sh --refresh
#   ./scripts/genieacs/probe-vsol-wan.sh --apply --ip 192.168.30.210 --mask 255.255.255.0 --gw 192.168.30.1 --vlan 100
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_LOCAL="$BACKEND_DIR/scripts/deploy.config.local"

SERIAL=""
REFRESH=0
APPLY=0
WAN_IP=""
WAN_MASK=""
WAN_GW=""
WAN_VLAN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --refresh) REFRESH=1; shift ;;
    --apply) APPLY=1; shift ;;
    --ip) WAN_IP="${2:-}"; shift 2 ;;
    --mask) WAN_MASK="${2:-}"; shift 2 ;;
    --gw) WAN_GW="${2:-}"; shift 2 ;;
    --vlan) WAN_VLAN="${2:-}"; shift 2 ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
    *) echo "Arg desconocido: $1" >&2; exit 1 ;;
  esac
done

if [[ -f "$CONFIG_LOCAL" ]]; then
  # shellcheck source=/dev/null
  source "$CONFIG_LOCAL"
fi

VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
SSH_TARGET="${VPS_USER}@${VPS_HOST}"
NBI="http://127.0.0.1:7557"

SSH_BASE=(ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=15)
if ! "${SSH_BASE[@]}" "$SSH_TARGET" true >/dev/null 2>&1; then
  if [[ -z "${DEPLOY_SSH_PASSWORD:-}" ]]; then
    echo "Falta clave SSH o DEPLOY_SSH_PASSWORD para $SSH_TARGET" >&2
    exit 1
  fi
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  SSH_BASE=(sshpass -e ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20)
fi

remote_python() {
  "${SSH_BASE[@]}" "$SSH_TARGET" python3 - "$@"
}

export PROBE_SERIAL="$SERIAL"
export PROBE_REFRESH="$REFRESH"
export PROBE_APPLY="$APPLY"
export PROBE_IP="$WAN_IP"
export PROBE_MASK="$WAN_MASK"
export PROBE_GW="$WAN_GW"
export PROBE_VLAN="$WAN_VLAN"
export PROBE_NBI="$NBI"

"${SSH_BASE[@]}" "$SSH_TARGET" \
  "PROBE_SERIAL=$(printf %q "$SERIAL") PROBE_REFRESH=$REFRESH PROBE_APPLY=$APPLY PROBE_IP=$(printf %q "$WAN_IP") PROBE_MASK=$(printf %q "$WAN_MASK") PROBE_GW=$(printf %q "$WAN_GW") PROBE_VLAN=$(printf %q "$WAN_VLAN") PROBE_NBI=$(printf %q "$NBI") python3 -" <<'PY'
import json, os, re, sys, urllib.parse, urllib.request

NBI = os.environ.get("PROBE_NBI", "http://127.0.0.1:7557")
SERIAL = os.environ.get("PROBE_SERIAL", "").strip()
REFRESH = os.environ.get("PROBE_REFRESH", "0") == "1"
APPLY = os.environ.get("PROBE_APPLY", "0") == "1"
WAN_IP = os.environ.get("PROBE_IP", "").strip()
WAN_MASK = os.environ.get("PROBE_MASK", "").strip()
WAN_GW = os.environ.get("PROBE_GW", "").strip()
WAN_VLAN = os.environ.get("PROBE_VLAN", "").strip()

KEYWORDS = re.compile(
    r"(VLAN|ExternalIPAddress|SubnetMask|DefaultGateway|AddressingType|"
    r"DNSServers|WANIPConnection|WANGponLinkConfig|WANEponLinkConfig|"
    r"WANPONLinkConfig|X_CT-COM_VLAN)",
    re.I,
)

def http(method, path, body=None, timeout=60):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        NBI + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"} if data else {},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        if not raw:
            return resp.status, None
        try:
            return resp.status, json.loads(raw)
        except json.JSONDecodeError:
            return resp.status, raw.decode("utf-8", "replace")

def flatten(node, prefix=""):
    rows = []
    if not isinstance(node, dict):
        return rows
    value = node.get("_value") if "_value" in node else None
    writable = node.get("_writable") if "_writable" in node else None
    if value is not None or writable is not None:
        rows.append((prefix.rstrip("."), value, writable))
    for key, child in node.items():
        if key.startswith("_"):
            continue
        child_prefix = f"{prefix}{key}."
        rows.extend(flatten(child, child_prefix))
    return rows

def device_meta(dev):
    did = dev.get("_deviceId") or {}
    return {
        "id": dev.get("_id"),
        "manufacturer": did.get("_Manufacturer"),
        "product": did.get("_ProductClass"),
        "serial": did.get("_SerialNumber"),
        "oui": did.get("_OUI"),
        "last_inform": dev.get("_lastInform"),
    }

status, devices = http("GET", "/devices/?projection=_id,_deviceId,_lastInform,_tags")
if not isinstance(devices, list):
    print(f"NBI no devolvió lista de devices (HTTP {status}): {devices!r}"[:400])
    sys.exit(1)

print(f"devices_en_acs={len(devices)}")
if not devices:
    print("BLOQUEO: GenieACS no tiene ningún CPE. La VSOL aún no hizo Inform a https://acs.gigafiberperu.cloud/")
    sys.exit(2)

def is_target(meta):
    blob = " ".join(str(meta.get(k) or "") for k in ("manufacturer", "product", "serial", "id"))
    if SERIAL:
        return SERIAL.lower() in blob.lower()
    return bool(re.search(r"VSOL|V2804|56534F4C", blob, re.I))

print("\nInventario ACS:")
print("manufacturer | product | serial | last_inform")
for d in devices:
    m = device_meta(d)
    print(f"{m['manufacturer']} | {m['product']} | {m['serial']} | {m['last_inform']}")

targets = [d for d in devices if is_target(device_meta(d))]
if not targets:
    print("\nNo hay VSOL/V2804AX15T en el ACS. Pasa --serial si el ProductClass no coincide.")
    sys.exit(3)

dev_id = targets[0].get("_id")
meta = device_meta(targets[0])
print(f"\nObjetivo: {meta['manufacturer']} {meta['product']} SN={meta['serial']}")
print(f"device_id={dev_id}")

enc_id = urllib.parse.quote(dev_id, safe="")
if REFRESH:
    print("Enviando refreshObject InternetGatewayDevice.WANDevice ...")
    st, body = http(
        "POST",
        f"/devices/{enc_id}/tasks?timeout=8000&connection_request",
        {"name": "refreshObject", "objectName": "InternetGatewayDevice.WANDevice"},
        timeout=90,
    )
    print(f"refresh HTTP {st} -> {json.dumps(body)[:400] if body is not None else ''}")

st, full = http("GET", f"/devices/?query={urllib.parse.quote(json.dumps({'_id': dev_id}))}")
if not isinstance(full, list) or not full:
    print("No se pudo recargar el dispositivo completo")
    sys.exit(4)

rows = flatten(full[0])
hits = [r for r in rows if KEYWORDS.search(r[0])]
print("\nParametros WAN/VLAN candidatos (path | value | writable):")
if not hits:
    print("(vacío) — el ACS no tiene el árbol WAN. Corre de nuevo con --refresh cuando la ONU esté online.")
    sys.exit(5)

for path, value, writable in hits:
    wr = "W" if writable is True else ("R" if writable is False else "?")
    print(f"{wr}\t{path}\t{value}")

def pick(patterns, writable_only=True):
    for path, value, writable in hits:
        if writable_only and writable is not True:
            continue
        for pat in patterns:
            if re.search(pat, path, re.I):
                return path, value
    return None, None

map_ = {
    "vlan": pick([r"VLANIDMark$", r"X_CT-COM_VLANID$", r"X_VLAN$", r"VLANID$"]),
    "ip": pick([r"WANIPConnection\.\d+\.ExternalIPAddress$"]),
    "mask": pick([r"WANIPConnection\.\d+\.SubnetMask$"]),
    "gw": pick([r"WANIPConnection\.\d+\.DefaultGateway$"]),
    "addr_type": pick([r"WANIPConnection\.\d+\.AddressingType$"]),
}

print("\nMapeo propuesto para setParameterValues:")
for key, (path, value) in map_.items():
    status = "OK" if path else "FALTA"
    print(f"  {status} {key:10} {path or '-'}  (actual={value})")

missing = [k for k, (p, _) in map_.items() if k != "addr_type" and not p]
if missing:
    print(f"\nRESULTADO: NO CERTIFICADO — faltan escribibles: {', '.join(missing)}")
    sys.exit(6)

print("\nRESULTADO: parámetros escribibles encontrados. Falta probar --apply y persistencia tras reboot.")

if not APPLY:
    sys.exit(0)

needed = {"ip": WAN_IP, "mask": WAN_MASK, "gw": WAN_GW, "vlan": WAN_VLAN}
if not all(needed.values()):
    print(" --apply requiere --ip --mask --gw --vlan")
    sys.exit(7)

params = []
if map_["addr_type"][0]:
    params.append([map_["addr_type"][0], "Static", "xsd:string"])
params.append([map_["vlan"][0], WAN_VLAN, "xsd:unsignedInt" if WAN_VLAN.isdigit() else "xsd:string"])
params.append([map_["ip"][0], WAN_IP, "xsd:string"])
params.append([map_["mask"][0], WAN_MASK, "xsd:string"])
params.append([map_["gw"][0], WAN_GW, "xsd:string"])

print("Enviando setParameterValues ...")
st, body = http(
    "POST",
    f"/devices/{enc_id}/tasks?timeout=8000&connection_request",
    {"name": "setParameterValues", "parameterValues": params},
    timeout=90,
)
print(f"set HTTP {st} -> {json.dumps(body)[:800] if body is not None else ''}")
if st >= 400:
    sys.exit(8)
print("Tarea creada. Verifica en la UI de la ONU y con ping al gateway. Si cambia la WAN del ACS, la sesión puede caer hasta el próximo Inform.")
PY
