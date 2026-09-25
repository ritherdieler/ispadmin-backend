// args[0]: JSON {operationId, expectedSerial, expectedModel, expectedFirmware, ssid24, ssid5, passphrase24, passphrase5}.
// Only WLAN leaves are writable. The caller must persist the encrypted pre-change snapshot first.
const WIFI_LAYOUTS = { F6600R: [1, 5], VSOLVA74: [5, 1] };
function readValue(path) {
  const result = declare(path, { value: Date.now() });
  return result.value && result.value[0];
}
function validatedRequest() {
  const request = JSON.parse(args[0]);
  if (!/^[a-zA-Z0-9-]{1,64}$/.test(request.operationId || '')) throw new Error('V2_INVALID_OPERATION');
  if (!request.expectedSerial || !request.expectedFirmware ||
      readValue('DeviceID.SerialNumber') !== request.expectedSerial ||
      readValue('DeviceID.ProductClass') !== request.expectedModel ||
      readValue('InternetGatewayDevice.DeviceInfo.SoftwareVersion') !== request.expectedFirmware) throw new Error('V2_IDENTITY_MISMATCH');
  if (!WIFI_LAYOUTS[request.expectedModel]) throw new Error('V2_MODEL_UNSUPPORTED');
  for (const passphrase of [request.passphrase24, request.passphrase5]) {
    if (typeof passphrase !== 'string' || passphrase.length < 8 || passphrase.length > 63) throw new Error('V2_INVALID_PASSPHRASE');
  }
  for (const ssid of [request.ssid24, request.ssid5]) {
    if (typeof ssid !== 'string' || !ssid.length || unescape(encodeURIComponent(ssid)).length > 32) throw new Error('V2_INVALID_SSID');
  }
  return request;
}
function applyBand(index, ssid, passphrase) {
  const base = 'InternetGatewayDevice.LANDevice.1.WLANConfiguration.' + index;
  declare(base + '.SSID', null, { value: ssid });
  declare(base + '.KeyPassphrase', null, { value: passphrase });
  declare(base + '.Enable', null, { value: true });
  commit();
}
function applyWifi() {
  const request = validatedRequest();
  const layout = WIFI_LAYOUTS[request.expectedModel];
  applyBand(layout[0], request.ssid24, request.passphrase24);
  applyBand(layout[1], request.ssid5, request.passphrase5);
  log('gf-onboarding-v2-wifi applied operation=' + request.operationId);
}
applyWifi();
