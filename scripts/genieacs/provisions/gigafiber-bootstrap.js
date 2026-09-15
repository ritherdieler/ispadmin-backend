var acsUser = "gigafiber-acs";
var acsPass = "3mtc0MhiudthIQMnBiIsCF5D";
var acsUrl = "http://acs.gigafiberperu.cloud/";
var now = Date.now();

// Factory / first ACS contact only (preset events: 0 BOOTSTRAP). clear() is
// required here per GenieACS docs; do not run this script on 1 BOOT.
// Ongoing PeriodicInformInterval changes belong on gf-inform-interval (inform
// channel), not on reboot.
var serial = declare("DeviceID.SerialNumber", {value: 1}).value[0] || "";
var jitter = 0;
for (var i = 0; i < serial.length; i++) jitter = (jitter * 31 + serial.charCodeAt(i)) % 300;

// GenieACS' provision sandbox has historically been unreliable with object-map
// lookups; keep lab detection as plain string compares. Tags.lab is set for
// humans/UI; serials are the source of truth for the lab cadence.
var isLab = serial === "ZTEGDC47BFFD" || serial === "12345B4641531C0B6";
var informInterval = isLab ? 30 : (1800 + jitter);
log("gigafiber-bootstrap serial=" + serial + " isLab=" + isLab + " interval=" + informInterval);

clear("Device", now);
clear("InternetGatewayDevice", now);
declare("Tags.gigafiber", {value: now}, {value: true});
if (isLab) declare("Tags.lab", {value: now}, {value: true});

// Only the data model root the CPE exposes; see inform.js for why the absent
// root must not be declared.
var roots = ["InternetGatewayDevice", "Device"];
for (var r = 0; r < roots.length; r++) {
  var root = roots[r];
  var probe = declare(root + ".ManagementServer.URL", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".ManagementServer.Username", {value: now}, {value: acsUser});
  declare(root + ".ManagementServer.URL", {value: now}, {value: acsUrl});
  declare(root + ".ManagementServer.ConnectionRequestUsername", {value: now}, {value: acsUser});
  declare(root + ".ManagementServer.PeriodicInformEnable", {value: now}, {value: true});
  declare(root + ".ManagementServer.PeriodicInformInterval", {value: now}, {value: informInterval});
  declare(root + ".ManagementServer.Password", null, {value: acsPass});
  declare(root + ".ManagementServer.ConnectionRequestPassword", null, {value: acsPass});
}
