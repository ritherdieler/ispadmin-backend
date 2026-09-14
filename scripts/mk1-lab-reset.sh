#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${MK1_LAB_ENV:-$ROOT/scripts/lab/mk1-e2e-lab.env}"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

ISP_BASE="${ISP_BASE:-http://127.0.0.1:8080/ispadmin}"
MYSQL_BIN="${MYSQL_BIN:-/usr/local/mysql/bin/mysql}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ispadmin_dev}"
LAB_IP="${LAB_IP:-192.168.250.1}"
LAB_SUBSCRIPTION_ID="${LAB_SUBSCRIPTION_ID:-900001}"
LAB_PAYMENT_ID="${LAB_PAYMENT_ID:-900001}"
LAB_MK1_DEVICE_ID="${LAB_MK1_DEVICE_ID:-1}"
ADMIN_USER="${ADMIN_USER:-dscorp}"
ADMIN_PASS="${ADMIN_PASS:-}"

if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "MYSQL_PASSWORD or SPRING_DATASOURCE_PASSWORD required" >&2
  exit 1
fi
if [[ -z "$ADMIN_PASS" ]]; then
  echo "ADMIN_PASS required for API login (seed uses labmk1 / same hash as dscorp if seeded)" >&2
  exit 1
fi

echo "==> SQL seed (user/subscription/payment $LAB_SUBSCRIPTION_ID)"
"$MYSQL_BIN" -h"$MYSQL_HOST" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$ROOT/scripts/lab/mk1-e2e-seed.sql"

export ISP_BASE ADMIN_USER ADMIN_PASS LAB_IP LAB_SUBSCRIPTION_ID LAB_PAYMENT_ID LAB_MK1_DEVICE_ID
python3 << PY
import base64, json, os, ssl, sys, urllib.parse, urllib.request

base = os.environ.get("ISP_BASE", "$ISP_BASE")
admin_user = os.environ.get("ADMIN_USER", "$ADMIN_USER")
admin_pass = os.environ.get("ADMIN_PASS", "$ADMIN_PASS")
lab_ip = os.environ.get("LAB_IP", "$LAB_IP")
lab_sub = int(os.environ.get("LAB_SUBSCRIPTION_ID", "$LAB_SUBSCRIPTION_ID"))
device_id = int(os.environ.get("LAB_MK1_DEVICE_ID", "$LAB_MK1_DEVICE_ID"))

def api_token():
    body = json.dumps({"username": admin_user, "password": admin_pass}).encode()
    req = urllib.request.Request(f"{base}/users/login", data=body, method="POST", headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())["accessToken"]

token = api_token()
headers = {"Authorization": f"Bearer {token}"}

def api(method, path, body=None):
    h = dict(headers)
    data = None
    if body is not None:
        h["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(f"{base}{path}", data=data, method=method, headers=h)
    with urllib.request.urlopen(req, timeout=420) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None

sysinfo = api("GET", f"/networkDevice/connection/{device_id}/system-info")
dev = sysinfo["device"]
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
auth = base64.b64encode(f"{dev['username']}:{dev['password']}".encode()).decode()
host = dev["ipAddress"]

def mk_get(path):
    req = urllib.request.Request(f"https://{host}{path}", headers={"Authorization": f"Basic {auth}"})
    with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
        return json.loads(resp.read())

def mk_put(path, payload):
    req = urllib.request.Request(
        f"https://{host}{path}",
        data=json.dumps(payload).encode(),
        method="PUT",
        headers={"Authorization": f"Basic {auth}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
        return resp.read()

def mk_delete(path):
    req = urllib.request.Request(f"https://{host}{path}", method="DELETE", headers={"Authorization": f"Basic {auth}"})
    with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
        return resp.read()

rows = mk_get(f"/rest/ip/firewall/address-list?list=deudores")
for row in rows:
    if row.get("address") == lab_ip:
        rid = row[".id"]
        mk_delete(f"/rest/ip/firewall/address-list/{rid}")
        print(f"removed old deudor entry {rid} for {lab_ip}")

mk_put("/rest/ip/firewall/address-list", {
    "list": "deudores",
    "address": lab_ip,
    "comment": f"LAB MK1 E2E sub {lab_sub}",
    "disabled": "false",
})
print(f"added deudores {lab_ip}")

after = mk_get(f"/rest/ip/firewall/address-list?list=deudores")
if not any(r.get("address") == lab_ip for r in after):
    sys.exit("MK1 deudores missing lab IP after add")
print("MK1 deudores OK")

sub = api("GET", f"/subscription/{lab_sub}")
if sub.get("id") != lab_sub or sub.get("ip") != lab_ip:
    sys.exit(f"subscription {lab_sub} invalid: {sub!r}")
print(f"API subscription {lab_sub} OK ip={sub.get('ip')}")

print("LAB_RESET_OK")
print(f"  user: labmk1 (password same as dscorp seed: use ANDROID_PASSWORD in env)")
print(f"  subscriptionId={lab_sub} paymentId={os.environ.get('LAB_PAYMENT_ID', '$LAB_PAYMENT_ID')} ip={lab_ip}")
PY
_py_status=$?
if [[ $_py_status -ne 0 ]]; then exit $_py_status; fi
