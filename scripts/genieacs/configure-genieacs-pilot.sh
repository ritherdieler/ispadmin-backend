#!/usr/bin/env bash
# Idempotent GenieACS pilot config: cwmp.auth, ACS/CR credentials, presets.
# Run on VPS: /opt/gigafiber/genieacs/configure-genieacs-pilot.sh
set -euo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/gigafiber/genieacs}"

cd "$REMOTE_DIR"
set -a
source .env
set +a

ACS_CPE_USERNAME="${ACS_CPE_USERNAME:-gigafiber-acs}"
if [[ -z "${ACS_CPE_PASSWORD:-}" ]]; then
  ACS_CPE_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=' | head -c 24)"
  if grep -q '^ACS_CPE_PASSWORD=' .env 2>/dev/null; then
    sed -i "s|^ACS_CPE_PASSWORD=.*|ACS_CPE_PASSWORD=${ACS_CPE_PASSWORD}|" .env
  else
    printf '\nACS_CPE_USERNAME=%s\nACS_CPE_PASSWORD=%s\n' "$ACS_CPE_USERNAME" "$ACS_CPE_PASSWORD" >> .env
  fi
  chmod 600 .env
fi

JS_FILE="$(mktemp /tmp/genieacs-pilot-config.XXXXXX.js)"
trap 'unlink "$JS_FILE" 2>/dev/null || true' EXIT

cat > "$JS_FILE" <<JS
const acsUser = ${ACS_CPE_USERNAME@Q};
const acsPass = ${ACS_CPE_PASSWORD@Q};
const acsUrl = "http://acs.gigafiberperu.cloud/";

// VSOL/ZTE gSOAP cannot complete HTTP Digest (401 retry / qop auth-int).
// ACS HTTP is IP-restricted in nginx; CR still uses ACS_CPE_* against the CPE.
db.config.updateOne(
  { _id: "cwmp.auth" },
  { \$set: { value: "true" } },
  { upsert: true }
);

db.config.updateOne(
  { _id: "cwmp.deviceOnlineThreshold" },
  { \$set: { value: "3600" } },
  { upsert: true }
);

db.provisions.updateOne(
  { _id: "inform" },
  {
    \$set: {
      script: \`const acsUser = "\${acsUser}";
const acsPass = "\${acsPass}";
const acsUrl = "\${acsUrl}";

// Only the data model root the CPE exposes. Declaring the absent root
// (Device.* on a TR-098-only CPE such as productClass IGD) never resolves and
// burns commit iterations until GenieACS aborts with too_many_commits.
const roots = ["InternetGatewayDevice", "Device"];
for (const root of roots) {
  const probe = declare(root + ".ManagementServer.URL", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".ManagementServer.Username", {value: 1}, {value: acsUser});
  declare(root + ".ManagementServer.Password", null, {value: acsPass});
  declare(root + ".ManagementServer.URL", {value: 1}, {value: acsUrl});
  declare(root + ".ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
  declare(root + ".ManagementServer.ConnectionRequestPassword", null, {value: acsPass});
}
\`,
    },
  },
  { upsert: true }
);

db.provisions.updateOne(
  { _id: "gigafiber-bootstrap" },
  {
    \$set: {
      script: \`const acsUser = "\${acsUser}";
const acsPass = "\${acsPass}";
const acsUrl = "\${acsUrl}";
const now = Date.now();

// The Inform interval is the only cadence knob: the 360 wants a sample every
// 30 min and each Inform is one sample. Jitter is derived from the serial so it
// is stable per CPE and the fleet does not converge on the same second.
// Lab serials use 60 s. This script runs only on 0 BOOTSTRAP (preset events).
// Ongoing cadence changes go through gf-inform-interval on the inform channel.
const serial = declare("DeviceID.SerialNumber", {value: 1}).value[0] || "";
let jitter = 0;
for (let i = 0; i < serial.length; i++) jitter = (jitter * 31 + serial.charCodeAt(i)) % 300;
const isLab = serial === "ZTEGDC47BFFD" || serial === "12345B4641531C0B6";
const informInterval = isLab ? 60 : (1800 + jitter);
log("gigafiber-bootstrap serial=" + serial + " isLab=" + isLab + " interval=" + informInterval);

clear("Device", now);
clear("InternetGatewayDevice", now);
declare("Tags.gigafiber", {value: now}, {value: true});
if (isLab) declare("Tags.lab", {value: now}, {value: true});

const roots = ["InternetGatewayDevice", "Device"];
for (const root of roots) {
  const probe = declare(root + ".ManagementServer.URL", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".ManagementServer.Username", {value: now}, {value: acsUser});
  declare(root + ".ManagementServer.Password", {value: now}, {value: acsPass});
  declare(root + ".ManagementServer.URL", {value: now}, {value: acsUrl});
  declare(root + ".ManagementServer.ConnectionRequestUsername", {value: now}, {value: acsUser});
  declare(root + ".ManagementServer.ConnectionRequestPassword", {value: now}, {value: acsPass});
  declare(root + ".ManagementServer.PeriodicInformEnable", {value: now}, {value: true});
  declare(root + ".ManagementServer.PeriodicInformInterval", {value: now}, {value: informInterval});
}\`,
    },
  },
  { upsert: true }
);

db.provisions.updateOne(
  { _id: "default" },
  {
    \$set: {
      script: \`const hourly = Date.now(3600000);

// Pinned instance indices instead of three levels of wildcard: every wildcard
// level costs a GetParameterNames round trip, and the MitraStar XC220-G3v fleet
// was exceeding the 50 ms per-revision script budget (script.Error) on them.
const WAN = "WANDevice.1.WANConnectionDevice.1.WANIPConnection.*";
const WLAN = "LANDevice.1.WLANConfiguration.*";

const roots = ["InternetGatewayDevice", "Device"];
for (const root of roots) {
  const probe = declare(root + ".DeviceInfo.SoftwareVersion", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".DeviceInfo.HardwareVersion", {path: hourly, value: hourly});
  declare(root + ".DeviceInfo.SoftwareVersion", {path: hourly, value: hourly});
  // CPEs report blank ACS/CR passwords; never overwrite GenieACS stored values.
  declare(root + ".ManagementServer.Password", {path: hourly, value: 1});
  declare(root + ".ManagementServer.ConnectionRequestPassword", {path: hourly, value: 1});
  if (root !== "InternetGatewayDevice") continue;
  declare(root + "." + WAN + ".MACAddress", {path: hourly, value: hourly});
  declare(root + "." + WAN + ".ExternalIPAddress", {path: hourly, value: hourly});
  declare(root + "." + WLAN + ".SSID", {path: hourly, value: hourly});
  declare(root + "." + WLAN + ".KeyPassphrase", {path: hourly, value: 1});
}\`,
    },
  },
  { upsert: true }
);

db.provisions.updateOne(
  { _id: "tplg-router" },
  {
    \$set: {
      script: \`const hourly = Date.now(3600000);
declare("Tags", {value: ["gigafiber", "tplg"]}, {value: ["gigafiber", "tplg"]});
declare("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", {path: hourly, value: hourly});
declare("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", {path: hourly, value: 1});
declare("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress", {path: hourly, value: hourly});\`,
    },
  },
  { upsert: true }
);

db.provisions.updateOne(
  { _id: "mstc-router" },
  {
    \$set: {
      script: \`const hourly = Date.now(3600000);
declare("Tags", {value: ["gigafiber", "mstc"]}, {value: ["gigafiber", "mstc"]});
declare("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", {path: hourly, value: hourly});
declare("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", {path: hourly, value: 1});
declare("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress", {path: hourly, value: hourly});\`,
    },
  },
  { upsert: true }
);

db.presets.updateOne(
  { _id: "bootstrap" },
  {
    \$set: {
      weight: 0,
      channel: "bootstrap",
      // GenieACS seed + docs: events are AND. Official bootstrap is 0 BOOTSTRAP
      // only (factory / first ACS contact). "0 BOOT" is not a TR-069 event;
      // requiring it together with 1 BOOT made reboot and factory never match.
      events: { "0 BOOTSTRAP": true },
      precondition: "",
      configurations: [
        { type: "provision", name: "gigafiber-bootstrap", args: null },
      ],
    },
  },
  { upsert: true }
);

db.presets.updateOne(
  { _id: "tplg-router" },
  {
    \$set: {
      weight: 10,
      channel: "default",
      events: {},
      precondition:
        'DeviceID.SerialNumber LIKE "TPLG%" OR DeviceID.Manufacturer LIKE "%TP-Link%"',
      configurations: [{ type: "provision", name: "tplg-router", args: null }],
    },
  },
  { upsert: true }
);

db.presets.updateOne(
  { _id: "mstc-router" },
  {
    \$set: {
      weight: 10,
      channel: "default",
      events: {},
      precondition:
        'DeviceID.SerialNumber LIKE "MSTC%" OR DeviceID.Manufacturer LIKE "%MitraStar%"',
      configurations: [{ type: "provision", name: "mstc-router", args: null }],
    },
  },
  { upsert: true }
);

print("Configured cwmp.auth=true (no HTTP Digest); CR user: " + acsUser);
print("Provisions: inform, gigafiber-bootstrap, default (CR-safe), tplg-router, mstc-router");
JS

docker compose exec -T mongo mongosh \
  -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" \
  --authenticationDatabase admin genieacs --quiet < "$JS_FILE"

docker compose restart genieacs
sleep 12
docker compose ps

echo "ACS CPE user: ${ACS_CPE_USERNAME}"
echo "ACS CPE password stored in ${REMOTE_DIR}/.env (ACS_CPE_PASSWORD)"
