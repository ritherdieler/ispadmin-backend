var acsUser = "gigafiber-acs";
var acsPass = "3mtc0MhiudthIQMnBiIsCF5D";
var acsUrl = "http://acs.gigafiberperu.cloud/";

// Only the data model root the CPE actually exposes. Declaring the absent root
// (Device.* on a TR-098-only CPE such as productClass IGD) never resolves and
// burns commit iterations until GenieACS aborts with too_many_commits.
var roots = ["InternetGatewayDevice", "Device"];
for (var i = 0; i < roots.length; i++) {
  var root = roots[i];
  var probe = declare(root + ".ManagementServer.URL", {value: 1});
  if (!probe || !probe.size) continue;
  declare(root + ".ManagementServer.Username", {value: 1}, {value: acsUser});
  declare(root + ".ManagementServer.URL", {value: 1}, {value: acsUrl});
  declare(root + ".ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
  declare(root + ".ManagementServer.Password", null, {value: acsPass});
  declare(root + ".ManagementServer.ConnectionRequestPassword", null, {value: acsPass});
}
