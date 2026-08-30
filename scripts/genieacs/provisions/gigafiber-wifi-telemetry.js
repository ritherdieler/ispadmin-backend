// Read-only telemetry. The preset must restrict this provision to 2 PERIODIC and pilot IDs.
const hourly = Date.now(3600000);
const model = declare("DeviceID.ProductClass", {value: 1}).value[0];
const radios = model === "F6600R" ? [1, 5] : model === "V2804AX15T" ? [1] : [];
const root = "InternetGatewayDevice.LANDevice.1";
if (radios.length) {
  declare(root + ".Hosts.HostNumberOfEntries", {path: hourly, value: hourly});
}
for (const radio of radios) {
  const wlan = root + ".WLANConfiguration." + radio;
  declare(wlan + ".TotalAssociations", {path: hourly, value: hourly});
  const fields = model === "F6600R"
    ? ["AssociatedDeviceMACAddress", "AssociatedDeviceRssi", "AssociatedDeviceRate", "X_ZTE-COM_WLAN_SNR", "X_ZTE-COM_WLAN_Noise", "X_ZTE-COM_WLAN_PacketSend", "X_ZTE-COM_WLAN_PacketReceived"]
    : ["AssociatedDeviceMACAddress", "X_HW_RSSI", "X_HW_SNR", "X_HW_Noise", "X_HW_RxRate", "X_HW_TxRate"];
  for (const field of fields) {
    declare(wlan + ".AssociatedDevice.*." + field, {path: hourly, value: hourly});
  }
}
