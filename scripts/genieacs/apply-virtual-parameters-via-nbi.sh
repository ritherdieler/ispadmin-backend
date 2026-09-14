#!/usr/bin/env bash
# Push GenieACS VirtualParameters Gf* via NBI (local tunnel or direct URL).
# Usage: GENIEACS_NBI_URL=http://127.0.0.1:7557 ./apply-virtual-parameters-via-nbi.sh
# Mandatory after ACS staging deploy when genieacs.vparams.enabled=true.
set -euo pipefail

NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"
NBI_URL="${NBI_URL%/}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
export NBI_URL ROOT

python3 <<'PY'
import os
import urllib.request

nbi = os.environ["NBI_URL"].rstrip("/")
root = os.environ["ROOT"]
vp = os.path.join(root, "virtual-parameters")
core_path = os.path.join(vp, "lib", "gf-vparams-core.js")
with open(core_path, encoding="utf-8") as fh:
    core = fh.read()

scripts = (
    ("GfApplyInternetPppoe", "gf-apply-internet-pppoe.js"),
    ("GfApplyInternetStatic", "gf-apply-internet-static.js"),
    ("GfSetWifi", "gf-set-wifi.js"),
    ("GfReboot", "gf-reboot.js"),
    ("GfInternetStatus", "gf-internet-status.js"),
    ("GfWifiStatus", "gf-wifi-status.js"),
    ("GfPppoeUsername", "gf-pppoe-username.js"),
    ("GfPppoePassword", "gf-pppoe-password.js"),
    ("GfPppoeVlanId", "gf-pppoe-vlan-id.js"),
    ("GfPppoeConnectionName", "gf-pppoe-connection-name.js"),
)


def put_vparam(name: str, body: str) -> None:
    req = urllib.request.Request(
        f"{nbi}/virtual_parameters/{name}",
        data=body.encode("utf-8"),
        method="PUT",
        headers={"Content-Type": "text/plain; charset=utf-8"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        print(f"{name}: HTTP {resp.status}")


for name, filename in scripts:
    with open(os.path.join(vp, filename), encoding="utf-8") as fh:
        entry = fh.read()
    put_vparam(name, core + "\n" + entry)

print("Done.")
PY
