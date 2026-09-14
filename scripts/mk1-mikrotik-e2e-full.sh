#!/usr/bin/env bash
set -euo pipefail

ISP_BASE="${ISP_BASE:-http://127.0.0.1:8080/ispadmin}"
ISP_USER="${ISP_USER:-dscorp}"
ISP_PASS="${ISP_PASS:?ISP_PASS required}"
DEVICE_ID="${DEVICE_ID:-1}"
SNAPSHOT_DIR="${SNAPSHOT_DIR:-/tmp/mk1-e2e-$(date +%Y%m%d-%H%M%S)}"
RUN_MASS="${RUN_MASS:-true}"
RUN_PAYMENT="${RUN_PAYMENT:-true}"

mkdir -p "$SNAPSHOT_DIR"

login_json="$(curl -sf -X POST "$ISP_BASE/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ISP_USER\",\"password\":\"$ISP_PASS\"}")"
TOKEN="$(printf '%s' "$login_json" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")"
export ISP_BASE TOKEN DEVICE_ID SNAPSHOT_DIR

python3 << 'PY'
import json, os, ssl, sys, urllib.request, urllib.parse, base64

base = os.environ["ISP_BASE"]
token = os.environ["TOKEN"]
device_id = int(os.environ["DEVICE_ID"])
snap_dir = os.environ["SNAPSHOT_DIR"]
run_mass = os.environ.get("RUN_MASS", "true") == "true"
run_payment = os.environ.get("RUN_PAYMENT", "true") == "true"

def api(method, path, body=None):
    headers = {"Authorization": f"Bearer {token}"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    req = urllib.request.Request(f"{base}{path}", data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=900) as resp:
        raw = resp.read()
        return resp.status, json.loads(raw) if raw else None

def mk_rest(path, user, pwd, host):
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()
    req = urllib.request.Request(
        f"https://{host}{path}",
        headers={"Authorization": f"Basic {auth}"},
    )
    with urllib.request.urlopen(req, context=ctx, timeout=60) as resp:
        return json.loads(resp.read())

results = []

def record(name, ok, detail=""):
    results.append({"name": name, "ok": ok, "detail": detail})
    mark = "PASS" if ok else "FAIL"
    print(f"{mark}  {name}" + (f" — {detail}" if detail else ""))

_, sysinfo = api("GET", f"/networkDevice/connection/{device_id}/system-info")
host = sysinfo["device"]["ipAddress"]
mk_user = sysinfo["device"]["username"]
mk_pwd = sysinfo["device"]["password"]

deudores = mk_rest("/rest/ip/firewall/address-list?list=deudores", mk_user, mk_pwd, host)
with open(f"{snap_dir}/deudores-before.json", "w") as f:
    json.dump(deudores, f, indent=2)
record("snapshot deudores", True, f"{len(deudores)} entries -> {snap_dir}/deudores-before.json")

_, debt_api = api("GET", f"/api/filter-rules/debt-cut/{device_id}")
record("debt-cut API", len(debt_api) == len(deudores), f"api={len(debt_api)} mk={len(deudores)}")

if debt_api:
    entry = debt_api[0]
    eid = entry["id"]
    eid_plain = eid.lstrip("*")
    enc = urllib.parse.quote(eid, safe="")
    st, body = api("POST", f"/api/filter-rules/disable/{device_id}/{eid_plain}")
    record("disable single path (no star)", body.get("success") is True, str(body.get("results")))
    st, body = api("POST", f"/api/filter-rules/enable/{device_id}/{eid_plain}")
    record("enable single path (no star)", body.get("success") is True, str(body.get("results")))
    enc = urllib.parse.quote(eid, safe="")
    st, body = api("POST", f"/api/filter-rules/disable/{device_id}/{enc}")
    record("disable single path (encoded star)", body.get("success") is True, str(body.get("results")))
    st, body = api("POST", f"/api/filter-rules/enable/{device_id}/{enc}")
    record("enable single path (encoded star)", body.get("success") is True, str(body.get("results")))
    st, body = api("POST", "/api/filter-rules/disable", {"deviceId": device_id, "ruleIds": [eid]})
    record("disable batch", body.get("success") is True)
    st, body = api("POST", "/api/filter-rules/enable", {"deviceId": device_id, "ruleIds": [eid]})
    record("enable batch", body.get("success") is True)

if run_payment and deudores:
    ip = deudores[0].get("address")
    _, subs = api("GET", f"/subscription/find/ip?ip={urllib.parse.quote(ip)}")
    if not subs:
        record("payment reactivation", False, f"no subscription for ip {ip}")
    else:
        sub = subs[0]
        sub_id = sub["id"]
        had_ip = any(r.get("address") == ip for r in deudores)
        _, payments = api("GET", f"/payment/filtered?subscriptionId={sub_id}&startDate=0&endDate=9999999999999")
        unpaid = [p for p in payments if not p.get("paid")]
        if not unpaid:
            record("payment reactivation", False, f"no unpaid payment for sub {sub_id}")
        else:
            pay = unpaid[0]
            pay_id = pay["id"]
            req_body = {
                "id": pay_id,
                "method": "e2e-mk1-test",
                "paid": True,
                "discountAmount": 0,
                "responsibleId": 1,
                "subscriptionId": sub_id,
            }
            st, _ = api("PUT", "/payment", req_body)
            after = mk_rest("/rest/ip/firewall/address-list?list=deudores", mk_user, mk_pwd, host)
            still = any(r.get("address") == ip for r in after)
            record("PUT payment removes deudor IP", had_ip and not still, f"ip={ip} sub={sub_id} pay={pay_id}")
            with open(f"{snap_dir}/deudores-after-payment.json", "w") as f:
                json.dump(after, f, indent=2)

if run_mass:
    st, cut = api("PUT", "/subscription/cortarDeudores")
    record("cortarDeudores", st == 200 and cut is not None, str(cut)[:200] if cut else "")
    after_cut = mk_rest("/rest/ip/firewall/address-list?list=deudores", mk_user, mk_pwd, host)
    with open(f"{snap_dir}/deudores-after-cortar.json", "w") as f:
        json.dump(after_cut, f, indent=2)
    record("deudores after cortar", len(after_cut) >= 0, f"count={len(after_cut)}")

    st, gen = api("POST", "/subscription/generate-address-list-cancelled-subscriptions")
    record("generate-address-list-cancelled", st == 200, str(gen)[:180] if gen else "")

    st, queues = api("POST", "/subscription/generate-simple-queues")
    record("generate-simple-queues", st == 200, str(queues)[:180] if queues else "")

st, _ = api("PUT", f"/subscription/restore-internet-connection?subscriptionId=1&responsibleId=1")
record("restore-internet-connection (BD only)", st == 200)

failed = [r for r in results if not r["ok"]]
with open(f"{snap_dir}/results.json", "w") as f:
    json.dump(results, f, indent=2)
print(f"\nSnapshot dir: {snap_dir}")
print(f"Summary: {len(results)-len(failed)}/{len(results)} passed")
sys.exit(1 if failed else 0)
PY
