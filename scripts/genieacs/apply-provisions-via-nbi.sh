#!/usr/bin/env bash
# Push GenieACS provisions via NBI (local tunnel or direct URL).
# Usage: GENIEACS_NBI_URL=http://127.0.0.1:7557 ./apply-provisions-via-nbi.sh
#
# The scripts under provisions/ are the source of truth. Credentials are
# substituted at push time so secrets can come from the environment.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"
NBI_URL="${NBI_URL%/}"
ACS_CPE_USERNAME="${ACS_CPE_USERNAME:-gigafiber-acs}"
ACS_URL="${ACS_URL:-http://acs.gigafiberperu.cloud/}"

export ROOT NBI_URL ACS_CPE_USERNAME ACS_URL

python3 <<'PY'
import json
import os
import re
import urllib.request
from pathlib import Path

nbi = os.environ["NBI_URL"].rstrip("/")
root = Path(os.environ["ROOT"]) / "provisions"
user = os.environ["ACS_CPE_USERNAME"]
url = os.environ["ACS_URL"]
password = os.environ.get("ACS_CPE_PASSWORD", "")

if not password:
    with urllib.request.urlopen(f"{nbi}/provisions/", timeout=10) as resp:
        provisions = json.load(resp)
    script = next((p["script"] for p in provisions if p["_id"] == "inform"), "")
    match = re.search(r'acsPass\s*=\s*"([^"]+)"', script)
    if not match:
        raise SystemExit("ACS_CPE_PASSWORD not set and could not be read from inform provision")
    password = match.group(1)


def substitute(script: str) -> str:
    for name, value in (("acsUser", user), ("acsPass", password), ("acsUrl", url)):
        script = re.sub(
            rf'((?:var|const|let)\s+{name}\s*=\s*)"[^"]*"',
            lambda m, v=value: m.group(1) + json.dumps(v),
            script,
            count=1,
        )
    return script


def put_provision(provision_id: str, filename: str) -> None:
    script = substitute((root / filename).read_text(encoding="utf-8"))
    req = urllib.request.Request(
        f"{nbi}/provisions/{provision_id}",
        data=script.encode("utf-8"),
        method="PUT",
        headers={"Content-Type": "text/plain; charset=utf-8"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        print(f"{provision_id}: HTTP {resp.status}")


for provision_id, filename in (
    ("inform", "inform.js"),
    ("gigafiber-bootstrap", "gigafiber-bootstrap.js"),
    ("default", "default.js"),
    ("gf-inform-interval", "gf-inform-interval.js"),
):
    put_provision(provision_id, filename)

print("Done.")
PY
