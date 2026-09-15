#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties"
MYSQL="${MYSQL_BIN:-/opt/homebrew/opt/mysql-client/bin/mysql}"

CORE="${CORE_BASE:-http://127.0.0.1:8082/ispadmin}"
HOST_DEVICE_ID="${HOST_DEVICE_ID:-8}"
TRAFFIC_KEY="${TRAFFIC_API_KEY:-dev-traffic-key}"
E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"
E2E_FIRST_NAME="${E2E_FIRST_NAME:-LAB}"
E2E_LAST_NAME="${E2E_LAST_NAME:-STATICIP}"
E2E_PHONE="${E2E_PHONE:-999000050}"
E2E_ADDRESS="${E2E_ADDRESS:-lab prestaging STATIC_IP traffic}"
WAIT_SECONDS="${WAIT_SECONDS:-180}"
CMD="${1:-all}"

usage() {
  cat <<EOF
Alta WIRELESS STATIC_IP en prestaging local y poll de tráfico por cola simple (MK2).

  $0 seed
  $0 wait
  $0 register
  $0 poll
  $0 check [subscriptionId]
  $0 all

Prerrequisito: WAR único ya arriba
  ./scripts/run-local-prestaging.sh start

Stack: :8082 /ispadmin, schema ispadmin_prestaging, MK2 hostDeviceId $HOST_DEVICE_ID.
Scheduling sigue apagado; el poll es POST /api/traffic/v1/admin/poll.
EOF
}

load_mysql() {
  if [[ -z "${MYSQL_PWD:-}" && -f "$SECRETS" ]]; then
    MYSQL_PWD="$(grep '^spring.datasource.password=' "$SECRETS" | cut -d= -f2-)"
    export MYSQL_PWD
  fi
  MYSQL_USER_NAME="${MYSQL_USER:-}"
  if [[ -z "$MYSQL_USER_NAME" && -f "$SECRETS" ]]; then
    MYSQL_USER_NAME="$(grep '^spring.datasource.username=' "$SECRETS" | cut -d= -f2-)"
  fi
  MYSQL_USER_NAME="${MYSQL_USER_NAME:-root}"
  export MYSQL_USER_NAME
}

mysql_prestaging() {
  "$MYSQL" -u "$MYSQL_USER_NAME" ispadmin_prestaging "$@"
}

seed() {
  if [[ "$CORE" == *gigafiberperu.cloud* ]]; then
    echo "skip mysql seed (remote $CORE)"
    return 0
  fi
  load_mysql
  [[ -x "$MYSQL" ]] || { echo "mysql client missing: $MYSQL" >&2; exit 1; }
  [[ -n "${MYSQL_PWD:-}" ]] || { echo "MYSQL_PWD missing (secrets overlay)" >&2; exit 1; }
  mysql_prestaging -e "
INSERT INTO plan (id, name, type, download_speed, upload_speed, is_active, price, downloadSpeed, isActive, uploadSpeed)
SELECT 50, 'basico_wireless 50', 'WIRELESS', 20, 20, 1, 50, 20, 1, 20
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM plan WHERE type='WIRELESS' AND (is_active=1 OR isActive=1));
"
  HASH="$(python3 - <<'PY'
import hashlib, os, base64
raw = os.environ.get("E2E_PASSWORD", "nohacker")
sha = hashlib.sha384(raw.encode("utf-8")).hexdigest()
salt = os.urandom(16)
dk = hashlib.pbkdf2_hmac("sha256", sha.encode("utf-8"), salt, 120000, dklen=32)
print("pbkdf2$120000$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(dk).decode())
PY
)"
  mysql_prestaging -e "
INSERT INTO user (name, last_name, username, password, type, verified)
SELECT 'Sergio', 'Carrillo Diestra', '${E2E_USER}', '${HASH}', 'ADMIN', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username='${E2E_USER}');
UPDATE user SET password='${HASH}', type='ADMIN', verified=1 WHERE username='${E2E_USER}';
"
  echo "ok seed wireless plan + user ${E2E_USER} ispadmin_prestaging"
  TECH_ID="$(mysql_prestaging -N -e "SELECT id FROM user WHERE type='TECHNICIAN' AND verified=1 LIMIT 1;")"
  export TECH_ID
  mysql_prestaging -N -e "
SELECT CONCAT('wireless_plans=', COUNT(*)) FROM plan WHERE type='WIRELESS' AND (is_active=1 OR isActive=1);
SELECT CONCAT('nd8=', IFNULL((SELECT id FROM network_device WHERE id=${HOST_DEVICE_ID}), 'missing'));
SELECT CONCAT('pool8=', COUNT(*)) FROM ip_pool WHERE host_device_id=${HOST_DEVICE_ID};
SELECT CONCAT('tech=', IFNULL('${TECH_ID}', 'missing'));
"
}

wait_core() {
  local i
  for i in $(seq 1 "$WAIT_SECONDS"); do
    if curl -sf --max-time 2 "$CORE/actuator/health" >/dev/null 2>&1; then
      echo "ok core $CORE"
      return 0
    fi
    if curl -sf --max-time 8 -X POST "$CORE/users/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$E2E_USER\",\"password\":\"$E2E_PASSWORD\",\"checkBox\":false}" >/dev/null 2>&1; then
      echo "ok core $CORE"
      return 0
    fi
    sleep 1
  done
  echo "Core no responde en $CORE" >&2
  exit 1
}

login() {
  TOKEN="$(curl -sS --fail --max-time 20 -X POST "$CORE/users/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$E2E_USER\",\"password\":\"$E2E_PASSWORD\",\"checkBox\":false}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken") or "")')"
  [[ -n "$TOKEN" ]] || { echo "login failed user=$E2E_USER" >&2; exit 1; }
}

register() {
  wait_core
  login
  export TOKEN
  python3 - <<'PY'
import json, os, time, urllib.request, urllib.error
core = os.environ["CORE"].rstrip("/")
token = os.environ["TOKEN"]
host_id = int(os.environ["HOST_DEVICE_ID"])
headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}

def get(path):
    req = urllib.request.Request(core + path, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)

def post(path, body):
    req = urllib.request.Request(
        core + path,
        data=json.dumps(body).encode(),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            detail = json.loads(raw)
        except json.JSONDecodeError:
            detail = raw
        return e.code, detail

rows = get("/subscription/all")
if not isinstance(rows, list):
    rows = []
existing = next(
    (
        s for s in rows
        if str(s.get("firstName")) == os.environ["E2E_FIRST_NAME"]
        and str(s.get("lastName")) == os.environ["E2E_LAST_NAME"]
        and str(s.get("installationType")) == "WIRELESS"
        and str(s.get("serviceStatus") or "ACTIVE") not in ("CANCELLED", "CANCELED")
    ),
    None,
)
if existing:
    print("reuse_sub", existing.get("id"), "accessMode", existing.get("accessMode"), "has_ip", bool(existing.get("ip")), "pppoe", existing.get("pppoeUsername"))
    print(existing.get("id"))
    raise SystemExit(0)

plans = get("/plan")
plan = next(
    (
        p for p in (plans if isinstance(plans, list) else [])
        if str((p or {}).get("type") or "").upper() == "WIRELESS"
        and (p.get("isActive") is True or p.get("active") is True or p.get("isActive") is None)
    ),
    None,
)
if not plan or not plan.get("id"):
    raise SystemExit("no active WIRELESS plan")
places = get("/place")
place = (places[0] if isinstance(places, list) and places else None)
if not place or not place.get("id"):
    raise SystemExit("no place in catalog")
tech_id = None
env_tech = os.environ.get("TECH_ID")
if env_tech:
    tech_id = int(env_tech)
if tech_id is None:
    try:
        techs = get("/technician")
    except Exception:
        techs = []
    if isinstance(techs, list):
        hit = next((u for u in techs if u.get("id")), None)
        if hit:
            tech_id = hit.get("id")
if tech_id is None:
    raise SystemExit("no technician id")

dni = os.environ.get("E2E_DNI") or ("9" + ("%07d" % (int(time.time()) % 10000000)))
body = {
    "firstName": os.environ["E2E_FIRST_NAME"],
    "lastName": os.environ["E2E_LAST_NAME"],
    "dni": dni,
    "address": os.environ["E2E_ADDRESS"],
    "phone": os.environ["E2E_PHONE"],
    "subscriptionDate": int(time.time() * 1000),
    "planId": plan["id"],
    "additionalDeviceIds": [],
    "placeId": place["id"],
    "location": {
        "latitude": float(place.get("latitude") or -11.233708),
        "longitude": float(place.get("longitude") or -77.376278),
    },
    "technicianId": tech_id,
    "hostDeviceId": host_id,
    "installationType": "WIRELESS",
    "equipmentCondition": "LOAN",
    "autoCut": True,
}
status, resp = post("/subscription", body)
data = resp.get("data") if isinstance(resp, dict) else None
if not isinstance(data, dict):
    data = resp if isinstance(resp, dict) else {}
print("register_http", status, "id", data.get("id"), "accessMode", data.get("accessMode"), "has_ip", bool(data.get("ip")), "pppoe", data.get("pppoeUsername"), "mikrotik", data.get("mikrotikProvisionStatus") or data.get("mikrotikError"))
if status != 200 or not data.get("id"):
    print(json.dumps(resp, default=str)[:800])
    raise SystemExit("POST /subscription failed")
if str(data.get("accessMode")) != "STATIC_IP":
    raise SystemExit("expected STATIC_IP got %s" % data.get("accessMode"))
if not data.get("ip"):
    raise SystemExit("STATIC_IP alta sin IP")
if data.get("pppoeUsername"):
    raise SystemExit("STATIC_IP no debe emitir pppoeUsername")
print(data["id"])
PY
}

poll() {
  wait_core
  login
  curl -sS --fail --max-time 120 -X POST "$CORE/api/traffic/v1/admin/poll" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Traffic-Key: $TRAFFIC_KEY" \
    -H "Content-Type: application/json"
  echo
}

check() {
  wait_core
  login
  export TOKEN
  local sid="${1:-}"
  export CHECK_SID="$sid"
  python3 - <<'PY'
import json, os, urllib.request, urllib.error
core = os.environ["CORE"].rstrip("/")
token = os.environ["TOKEN"]
key = os.environ["TRAFFIC_KEY"]
sid = os.environ.get("CHECK_SID") or ""

def get(path, extra=None):
    headers = {"Authorization": "Bearer " + token}
    if extra:
        headers.update(extra)
    req = urllib.request.Request(core + path, headers=headers)
    with urllib.request.urlopen(req, timeout=45) as r:
        return json.load(r)

if not sid:
    rows = get("/subscription/all")
    hit = next(
        (
            s for s in rows
            if str(s.get("firstName")) == os.environ["E2E_FIRST_NAME"]
            and str(s.get("lastName")) == os.environ["E2E_LAST_NAME"]
            and str(s.get("installationType")) == "WIRELESS"
        ),
        None,
    )
    if not hit:
        raise SystemExit("no LAB STATICIP wireless sub")
    sid = str(hit["id"])

sub = get("/subscription/" + sid)
print("sub", {
    "id": sub.get("id"),
    "accessMode": sub.get("accessMode"),
    "installationType": sub.get("installationType"),
    "has_ip": bool(sub.get("ip")),
    "pppoeUsername": sub.get("pppoeUsername"),
    "hostDeviceId": (sub.get("hostDevice") or {}).get("id") if isinstance(sub.get("hostDevice"), dict) else None,
})
if str(sub.get("accessMode")) != "STATIC_IP":
    raise SystemExit("accessMode is not STATIC_IP")
if sub.get("pppoeUsername"):
    raise SystemExit("directory must not see residual pppoeUsername on STATIC_IP")

req = urllib.request.Request(
    core + "/internal/traffic/targets",
    headers={"X-Traffic-Key": key},
)
with urllib.request.urlopen(req, timeout=30) as r:
    targets = json.load(r)
entry = next((t for t in targets if str(t.get("subscriptionId")) == str(sid)), None)
print("directory", {
    "found": entry is not None,
    "ip": bool((entry or {}).get("ip")),
    "pppoeUsername": (entry or {}).get("pppoeUsername"),
    "routerHint": (entry or {}).get("routerHint"),
})
if not entry:
    raise SystemExit("subscription not in traffic directory")
if not entry.get("ip"):
    raise SystemExit("directory missing ip for STATIC_IP")
if entry.get("pppoeUsername"):
    raise SystemExit("directory leaked pppoeUsername for STATIC_IP")

try:
    latest = get("/subscription/%s/traffic/latest" % sid)
    print("latest", {
        "subscriptionId": latest.get("subscriptionId"),
        "has_bucket": bool(latest.get("bucketStart") or latest.get("polledAt")),
        "polledAt": latest.get("polledAt"),
        "avgMbpsDown": latest.get("avgMbpsDown"),
        "avgMbpsUp": latest.get("avgMbpsUp"),
    })
except urllib.error.HTTPError as e:
    print("latest_http", e.code, e.read()[:180].decode("utf-8", "replace"))
    raise

try:
    health = get("/subscription/%s/service-health" % sid)
    traffic = [x for x in (health.get("sources") or []) if x.get("source") == "TRAFFIC"]
    print("health_traffic", [
        {"metric": x.get("metric"), "quality": x.get("quality_status"), "observed_at": x.get("observed_at")}
        for x in traffic
    ])
except urllib.error.HTTPError as e:
    print("health_http", e.code)
print("STATIC_IP_TRAFFIC_LAB_OK id", sid)
PY
}

export CORE HOST_DEVICE_ID TRAFFIC_KEY E2E_USER E2E_PASSWORD E2E_FIRST_NAME E2E_LAST_NAME E2E_PHONE E2E_ADDRESS

case "$CMD" in
  seed) seed ;;
  wait) wait_core ;;
  register)
    seed
    register
    ;;
  poll) poll ;;
  check)
    check "${2:-}"
    ;;
  all)
    seed
    wait_core
    SID="$(register | tail -n 1)"
    echo "subscriptionId=$SID"
    poll
    check "$SID"
    ;;
  -h|--help|help) usage ;;
  *) usage; exit 2 ;;
esac
