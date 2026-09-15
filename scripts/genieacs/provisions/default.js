var hourly = Date.now(3600000);

// Pinned instance indices instead of three levels of wildcard: every wildcard
// level costs a GetParameterNames round trip, and the MitraStar XC220-G3v fleet
// was exceeding the 50 ms per-revision script budget (script.Error) on them.
var WAN = "WANDevice.1.WANConnectionDevice.1.WANIPConnection.*";
var WLAN = "LANDevice.1.WLANConfiguration.*";

// Only the data model root the CPE exposes; see inform.js for why the absent
// root must not be declared.
var roots = ["InternetGatewayDevice", "Device"];
for (var i = 0; i < roots.length; i++) {
  var root = roots[i];
  var probe = declare(root + ".DeviceInfo.SoftwareVersion", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".DeviceInfo.HardwareVersion", {path: hourly, value: hourly});
  declare(root + ".DeviceInfo.SoftwareVersion", {path: hourly, value: hourly});
  declare(root + ".ManagementServer.Password", {path: hourly, value: 1});
  declare(root + ".ManagementServer.ConnectionRequestPassword", {path: hourly, value: 1});
  if (root !== "InternetGatewayDevice") continue;
  declare(root + "." + WAN + ".MACAddress", {path: hourly, value: hourly});
  declare(root + "." + WAN + ".ExternalIPAddress", {path: hourly, value: hourly});
  declare(root + "." + WLAN + ".SSID", {path: hourly, value: hourly});
  // {value: 1} keeps whatever is cached; never fetch the passphrase.
  declare(root + "." + WLAN + ".KeyPassphrase", {path: hourly, value: 1});
}
