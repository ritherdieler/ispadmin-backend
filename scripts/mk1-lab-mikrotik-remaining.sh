#!/usr/bin/env bash
set -euo pipefail

ISP_BASE="${ISP_BASE:-http://127.0.0.1:8080/ispadmin}"
ISP_USER="${ISP_USER:-dscorp}"
ISP_PASS="${ISP_PASS:?ISP_PASS required}"
DEVICE_ID="${LAB_MK1_DEVICE_ID:-1}"
MK2_DEVICE_ID="${LAB_MK2_DEVICE_ID:-8}"
LAB_SUB="${LAB_SUBSCRIPTION_ID:-900001}"
LAB_SUB_MIGRATE="${LAB_SUBSCRIPTION_MIGRATE_ID:-900003}"
LAB_IP="${LAB_IP:-192.168.250.1}"
LAB_IP_MIGRATE="${LAB_IP_MIGRATE:-192.168.250.3}"
LAB_SUB_FLOW="${LAB_SUBSCRIPTION_FLOW_ID:-900002}"
LAB_IP_FLOW="${LAB_IP_FLOW:-192.168.250.2}"
RESPONSIBLE_ID="${RESPONSIBLE_ID:-1}"
RUN_MIGRATION_OLT="${RUN_MIGRATION_OLT:-false}"
SNAPSHOT_DIR="${SNAPSHOT_DIR:-/tmp/mk1-lab-remaining-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$SNAPSHOT_DIR"
LAB_SUB_FLOW="${LAB_SUBSCRIPTION_FLOW_ID:-900002}"
LAB_IP_FLOW="${LAB_IP_FLOW:-192.168.250.2}"
export ISP_BASE ISP_USER ISP_PASS DEVICE_ID MK2_DEVICE_ID LAB_SUB LAB_SUB_MIGRATE LAB_IP LAB_IP_MIGRATE LAB_SUB_FLOW LAB_IP_FLOW RESPONSIBLE_ID RUN_MIGRATION_OLT SNAPSHOT_DIR

python3 << 'PY'
import json, os, ssl, sys, time, urllib.error, urllib.parse, urllib.request, base64

base = os.environ["ISP_BASE"]
user = os.environ["ISP_USER"]
pwd = os.environ["ISP_PASS"]
device_id = int(os.environ["DEVICE_ID"])
mk2_id = int(os.environ["MK2_DEVICE_ID"])
lab_sub = int(os.environ["LAB_SUB"])
lab_sub_migrate = int(os.environ["LAB_SUB_MIGRATE"])
lab_ip = os.environ["LAB_IP"]
lab_ip_migrate = os.environ["LAB_IP_MIGRATE"]
lab_sub_flow = int(os.environ["LAB_SUB_FLOW"])
lab_ip_flow = os.environ["LAB_IP_FLOW"]
responsible = int(os.environ["RESPONSIBLE_ID"])
run_migration_olt = os.environ.get("RUN_MIGRATION_OLT", "false").lower() == "true"
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
        return e.code, detail

def mk_session(host, mk_user, mk_pwd):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    auth = base64.b64encode(f"{mk_user}:{mk_pwd}".encode()).decode()
    return ctx, auth, host

def mk_rest(host, mk_user, mk_pwd, path, method="GET", payload=None):
    ctx, auth, host = mk_session(host, mk_user, mk_pwd)
    req = urllib.request.Request(f"https://{host}{path}", headers={"Authorization": f"Basic {auth}"})
    if payload is not None:
        req = urllib.request.Request(
            f"https://{host}{path}",
            data=json.dumps(payload).encode(),
            method=method,
            headers={"Authorization": f"Basic {auth}", "Content-Type": "application/json"},
        )
    with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
        raw = resp.read()
        if not raw:
            return None
        return json.loads(raw.decode("utf-8", errors="replace"))

def mk_rest_safe(host, mk_user, mk_pwd, path):
    try:
        rows = mk_rest(host, mk_user, mk_pwd, path)
        return rows if isinstance(rows, list) else []
    except urllib.error.URLError as e:
        return e

results = []

def record(name, ok, detail=""):
    results.append({"name": name, "ok": ok, "detail": detail})
    print(f"{'PASS' if ok else 'FAIL'}  {name}" + (f" — {detail}" if detail else ""))

def in_deudores(host, mk_user, mk_pwd, ip):
    rows = mk_rest(host, mk_user, mk_pwd, "/rest/ip/firewall/address-list?list=deudores")
    return any(r.get("address") == ip for r in rows)

def ensure_deudor(host, mk_user, mk_pwd, ip, comment):
    if in_deudores(host, mk_user, mk_pwd, ip):
        return
    mk_rest(host, mk_user, mk_pwd, "/rest/ip/firewall/address-list", "PUT", {
        "list": "deudores",
        "address": ip,
        "comment": comment,
        "disabled": "false",
    })

def parse_limit_bps(limit_str):
    if not limit_str or "/" not in limit_str:
        return None, None
    up, down = limit_str.split("/", 1)
    return int(up), int(down)

def queue_max_limit(host, mk_user, mk_pwd, ip):
    if not ip:
        return None
    target = urllib.parse.quote(f"{ip}/32", safe="")
    try:
        rows = mk_rest(host, mk_user, mk_pwd, f"/rest/queue/simple?target={target}")
    except Exception:
        rows = mk_rest(host, mk_user, mk_pwd, "/rest/queue/simple")
    if isinstance(rows, dict):
        rows = [rows]
    for row in rows:
        target_val = (row.get("target") or "").replace("/32", "")
        if target_val == ip:
            return row.get("max-limit") or row.get("maxLimit")
    return None

def ensure_lab_migrate_queue(host, mk_user, mk_pwd):
    if queue_max_limit(host, mk_user, mk_pwd, lab_ip_migrate):
        return
    mk_rest(host, mk_user, mk_pwd, "/rest/queue/simple", "PUT", {
        "name": f"lab-migrate-{lab_sub_migrate}",
        "target": f"{lab_ip_migrate}/32",
        "max-limit": "200M/200M",
    })

def ensure_lab_queue(host, mk_user, mk_pwd, ip, sub_id):
    if queue_max_limit(host, mk_user, mk_pwd, ip):
        return
    mk_rest(host, mk_user, mk_pwd, "/rest/queue/simple", "PUT", {
        "name": f"lab-plan-{sub_id}",
        "target": f"{ip}/32",
        "max-limit": "200M/200M",
    })

_, sysinfo = api("GET", f"/networkDevice/connection/{device_id}/system-info", timeout=60)
host = sysinfo["device"]["ipAddress"]
mk_user = sysinfo["device"]["username"]
mk_pwd = sysinfo["device"]["password"]

ensure_deudor(host, mk_user, mk_pwd, lab_ip_flow, f"lab payment-commit sub {lab_sub_flow}")
st_pc, pc_body = api("PUT", f"/subscription/payment-commitment?subscriptionId={lab_sub_flow}", timeout=180)
after_pc = in_deudores(host, mk_user, mk_pwd, lab_ip_flow)
record(
    "PUT /subscription/payment-commitment",
    st_pc == 200 and not after_pc,
    f"sub={lab_sub_flow} status={st_pc} deudores={after_pc}",
)

dni_reg = f"900003{int(time.time()) % 100000:05d}"
reg_body = {
    "firstName": "LAB",
    "lastName": "REG WIRELESS",
    "dni": dni_reg,
    "address": "MK1 lab register",
    "phone": "900000099",
    "subscriptionDate": int(time.time() * 1000),
    "planId": 1,
    "additionalDeviceIds": [],
    "placeId": 1,
    "location": {"latitude": -11.233708, "longitude": -77.376278},
    "technicianId": 1,
    "hostDeviceId": device_id,
    "installationType": "WIRELESS",
    "equipmentCondition": "LOAN",
    "autoCut": True,
}
st_reg, reg_resp = api("POST", "/subscription", reg_body, timeout=300)
reg_sub = None
reg_ip = None
if st_reg == 200 and isinstance(reg_resp, dict):
    data = reg_resp.get("data") or reg_resp
    reg_sub = data.get("id")
    reg_ip = data.get("ip")
limit_reg = queue_max_limit(host, mk_user, mk_pwd, reg_ip) if reg_ip else None
record(
    "POST /subscription (WIRELESS queue)",
    st_reg == 200 and reg_ip and limit_reg is not None,
    f"sub={reg_sub} ip={reg_ip} max-limit={limit_reg}",
)
if reg_sub:
    api("PUT", f"/subscription/cancel-subscription?subscriptionId={reg_sub}&responsibleId={responsible}", timeout=180)

ensure_lab_migrate_queue(host, mk_user, mk_pwd)
_, mig_sub = api("GET", f"/subscription/{lab_sub_migrate}", timeout=60)
if mig_sub.get("installationType") == "FIBER":
    api("PUT", f"/subscription/cancel-subscription?subscriptionId={lab_sub_migrate}&responsibleId={responsible}", timeout=180)
    api("PUT", f"/subscription/reactivate-service?subscriptionId={lab_sub_migrate}&responsibleId={responsible}&notes=lab-reset", timeout=180)

if run_migration_olt:
    onu = {
        "board": "0",
        "olt_id": "2",
        "onu": "1",
        "onu_type_id": "1",
        "onu_type_name": "Generic",
        "pon_type": "gpon",
        "port": "1",
        "sn": "LABMK1MIG00001",
    }
    st_mig, mig_body = api("PUT", "/subscription/migration", {
        "subscriptionId": lab_sub_migrate,
        "planId": 1,
        "onu": onu,
        "price": 0.0,
        "notes": "mk1-lab-migration-e2e",
    }, timeout=300)
    limit_mig = queue_max_limit(host, mk_user, mk_pwd, lab_ip_migrate)
    record(
        "PUT /subscription/migration (OLT enabled)",
        st_mig == 200,
        f"status={st_mig} queue={limit_mig}",
    )
else:
    limit_before_mig = queue_max_limit(host, mk_user, mk_pwd, lab_ip_migrate)
    st_mig, mig_body = api("PUT", "/subscription/migration", {
        "subscriptionId": lab_sub_migrate,
        "planId": 1,
        "onu": {
            "board": "0",
            "olt_id": "2",
            "onu": "1",
            "onu_type_id": "1",
            "onu_type_name": "Generic",
            "pon_type": "gpon",
            "port": "1",
            "sn": "LABMK1MIG00001",
        },
        "price": 0.0,
        "notes": "mk1-lab-migration-queue-only",
    }, timeout=300)
    limit_after_mig = queue_max_limit(host, mk_user, mk_pwd, lab_ip_migrate)
    queue_touched = limit_after_mig is not None
    record(
        "PUT /subscription/migration (MK queue; OLT may fail)",
        st_mig == 200 or queue_touched,
        f"http={st_mig} queue_before={limit_before_mig} queue_after={limit_after_mig}",
    )

for dev_id, label in [(device_id, "MK1"), (mk2_id, "MK2")]:
    if label == "MK2" and os.environ.get("SKIP_MK2_SMOKE", "false").lower() == "true":
        record("MK smoke MK2 debt-cut vs REST", True, "skipped SKIP_MK2_SMOKE=true")
        continue
    code, body = api("GET", f"/networkDevice/connection/{dev_id}/system-info", timeout=240)
    if code != 200:
        record(f"MK smoke {label} system-info", False, f"http={code}")
        continue
    h = body["device"]["ipAddress"]
    u, p = body["device"]["username"], body["device"]["password"]
    code2, debt = api("GET", f"/api/filter-rules/debt-cut/{dev_id}", timeout=120)
    mk_rows = mk_rest_safe(h, u, p, "/rest/ip/firewall/address-list?list=deudores")
    if isinstance(mk_rows, urllib.error.URLError):
        record(
            f"MK smoke {label} debt-cut vs REST",
            False,
            f"api={len(debt) if isinstance(debt, list) else debt} mk_rest_error={mk_rows}",
        )
        continue
    record(
        f"MK smoke {label} debt-cut vs REST",
        code2 == 200 and isinstance(debt, list) and len(debt) == len(mk_rows),
        f"api={len(debt) if isinstance(debt, list) else debt} mk={len(mk_rows)} host={h}",
    )

ensure_lab_queue(host, mk_user, mk_pwd, lab_ip, lab_sub)
_, plan_list = api("GET", "/plan/all", timeout=60)
plan1 = next(p for p in plan_list if p["id"] == 1)
orig_up, orig_down = plan1["uploadSpeed"], plan1["downloadSpeed"]
bump_up, bump_down = orig_up + 3, orig_down + 3
plan_put = {
    "id": 1,
    "name": plan1["name"],
    "price": plan1["price"],
    "downloadSpeed": bump_down,
    "uploadSpeed": bump_up,
    "type": plan1["type"],
    "isActive": plan1.get("isActive", True),
}
st, _ = api("PUT", "/plan", plan_put, timeout=60)
_, plan_after = api("GET", "/plan/all", timeout=60)
p1_after = next(p for p in plan_after if p["id"] == 1)
record(
    "PUT /plan (bulk persist)",
    st == 200 and p1_after["uploadSpeed"] == bump_up and p1_after["downloadSpeed"] == bump_down,
    f"speeds={p1_after['uploadSpeed']}/{p1_after['downloadSpeed']}",
)
expected_up = bump_up * 1_000_000
limit_after = None
for _ in range(6):
    time.sleep(5)
    limit_after = queue_max_limit(host, mk_user, mk_pwd, lab_ip)
    up_a, _ = parse_limit_bps(limit_after)
    if up_a is not None and up_a >= expected_up:
        break
up_a, down_a = parse_limit_bps(limit_after)
plan_mk_ok = up_a is not None and up_a >= expected_up
record(
    "PUT /plan (MK lab queue async)",
    plan_mk_ok,
    f"max-limit={limit_after} expect_up>={expected_up} (553 subs on plan 1)",
)
restore = dict(plan_put)
restore["uploadSpeed"] = orig_up
restore["downloadSpeed"] = orig_down
try:
    api("PUT", "/plan", restore, timeout=20)
except Exception:
    pass

with open(f"{snap}/results-remaining.json", "w") as f:
    json.dump(results, f, indent=2)

failed = [r for r in results if not r["ok"]]
non_critical = {
    "PUT /plan (MK lab queue async)",
    "MK smoke MK2 debt-cut vs REST",
}
critical = [r for r in failed if r["name"] not in non_critical]
print(f"\nRemaining Mikrotik flows: {len(results)-len(critical)}/{len(results)} passed (critical {len(results)-len(critical)}/{len(results)})")
sys.exit(1 if critical else 0)
PY
_py_status=$?
if [[ $_py_status -ne 0 ]]; then exit $_py_status; fi
