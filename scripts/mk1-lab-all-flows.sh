#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ISP_BASE="${ISP_BASE:-http://127.0.0.1:8080/ispadmin}"
ISP_USER="${ISP_USER:-dscorp}"
ISP_PASS="${ISP_PASS:-}"
DEVICE_ID="${LAB_MK1_DEVICE_ID:-1}"
LAB_SUB="${LAB_SUBSCRIPTION_ID:-900001}"
LAB_SUB_FLOW="${LAB_SUBSCRIPTION_FLOW_ID:-900002}"
LAB_IP_FLOW="${LAB_IP_FLOW:-192.168.250.2}"
LAB_IP="${LAB_IP:-192.168.250.1}"
RESPONSIBLE_ID="${RESPONSIBLE_ID:-1}"
IP_POOL_SEGMENT="${IP_POOL_SEGMENT:-192.168.250.250/32}"
SNAPSHOT_DIR="${SNAPSHOT_DIR:-/tmp/mk1-lab-all-flows-$(date +%Y%m%d-%H%M%S)}"

if [[ -z "$ISP_PASS" ]]; then
  echo "ISP_PASS required" >&2
  exit 1
fi

mkdir -p "$SNAPSHOT_DIR"
export ISP_BASE ISP_USER ISP_PASS DEVICE_ID LAB_SUB LAB_SUB_FLOW LAB_IP_FLOW LAB_IP RESPONSIBLE_ID IP_POOL_SEGMENT SNAPSHOT_DIR

python3 << 'PY'
import json, os, ssl, sys, urllib.error, urllib.parse, urllib.request, base64

base = os.environ["ISP_BASE"]
user = os.environ["ISP_USER"]
pwd = os.environ["ISP_PASS"]
device_id = int(os.environ["DEVICE_ID"])
lab_sub = int(os.environ["LAB_SUB"])
lab_sub_flow = int(os.environ["LAB_SUB_FLOW"])
lab_ip_flow = os.environ["LAB_IP_FLOW"]
lab_ip = os.environ["LAB_IP"]
responsible = int(os.environ["RESPONSIBLE_ID"])
ip_pool_segment = os.environ["IP_POOL_SEGMENT"]
snap = os.environ["SNAPSHOT_DIR"]

def login():
    body = json.dumps({"username": user, "password": pwd}).encode()
    req = urllib.request.Request(f"{base}/users/login", data=body, method="POST", headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())["accessToken"]

token = login()
headers = {"Authorization": f"Bearer {token}"}

def api(method, path, body=None, timeout=900):
    h = dict(headers)
    data = None
    if body is not None:
        h["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(f"{base}{path}", data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            if not raw:
                return resp.status, None
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw.decode(errors="replace")
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        try:
            detail = json.loads(raw)
        except json.JSONDecodeError:
            detail = raw
        raise RuntimeError(f"HTTP {e.code} {method} {path}: {detail}") from e

def mk_rest(path, mk_user, mk_pwd, host):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    auth = base64.b64encode(f"{mk_user}:{mk_pwd}".encode()).decode()
    req = urllib.request.Request(f"https://{host}{path}", headers={"Authorization": f"Basic {auth}"})
    with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
        return json.loads(resp.read())

results = []

def record(name, ok, detail=""):
    results.append({"name": name, "ok": ok, "detail": detail})
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f" — {detail}" if detail else ""))

_, sysinfo = api("GET", f"/networkDevice/connection/{device_id}/system-info", timeout=60)
host = sysinfo["device"]["ipAddress"]
mk_user = sysinfo["device"]["username"]
mk_pwd = sysinfo["device"]["password"]

def in_deudores(ip):
    rows = mk_rest("/rest/ip/firewall/address-list?list=deudores", mk_user, mk_pwd, host)
    return any(r.get("address") == ip for r in rows)

_, sub_before = api("GET", f"/subscription/{lab_sub}", timeout=60)
plan_before = sub_before["plan"]["id"]
alt_plan = 2 if plan_before == 1 else 1

_, sub_plan = api("PUT", "/subscription/update-plan", {"subscriptionId": lab_sub, "planId": alt_plan}, timeout=120)
record("PUT /subscription/update-plan", sub_plan.get("plan", {}).get("id") == alt_plan, f"sub={lab_sub} plan={alt_plan}")

_, sub_restore_plan = api("PUT", "/subscription/update-plan", {"subscriptionId": lab_sub, "planId": plan_before}, timeout=120)
record("PUT /subscription/update-plan (restore)", sub_restore_plan.get("plan", {}).get("id") == plan_before, f"plan={plan_before}")

_, pool = api("POST", "/ip-pool", {"ipSegment": ip_pool_segment, "hostDeviceId": device_id}, timeout=120)
pool_id = pool.get("id")
record("POST /ip-pool", pool_id is not None, f"id={pool_id} segment={ip_pool_segment}")

if pool_id:
    addrs = mk_rest("/rest/ip/address", mk_user, mk_pwd, host)
    has_seg = any((a.get("address") or "").startswith(ip_pool_segment.split("/")[0]) for a in addrs)
    record("MK1 /ip/address has pool segment", has_seg, ip_pool_segment)
    st, _ = api("DELETE", f"/ip-pool/{pool_id}", timeout=120)
    record("DELETE /ip-pool/{id}", st == 200, str(pool_id))

_, sub_flow = api("GET", f"/subscription/{lab_sub_flow}", timeout=60)
if sub_flow.get("serviceStatus") == "CANCELLED":
    api("PUT", f"/subscription/reactivate-service?subscriptionId={lab_sub_flow}&responsibleId={responsible}&notes=mk1-lab-prep", timeout=180)
    record("prep reactivate flow sub (was cancelled)", True, str(lab_sub_flow))

api("PUT", f"/subscription/cancel-subscription?subscriptionId={lab_sub_flow}&responsibleId={responsible}", timeout=180)
after_cancel = in_deudores(lab_ip_flow)
_, sub_cancelled = api("GET", f"/subscription/{lab_sub_flow}", timeout=60)
record(
    "PUT /subscription/cancel-subscription",
    sub_cancelled.get("serviceStatus") == "CANCELLED" and after_cancel,
    f"status={sub_cancelled.get('serviceStatus')} deudores={after_cancel}",
)

api("PUT", f"/subscription/reactivate-service?subscriptionId={lab_sub_flow}&responsibleId={responsible}&notes=mk1-lab-e2e", timeout=180)
after_react = in_deudores(lab_ip_flow)
_, sub_active = api("GET", f"/subscription/{lab_sub_flow}", timeout=60)
record(
    "PUT /subscription/reactivate-service",
    sub_active.get("serviceStatus") == "ACTIVE" and not after_react,
    f"status={sub_active.get('serviceStatus')} deudores={after_react}",
)

_, payments = api("GET", f"/payment/filtered?subscriptionId={lab_sub}&startDate=0&endDate=9999999999999", timeout=60)
unpaid = [p for p in payments if not p.get("paid")]
if unpaid:
    pay = unpaid[0]
    if not in_deudores(lab_ip):
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        auth = base64.b64encode(f"{mk_user}:{mk_pwd}".encode()).decode()
        req = urllib.request.Request(
            f"https://{host}/rest/ip/firewall/address-list",
            data=json.dumps({
                "list": "deudores",
                "address": lab_ip,
                "comment": f"LAB payment test sub {lab_sub}",
                "disabled": "false",
            }).encode(),
            method="PUT",
            headers={"Authorization": f"Basic {auth}", "Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, context=ctx, timeout=60):
            pass
    had = in_deudores(lab_ip)
    api("PUT", "/payment", {
        "id": pay["id"],
        "method": "mk1-lab-all-flows",
        "paid": True,
        "discountAmount": 0,
        "responsibleId": responsible,
        "subscriptionId": lab_sub,
    }, timeout=180)
    still = in_deudores(lab_ip)
    record("PUT /payment (lab fixture)", had and not still, f"sub={lab_sub} pay={pay['id']} ip={lab_ip}")

with open(f"{snap}/results-extended.json", "w") as f:
    json.dump(results, f, indent=2)

failed = [r for r in results if not r["ok"]]
print(f"\nExtended flows: {len(results)-len(failed)}/{len(results)} passed")
sys.exit(1 if failed else 0)
PY

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "$ROOT/scripts/mk1-lab-mikrotik-remaining.sh" ]]; then
  echo "==> Remaining Mikrotik flows (plan bulk, commitment, register, migration, MK2)"
  ISP_PASS="$ISP_PASS" "$ROOT/scripts/mk1-lab-mikrotik-remaining.sh" || exit 1
fi
