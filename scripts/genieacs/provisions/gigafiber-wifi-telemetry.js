const fresh = Date.now();
const model = declare("DeviceID.ProductClass", {value: 1}).value[0];
const serial = declare("DeviceID.SerialNumber", {value: 1}).value[0];
const oui = declare("DeviceID.OUI", {value: 1}).value[0];
const deviceId = oui && model && serial ? (oui + "-" + model + "-" + serial) : null;
if (serial === "12345B4641531C0B6") {
  declare("InternetGatewayDevice.ManagementServer.PeriodicInformEnable", {value: 1}, {value: true});
  declare("InternetGatewayDevice.ManagementServer.PeriodicInformInterval", {value: 1}, {value: 180});
}
if (serial) {
  ext("wifi-inform-notify", "notify", serial, deviceId);
}
const radios = model === "F6600R" || model === "V2804AX15T" ? [1, 5] : [];
const root = "InternetGatewayDevice.LANDevice.1";
if (radios.length) {
  declare(root + ".Hosts.HostNumberOfEntries", {path: fresh, value: fresh});
}
for (const radio of radios) {
  const wlan = root + ".WLANConfiguration." + radio;
  declare(wlan + ".TotalAssociations", {path: fresh, value: fresh});
  const fields = model === "F6600R"
    ? ["AssociatedDeviceMACAddress", "AssociatedDeviceRssi", "AssociatedDeviceRate", "X_ZTE-COM_WLAN_SNR", "X_ZTE-COM_WLAN_Noise", "X_ZTE-COM_WLAN_PacketSend", "X_ZTE-COM_WLAN_PacketReceived"]
    : ["AssociatedDeviceMACAddress", "X_HW_RSSI", "X_HW_SNR", "X_HW_Noise", "X_HW_RxRate", "X_HW_TxRate"];
  for (const field of fields) {
    declare(wlan + ".AssociatedDevice.*." + field, {path: fresh, value: fresh});
  }
}
