// The Inform is the only source of truth for the 360. This provision refreshes
// the WLAN leaves and hands their values to the ext, so the ACS never reads the
// NBI: GenieACS commits the parameter tree when the session closes, so anything
// read during the session belongs to the previous one.
//
// Values carry no per-leaf timestamp on purpose. They were declared fresh in
// this session, so the ACS stamps them all with `at` (the session clock).
const fresh = Date.now();
const ROOT = "InternetGatewayDevice.LANDevice.1";
const MAX_HOSTS = 64;
const MAX_STATIONS = 32;

const model = declare("DeviceID.ProductClass", {value: 1}).value[0];
const serial = declare("DeviceID.SerialNumber", {value: 1}).value[0];
const oui = declare("DeviceID.OUI", {value: 1}).value[0];
const deviceId = oui && model && serial ? (oui + "-" + model + "-" + serial) : null;

const radios = model === "F6600R" || model === "V2804AX15T" ? [1, 5] : [];
const leaves = {};

function relative(path) {
  return path.substring(ROOT.length + 1);
}

function keep(path, declaration) {
  if (!declaration || !declaration.value) return null;
  leaves[relative(path)] = declaration.value[0];
  return declaration.value[0];
}

// Wildcards discover the instances that actually exist. Enumerating fixed
// indices declares absent instances, which never resolve and burn commit
// iterations until GenieACS aborts with too_many_commits.
function keepAll(declaration, limit) {
  if (!declaration) return;
  let kept = 0;
  for (const instance of declaration) {
    if (kept >= limit) break;
    if (!instance || !instance.value) continue;
    leaves[relative(instance.path)] = instance.value[0];
    kept += 1;
  }
}

if (radios.length) {
  const hosts = ROOT + ".Hosts";
  keep(hosts + ".HostNumberOfEntries", declare(hosts + ".HostNumberOfEntries", {path: fresh, value: fresh}));
  keepAll(declare(hosts + ".Host.*.MACAddress", {path: fresh, value: fresh}), MAX_HOSTS);
  keepAll(declare(hosts + ".Host.*.HostName", {path: fresh, value: fresh}), MAX_HOSTS);
}

for (const radio of radios) {
  const wlan = ROOT + ".WLANConfiguration." + radio;
  const total = Number(keep(wlan + ".TotalAssociations", declare(wlan + ".TotalAssociations", {path: fresh, value: fresh})));
  if (!(total > 0)) continue;
  const fields = model === "F6600R"
    ? ["AssociatedDeviceMACAddress", "AssociatedDeviceRssi", "AssociatedDeviceRate", "X_ZTE-COM_WLAN_SNR", "X_ZTE-COM_WLAN_Noise", "X_ZTE-COM_WLAN_PacketSend", "X_ZTE-COM_WLAN_PacketReceived"]
    : ["AssociatedDeviceMACAddress", "X_HW_RSSI", "X_HW_SNR", "X_HW_Noise", "X_HW_RxRate", "X_HW_TxRate"];
  const wanted = total > MAX_STATIONS ? MAX_STATIONS : total;
  for (const field of fields) {
    keepAll(declare(wlan + ".AssociatedDevice.*." + field, {path: fresh, value: fresh}), wanted);
  }
}

// After the declares: the ext carries the values, so it must run once they
// resolved. Never wrap this in try/catch, it swallows the EXT/COMMIT symbols.
if (serial) {
  ext("wifi-inform-notify", "notify", serial, deviceId, JSON.stringify({
    v: 1,
    serial: serial,
    deviceId: deviceId,
    model: model,
    at: fresh,
    root: ROOT,
    leaves: leaves,
  }));
}
