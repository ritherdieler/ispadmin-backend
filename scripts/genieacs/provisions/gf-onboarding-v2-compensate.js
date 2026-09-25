// args[0]: JSON identity, operationId, optional owned pppPath, previousWifi snapshot, mode=wifi|internet|all.
// The caller retains management access until this task is verified complete.
const WAN_ROOT = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.';
const RESTORE_LAYOUTS = {
  F6600R: { wcd: 1, bands: [1, 5], provisioning: WAN_ROOT + '1.WANIPConnection.1' },
  VSOLVA74: { wcd: 2, bands: [5, 1], provisioning: WAN_ROOT + '1.WANIPConnection.1' },
  V2804AX15T: { wcd: 2, bands: [5, 1], provisioning: WAN_ROOT + '1.WANIPConnection.1' },
};
function restoreValue(path) {
  const item = declare(path, { value: Date.now() });
  return item.value && item.value[0];
}
function validateCompensation() {
  const request = JSON.parse(args[0]);
  if (!/^[a-zA-Z0-9-]{1,64}$/.test(request.operationId || '')) throw new Error('V2_INVALID_OPERATION');
  if (!request.expectedSerial || !request.expectedFirmware ||
      restoreValue('DeviceID.SerialNumber') !== request.expectedSerial || restoreValue('DeviceID.ProductClass') !== request.expectedModel ||
      restoreValue('InternetGatewayDevice.DeviceInfo.SoftwareVersion') !== request.expectedFirmware) throw new Error('V2_IDENTITY_MISMATCH');
  const layout = RESTORE_LAYOUTS[request.expectedModel];
  if (!layout) throw new Error('V2_MODEL_UNSUPPORTED');
  request.mode = request.mode || 'all';
  if (!['wifi', 'internet', 'all'].includes(request.mode)) throw new Error('V2_INVALID_COMPENSATION');
  if (request.mode !== 'internet') validateWifiSnapshot(request.previousWifi);
  if (request.mode !== 'wifi' && !request.pppPath && request.internetWasAbsent !== true) throw new Error('V2_INTERNET_SNAPSHOT_REQUIRED');
  if (request.mode !== 'wifi' && request.pppPath) {
    const prefix = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.' + layout.wcd + '.WANPPPConnection.';
    if (!request.pppPath.startsWith(prefix) || !/^[1-9][0-9]*$/.test(request.pppPath.slice(prefix.length))) throw new Error('V2_OWNERSHIP_MISMATCH');
    if (declare(request.pppPath, { path: Date.now() }).path && restoreValue(request.pppPath + '.Name') !== 'GFv2-' + request.operationId) throw new Error('V2_OWNERSHIP_MISMATCH');
  }
  return request;
}
function validateWifiSnapshot(snapshot) {
  if (!snapshot) throw new Error('V2_WIFI_SNAPSHOT_REQUIRED');
  for (const band of ['24', '5']) {
    if (typeof snapshot['ssid' + band] !== 'string' || !snapshot['ssid' + band] ||
        typeof snapshot['passphrase' + band] !== 'string' ||
        (snapshot['passphrase' + band].length > 0 && snapshot['passphrase' + band].length < 8) ||
        typeof snapshot['enabled' + band] !== 'boolean') throw new Error('V2_WIFI_SNAPSHOT_INVALID');
  }
}
function ownedPppPath(layout, operationId) {
  const prefix = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.' + layout.wcd + '.WANPPPConnection.';
  const paths = [];
  for (const instance of declare(prefix + '*', { path: Date.now() })) {
    if (instance.path && restoreValue(instance.path + '.Name') === 'GFv2-' + operationId) paths.push(instance.path);
  }
  if (paths.length > 1) throw new Error('V2_OWNERSHIP_MISMATCH');
  return paths[0] || null;
}
function restoreWifi(layout, snapshot) {
  ['24', '5'].forEach(function (band, index) {
    const path = 'InternetGatewayDevice.LANDevice.1.WLANConfiguration.' + layout.bands[index];
    declare(path + '.SSID', null, { value: snapshot['ssid' + band] });
    if (snapshot['passphrase' + band]) declare(path + '.KeyPassphrase', null, { value: snapshot['passphrase' + band] });
    declare(path + '.Enable', null, { value: snapshot['enabled' + band] });
    commit();
  });
}
function ownedInternetPaths(layout, operationId) {
  const name = 'GFv2-' + operationId;
  const paths = [];
  for (const segment of ['WANPPPConnection', 'WANIPConnection']) {
    const prefix = WAN_ROOT + layout.wcd + '.' + segment + '.';
    for (const instance of declare(prefix + '*', { path: Date.now() })) {
      if (!instance.path || instance.path === layout.provisioning) continue;
      if (restoreValue(instance.path + '.Name') === name) paths.push(instance.path);
    }
  }
  return paths;
}
function removeOwnedInternet(request) {
  const layout = RESTORE_LAYOUTS[request.expectedModel];
  const targets = [];
  if (request.pppPath) targets.push(request.pppPath);
  for (const found of ownedInternetPaths(layout, request.operationId)) {
    if (!targets.includes(found)) targets.push(found);
  }
  for (const target of targets) {
    if (target === layout.provisioning) throw new Error('V2_OWNERSHIP_MISMATCH');
    if (!declare(target, { path: Date.now() }).path) continue;
    if (restoreValue(target + '.Name') !== 'GFv2-' + request.operationId) throw new Error('V2_OWNERSHIP_MISMATCH');
    declare(target, null, { path: 0 });
    commit();
  }
}
function compensate() {
  const request = validateCompensation();
  if (request.mode !== 'wifi' && !request.pppPath) request.pppPath = ownedPppPath(RESTORE_LAYOUTS[request.expectedModel], request.operationId);
  if (request.mode !== 'internet') restoreWifi(RESTORE_LAYOUTS[request.expectedModel], request.previousWifi);
  if (request.mode !== 'wifi') removeOwnedInternet(request);
  log('gf-onboarding-v2-compensate applied operation=' + request.operationId);
}
compensate();
