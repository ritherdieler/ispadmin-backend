#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPS="$ROOT/src/main/resources/application-local.properties"
MYSQL="${MYSQL_BIN:-/opt/homebrew/opt/mysql-client/bin/mysql}"
E2E_USER="${E2E_USER:-dscorp}"
E2E_PASSWORD="${E2E_PASSWORD:-nohacker}"
E2E_PLACE="${E2E_PLACE:-9 de octubre}"
E2E_NAP_CODE="${E2E_NAP_CODE:-NO-001}"
E2E_ONU_SN="${E2E_ONU_SN:-ZTEGDC47BFFD}"
CORE="${CORE_BASE:-http://127.0.0.1:8082/ispadmin}"

if [[ -z "${MYSQL_PWD:-}" && -f "$LOCAL_PROPS" ]]; then
  MYSQL_PWD="$(grep '^spring.datasource.password=' "$LOCAL_PROPS" | cut -d= -f2-)"
  export MYSQL_PWD
fi
[[ -n "${MYSQL_PWD:-}" ]] || { echo "MYSQL_PWD missing" >&2; exit 1; }
[[ -x "$MYSQL" ]] || { echo "mysql client missing: $MYSQL" >&2; exit 1; }

export E2E_USER E2E_PASSWORD MYSQL_BIN="$MYSQL"
HASH="$(python3 - <<'PY'
import hashlib, os, base64
raw = os.environ["E2E_PASSWORD"]
sha = hashlib.sha384(raw.encode("utf-8")).hexdigest()
salt = os.urandom(16)
dk = hashlib.pbkdf2_hmac("sha256", sha.encode("utf-8"), salt, 120000, dklen=32)
print("pbkdf2$120000$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(dk).decode())
PY
)"

export E2E_USER E2E_PASSWORD HASH
python3 - <<'PY'
import os, subprocess
user = os.environ["E2E_USER"]
name = "Sergio"
last = "Carrillo Diestra"
hashed = os.environ["HASH"]
mysql = os.environ.get("MYSQL_BIN", "/opt/homebrew/opt/mysql-client/bin/mysql")
sql = f"""
INSERT INTO user (name, last_name, username, password, type, verified)
SELECT '{name}', '{last}', '{user}', '{hashed}', 'ADMIN', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = '{user}');
UPDATE user
SET password = '{hashed}', type = 'ADMIN', verified = 1, name = IFNULL(name, '{name}'), last_name = IFNULL(last_name, '{last}')
WHERE username = '{user}';
SELECT CONCAT('user=', username, ' type=', type, ' verified=', verified) FROM user WHERE username = '{user}';
"""
out = subprocess.check_output([mysql, "-uroot", "ispadmin_dev", "-e", sql], text=True)
print(out.strip())
PY

fail=0
check() {
  local label="$1"
  local sql="$2"
  local got
  got="$("$MYSQL" -uroot ispadmin_dev -N -e "$sql" || true)"
  if [[ -z "$got" || "$got" == "0" ]]; then
    echo "missing $label" >&2
    fail=1
  else
    echo "ok $label $got"
  fi
}

check "place $E2E_PLACE" "SELECT CONCAT(id, ':', name) FROM place WHERE LOWER(name) = LOWER('$E2E_PLACE') LIMIT 1;"
check "nap $E2E_NAP_CODE" "SELECT CONCAT(id, ':', code) FROM nap_box WHERE REPLACE(UPPER(code),' ','') = REPLACE(UPPER('$E2E_NAP_CODE'),' ','') LIMIT 1;"
check "FIBER plan" "SELECT CONCAT(id, ':', name) FROM plan WHERE type = 'FIBER' AND is_active = 1 LIMIT 1;"
check "network_device 8" "SELECT CONCAT(id, ':', name) FROM network_device WHERE id = 8 AND (disabled = 0 OR disabled IS NULL) LIMIT 1;"
check "ip_pool MK2" "SELECT CONCAT(id, ':', ip_segment) FROM ip_pool WHERE host_device_id = 8 AND is_eligible = 1 LIMIT 1;"
check "verified technician" "SELECT CONCAT(id, ':', username) FROM user WHERE type = 'TECHNICIAN' AND verified = 1 LIMIT 1;"

if [[ "$fail" -ne 0 ]]; then
  echo "local e2e catalog incomplete in ispadmin_dev" >&2
  exit 1
fi

export CORE E2E_ONU_SN
python3 - <<'PY'
import json, os, re, urllib.request, urllib.error
core = os.environ["CORE"].rstrip("/")
user = os.environ["E2E_USER"]
password = os.environ["E2E_PASSWORD"]
wanted = re.sub(r"[^A-Z0-9]", "", os.environ["E2E_ONU_SN"].upper())
req = urllib.request.Request(
    f"{core}/users/login",
    data=json.dumps({"username": user, "password": password, "checkBox": False}).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=20) as r:
        token = json.loads(r.read().decode()).get("accessToken") or ""
except urllib.error.HTTPError as e:
    raise SystemExit(f"Core login failed for unconfigured check HTTP {e.code}")
if not token:
    raise SystemExit("Core login failed for unconfigured check")
req = urllib.request.Request(
    f"{core}/onu/unconfigured_onus",
    headers={"Authorization": f"Bearer {token}"},
)
with urllib.request.urlopen(req, timeout=45) as r:
    data = json.loads(r.read().decode())
items = data if isinstance(data, list) else (data.get("response") or data.get("onus") or data.get("data") or [])
if isinstance(items, dict):
    items = items.get("onus") or items.get("data") or []
sns = [str((i or {}).get("sn") or "") for i in items]
compact = [re.sub(r"[^A-Z0-9]", "", s.upper()) for s in sns]
if any("ALCL12345678" in c or "ALCL87654321" in c for c in compact):
    raise SystemExit(
        "Core sirve MockOltService (ALCL*). Pon olt.service.mock.enabled=false "
        "en application-local.properties y reinicia el Core."
    )
wanted_hex = "5A544547" + wanted[4:] if wanted.startswith("ZTEG") else wanted
if not any(wanted in c or wanted_hex in c for c in compact):
    raise SystemExit(f"ONU lab {os.environ['E2E_ONU_SN']} ausente en Core unconfigured_onus sns={sns[:12]}")
print("ok Core unconfigured", os.environ["E2E_ONU_SN"], "count", len(sns))
PY

echo "LOCAL_E2E_CATALOG_OK user=$E2E_USER"
