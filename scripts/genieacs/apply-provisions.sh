#!/usr/bin/env bash
# Push GenieACS provision scripts and presets via NBI API.
# Usage: GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-provisions.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NBI_URL="${GENIEACS_NBI_URL:-http://127.0.0.1:7557}"

put_provision() {
  local id="$1"
  local file="$2"
  curl -sf -X PUT "${NBI_URL}/provisions/${id}" \
    -H 'Content-Type: text/javascript' \
    --data-binary "@${file}" \
    -w "provision ${id}: HTTP %{http_code}\n" -o /dev/null
}

put_preset() {
  local id="$1"
  local json="$2"
  curl -sf -X PUT "${NBI_URL}/presets/${id}" \
    -H 'Content-Type: application/json' \
    -d "${json}" \
    -w "preset ${id}: HTTP %{http_code}\n" -o /dev/null
}

echo "GenieACS NBI: ${NBI_URL}"

put_provision "inform" "${ROOT}/provisions/inform.js"
put_provision "default" "${ROOT}/provisions/default.js"
put_provision "gigafiber-bootstrap" "${ROOT}/provisions/gigafiber-bootstrap.js"
put_provision "gf-inform-interval" "${ROOT}/provisions/gf-inform-interval.js"
put_provision "huawei-writeonly-acs-credentials" "${ROOT}/provisions/huawei-writeonly-acs-credentials.js"

put_preset "bootstrap" "$(cat <<'EOF'
{
  "weight": 0,
  "channel": "bootstrap",
  "events": { "0 BOOTSTRAP": true },
  "precondition": "",
  "configurations": [
    { "type": "provision", "name": "gigafiber-bootstrap", "args": null }
  ]
}
EOF
)"

put_preset "gf-inform-interval" "$(cat <<'EOF'
{
  "weight": 5,
  "channel": "inform",
  "events": {},
  "precondition": "DeviceID.SerialNumber = \"ZTEGDC47BFFD\" OR DeviceID.SerialNumber = \"12345B4641531C0B6\"",
  "configurations": [
    { "type": "provision", "name": "gf-inform-interval", "args": [] }
  ]
}
EOF
)"

put_preset "huawei-acs-credentials" "$(cat <<'EOF'
{
  "_id": "huawei-acs-credentials",
  "weight": 100,
  "channel": "default",
  "precondition": "DeviceID.OUI = \"00259E\" OR DeviceID.Manufacturer LIKE \"%Huawei%\" OR DeviceID.ProductClass LIKE \"%HG8145%\"",
  "events": {},
  "configurations": [
    {
      "type": "provision",
      "name": "huawei-writeonly-acs-credentials",
      "args": null
    }
  ]
}
EOF
)"

echo "Done."
