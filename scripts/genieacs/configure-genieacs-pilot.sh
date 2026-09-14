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

declare("InternetGatewayDevice.ManagementServer.Username", {value: 1}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.Password", null, {value: acsPass});
declare("InternetGatewayDevice.ManagementServer.URL", {value: 1}, {value: acsUrl});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", null, {value: acsPass});

declare("Device.ManagementServer.Username", {value: 1}, {value: acsUser});
declare("Device.ManagementServer.Password", null, {value: acsPass});
declare("Device.ManagementServer.URL", {value: 1}, {value: acsUrl});
declare("Device.ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
declare("Device.ManagementServer.ConnectionRequestPassword", null, {value: acsPass});
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
const informInterval = 3600;
const now = Date.now();

clear("Device", now);
clear("InternetGatewayDevice", now);
declare("Tags", {value: now}, {value: ["gigafiber"]});

declare("InternetGatewayDevice.ManagementServer.Username", {value: now}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.Password", {value: now}, {value: acsPass});
declare("InternetGatewayDevice.ManagementServer.URL", {value: now}, {value: acsUrl});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {value: now}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", {value: now}, {value: acsPass});
declare("InternetGatewayDevice.ManagementServer.PeriodicInformEnable", {value: now}, {value: true});
declare("InternetGatewayDevice.ManagementServer.PeriodicInformInterval", {value: now}, {value: informInterval});

declare("Device.ManagementServer.Username", {value: now}, {value: acsUser});
declare("Device.ManagementServer.Password", {value: now}, {value: acsPass});
declare("Device.ManagementServer.URL", {value: now}, {value: acsUrl});
declare("Device.ManagementServer.ConnectionRequestUsername", {value: now}, {value: acsUser});
declare("Device.ManagementServer.ConnectionRequestPassword", {value: now}, {value: acsPass});
declare("Device.ManagementServer.PeriodicInformEnable", {value: now}, {value: true});
declare("Device.ManagementServer.PeriodicInformInterval", {value: now}, {value: informInterval});\`,
    },
  },
  { upsert: true }
);

db.provisions.updateOne(
  { _id: "default" },
  {
    \$set: {
      script: \`const hourly = Date.now(3600000);

declare("InternetGatewayDevice.DeviceInfo.HardwareVersion", {path: hourly, value: hourly});
declare("InternetGatewayDevice.DeviceInfo.SoftwareVersion", {path: hourly, value: hourly});
declare("InternetGatewayDevice.WANDevice.*.WANConnectionDevice.*.WANIPConnection.*.MACAddress", {path: hourly, value: hourly});
declare("InternetGatewayDevice.WANDevice.*.WANConnectionDevice.*.WANIPConnection.*.ExternalIPAddress", {path: hourly, value: hourly});
declare("InternetGatewayDevice.LANDevice.*.WLANConfiguration.*.SSID", {path: hourly, value: hourly});
declare("InternetGatewayDevice.LANDevice.*.WLANConfiguration.*.KeyPassphrase", {path: hourly, value: 1});
// CPEs report blank ACS/CR passwords; never overwrite GenieACS stored values.
declare("InternetGatewayDevice.ManagementServer.Password", {path: hourly, value: 1});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", {path: hourly, value: 1});
declare("Device.ManagementServer.Password", {path: hourly, value: 1});
declare("Device.ManagementServer.ConnectionRequestPassword", {path: hourly, value: 1});\`,
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
      events: { "0 BOOTSTRAP": true, "0 BOOT": true, "1 BOOT": true },
      precondition: "",
      configurations: [
        { type: "provision", name: "bootstrap", args: null },
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
