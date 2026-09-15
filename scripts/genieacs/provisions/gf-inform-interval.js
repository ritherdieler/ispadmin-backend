// Cadence knob on the Inform channel (not bootstrap). Change informInterval
// and bump INTERVAL_VERSION (unix ms) so GenieACS SPVs devices whose last
// write is older than this version. An older VERSION is ignored.
var INTERVAL_VERSION = 1789473000000;
var serial = declare("DeviceID.SerialNumber", {value: 1}).value[0] || "";
var jitter = 0;
for (var i = 0; i < serial.length; i++) jitter = (jitter * 31 + serial.charCodeAt(i)) % 300;
var isLab = serial === "ZTEGDC47BFFD" || serial === "12345B4641531C0B6";
var informInterval = isLab ? 30 : (1800 + jitter);
log("gf-inform-interval serial=" + serial + " isLab=" + isLab + " interval=" + informInterval + " v=" + INTERVAL_VERSION);

var roots = ["InternetGatewayDevice", "Device"];
for (var r = 0; r < roots.length; r++) {
  var root = roots[r];
  var probe = declare(root + ".ManagementServer.URL", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".ManagementServer.PeriodicInformEnable", {value: INTERVAL_VERSION}, {value: true});
  declare(root + ".ManagementServer.PeriodicInformInterval", {value: INTERVAL_VERSION}, {value: informInterval});
}
