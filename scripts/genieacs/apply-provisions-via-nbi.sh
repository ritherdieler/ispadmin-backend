#!/usr/bin/env bash
# Push GenieACS provisions via NBI (local tunnel or direct URL).
# Usage: GENIEACS_NBI_URL=http://127.0.0.1:7557 ./apply-provisions-via-nbi.sh
set -euo pipefail

NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"
NBI_URL="${NBI_URL%/}"
ACS_CPE_USERNAME="${ACS_CPE_USERNAME:-gigafiber-acs}"
ACS_URL="${ACS_URL:-http://acs.gigafiberperu.cloud/}"

export NBI_URL ACS_CPE_USERNAME ACS_URL

python3 <<'PY'
import json
import os
import re
import urllib.request

nbi = os.environ["NBI_URL"].rstrip("/")
user = os.environ["ACS_CPE_USERNAME"]
url = os.environ["ACS_URL"]
password = os.environ.get("ACS_CPE_PASSWORD", "")

if not password:
    with urllib.request.urlopen(f"{nbi}/provisions/", timeout=10) as resp:
        provisions = json.load(resp)
    script = next(p["script"] for p in provisions if p["_id"] == "inform")
    match = re.search(r'const acsPass = "([^"]+)"', script)
    if not match:
        raise SystemExit("ACS_CPE_PASSWORD not set and could not be read from inform provision")
    password = match.group(1)


def provision_inform() -> str:
    return f'''const acsUser = "{user}";
const acsPass = "{password}";
const acsUrl = "{url}";

declare("InternetGatewayDevice.ManagementServer.Username", {{value: 1}}, {{value: acsUser}});
declare("InternetGatewayDevice.ManagementServer.Password", null, {{value: acsPass}});
declare("InternetGatewayDevice.ManagementServer.URL", {{value: 1}}, {{value: acsUrl}});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {{value: 1}}, {{value: acsUser}});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", null, {{value: acsPass}});

declare("Device.ManagementServer.Username", {{value: 1}}, {{value: acsUser}});
declare("Device.ManagementServer.Password", null, {{value: acsPass}});
declare("Device.ManagementServer.URL", {{value: 1}}, {{value: acsUrl}});
declare("Device.ManagementServer.ConnectionRequestUsername", {{value: 1}}, {{value: acsUser}});
declare("Device.ManagementServer.ConnectionRequestPassword", null, {{value: acsPass}});
'''


def provision_bootstrap() -> str:
    return f'''const acsUser = "{user}";
const acsPass = "{password}";
const acsUrl = "{url}";
const informInterval = 3600;
const now = Date.now();

clear("Device", now);
clear("InternetGatewayDevice", now);
declare("Tags", {{value: now}}, {{value: ["gigafiber"]}});

declare("InternetGatewayDevice.ManagementServer.Username", {{value: now}}, {{value: acsUser}});
declare("InternetGatewayDevice.ManagementServer.Password", {{value: now}}, {{value: acsPass}});
declare("InternetGatewayDevice.ManagementServer.URL", {{value: now}}, {{value: acsUrl}});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {{value: now}}, {{value: acsUser}});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", {{value: now}}, {{value: acsPass}});
declare("InternetGatewayDevice.ManagementServer.PeriodicInformEnable", {{value: now}}, {{value: true}});
declare("InternetGatewayDevice.ManagementServer.PeriodicInformInterval", {{value: now}}, {{value: informInterval}});

declare("Device.ManagementServer.Username", {{value: now}}, {{value: acsUser}});
declare("Device.ManagementServer.Password", {{value: now}}, {{value: acsPass}});
declare("Device.ManagementServer.URL", {{value: now}}, {{value: acsUrl}});
declare("Device.ManagementServer.ConnectionRequestUsername", {{value: now}}, {{value: acsUser}});
declare("Device.ManagementServer.ConnectionRequestPassword", {{value: now}}, {{value: acsPass}});
declare("Device.ManagementServer.PeriodicInformEnable", {{value: now}}, {{value: true}});
declare("Device.ManagementServer.PeriodicInformInterval", {{value: now}}, {{value: informInterval}});'''


DEFAULT = """const hourly = Date.now(3600000);

declare("InternetGatewayDevice.DeviceInfo.HardwareVersion", {path: hourly, value: hourly});
declare("InternetGatewayDevice.DeviceInfo.SoftwareVersion", {path: hourly, value: hourly});
declare("InternetGatewayDevice.WANDevice.*.WANConnectionDevice.*.WANIPConnection.*.MACAddress", {path: hourly, value: hourly});
declare("InternetGatewayDevice.WANDevice.*.WANConnectionDevice.*.WANIPConnection.*.ExternalIPAddress", {path: hourly, value: hourly});
declare("InternetGatewayDevice.LANDevice.*.WLANConfiguration.*.SSID", {path: hourly, value: hourly});
declare("InternetGatewayDevice.LANDevice.*.WLANConfiguration.*.KeyPassphrase", {path: hourly, value: 1});
declare("InternetGatewayDevice.ManagementServer.Password", {path: hourly, value: 1});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", {path: hourly, value: 1});
declare("Device.ManagementServer.Password", {path: hourly, value: 1});
declare("Device.ManagementServer.ConnectionRequestPassword", {path: hourly, value: 1});"""


def put_provision(provision_id: str, script: str) -> None:
    body = script.encode("utf-8")
    req = urllib.request.Request(
        f"{nbi}/provisions/{provision_id}",
        data=body,
        method="PUT",
        headers={"Content-Type": "text/plain; charset=utf-8"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        print(f"{provision_id}: HTTP {resp.status}")


for pid, script in (
    ("inform", provision_inform()),
    ("gigafiber-bootstrap", provision_bootstrap()),
    ("default", DEFAULT),
):
    put_provision(pid, script)

print("Done.")
PY
