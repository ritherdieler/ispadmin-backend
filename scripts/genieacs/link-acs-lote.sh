#!/usr/bin/env bash
# Link GenieACS devices 1:1 to Core subscriptions without TR-069 provision.
#
#   CORE_BASE=https://api.gigafiberperu.cloud/ispadmin \
#     ./scripts/genieacs/link-acs-lote.sh --prod --dry-run --file /tmp/vsol-tr069-inverted-todo.tsv
#   ./scripts/genieacs/link-acs-lote.sh --prod --file /tmp/vsol-tr069-inverted-todo.tsv
#   ./scripts/genieacs/link-acs-lote.sh --prod --from-ghosts --dry-run
#   ./scripts/genieacs/link-acs-lote.sh --prod --from-ghosts
#   ./scripts/genieacs/link-acs-lote.sh --prod --list-ghosts
#   ./scripts/genieacs/link-acs-lote.sh --prod --delete-ghosts   # explicit; deletes NBI ghosts only
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE_BASE="${CORE_BASE:-http://127.0.0.1:8082/ispadmin}"
E2E_USER="${E2E_USER:-${CORE_USER:-dscorp}}"
E2E_PASSWORD="${E2E_PASSWORD:-${CORE_PASSWORD:-nohacker}}"
CONFIG_LOCAL="${DEPLOY_CONFIG_LOCAL:-$ROOT/scripts/deploy.config.local}"
FILE=""
DRY_RUN=0
LIST_GHOSTS=0
DELETE_GHOSTS=0
FROM_GHOSTS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prod) CORE_BASE="https://api.gigafiberperu.cloud/ispadmin"; shift ;;
    --core) CORE_BASE="${2:-}"; shift 2 ;;
    --file) FILE="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --list-ghosts) LIST_GHOSTS=1; shift ;;
    --from-ghosts) FROM_GHOSTS=1; shift ;;
    --delete-ghosts) DELETE_GHOSTS=1; shift ;;
    -h|--help)
      sed -n '2,10p' "$0"
      exit 0
      ;;
    *) echo "Arg desconocido: $1" >&2; exit 1 ;;
  esac
done

if [[ -f "$CONFIG_LOCAL" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$CONFIG_LOCAL"
  set +a
fi

if [[ -z "$FILE" ]]; then
  FILE="${ACS_LINK_FILE:-/tmp/vsol-tr069-inverted-todo.tsv}"
fi

CORE_BASE="${CORE_BASE%/}"
export ROOT CORE_BASE E2E_USER E2E_PASSWORD FILE DRY_RUN LIST_GHOSTS DELETE_GHOSTS FROM_GHOSTS

python3 <<'PY'
import json, os, ssl, sys, time, urllib.error, urllib.request

core = os.environ["CORE_BASE"]
user = os.environ["E2E_USER"]
password = os.environ["E2E_PASSWORD"]
path = os.environ["FILE"]
dry_run = os.environ.get("DRY_RUN") == "1"
list_ghosts = os.environ.get("LIST_GHOSTS") == "1"
delete_ghosts = os.environ.get("DELETE_GHOSTS") == "1"
from_ghosts = os.environ.get("FROM_GHOSTS") == "1"

def ssl_context():
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()

SSL_CTX = ssl_context()

def http(method, url_path, body=None, token=None, timeout=80):
    data = None
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(core + url_path, data=data, method=method, headers=headers)
    last = None
    for _ in range(4):
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
                raw = resp.read()
                parsed = json.loads(raw) if raw else None
                return resp.status, parsed
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            try:
                parsed = json.loads(raw) if raw else None
            except Exception:
                parsed = raw[:400].decode("utf-8", "replace")
            return exc.code, parsed
        except urllib.error.URLError as exc:
            last = exc
            time.sleep(2)
    raise SystemExit("HTTP URLError %s %s%s" % (last, core, url_path))

st, login = http("POST", "/users/login", {"username": user, "password": password}, timeout=20)
token = login.get("accessToken") if isinstance(login, dict) else None
if not token:
    raise SystemExit("Core login failed HTTP %s on %s" % (st, core))
print("CORE login HTTP", st, "base", core)

if list_ghosts or delete_ghosts or from_ghosts:
    st, ghosts = http("GET", "/subscription/acs/ghosts", token=token, timeout=120)
    rows = ghosts if isinstance(ghosts, list) else []
    print("GHOSTS", len(rows), "HTTP", st)
    if list_ghosts or delete_ghosts:
        for row in rows:
            print("GHOST", row.get("deviceId"), row.get("suffix"), row.get("lastInform"))
            if delete_ghosts:
                dst, dbody = http(
                    "POST",
                    "/subscription/acs/ghosts/delete",
                    {"deviceId": row.get("deviceId")},
                    token=token,
                    timeout=60,
                )
                print("DELETE", dst, row.get("deviceId"), dbody)
        raise SystemExit(0)
    device_ids = [row.get("deviceId") for row in rows if row.get("deviceId")]
    counts = {"LINKED": 0, "SKIP_NONE": 0, "SKIP_AMBIGUOUS": 0, "SKIP_NOT_ACTIVE": 0, "SKIP_DEVICE_NOT_FOUND": 0, "OTHER": 0}
    for device_id in device_ids:
        st, body = http(
            "POST",
            "/subscription/acs/link",
            {"deviceId": device_id, "dryRun": dry_run},
            token=token,
            timeout=60,
        )
        status = body.get("status") if isinstance(body, dict) else None
        sub_id = body.get("subscriptionId") if isinstance(body, dict) else None
        key = status if status in counts else "OTHER"
        counts[key] += 1
        print("LINK", "DRY" if dry_run else "WRITE", st, status, device_id, sub_id)
    print("SUMMARY", json.dumps(counts), "dryRun", dry_run, "fromGhosts", True)
    raise SystemExit(0)

if not os.path.isfile(path):
    raise SystemExit("Falta lote %s" % path)

counts = {"LINKED": 0, "SKIP_NONE": 0, "SKIP_AMBIGUOUS": 0, "SKIP_NOT_ACTIVE": 0, "SKIP_DEVICE_NOT_FOUND": 0, "OTHER": 0}
with open(path, encoding="utf-8") as fh:
    for line in fh:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        device_id = line.split("\t", 1)[0].strip()
        if not device_id:
            continue
        st, body = http(
            "POST",
            "/subscription/acs/link",
            {"deviceId": device_id, "dryRun": dry_run},
            token=token,
            timeout=60,
        )
        status = body.get("status") if isinstance(body, dict) else None
        sub_id = body.get("subscriptionId") if isinstance(body, dict) else None
        key = status if status in counts else "OTHER"
        counts[key] += 1
        print("LINK", "DRY" if dry_run else "WRITE", st, status, device_id, sub_id)

print("SUMMARY", json.dumps(counts), "dryRun", dry_run)
PY
