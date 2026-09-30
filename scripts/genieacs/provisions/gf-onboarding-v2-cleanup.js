// args[0]: JSON {operationId, expectedSerial, expectedModel, expectedFirmware, mode}.
// Block 3: preserve only the platform-tagged management and Internet WANs, then delete every other WAN in one commit.
const WAN_ROOT = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.';
const F6600 = {
  provisioning: WAN_ROOT + '1.WANIPConnection.1',
  internetPpp: WAN_ROOT + '1.WANPPPConnection.2',
  internetIp: WAN_ROOT + '1.WANIPConnection.2',
  managementLeaf: 'Name',
  internetLeaf: 'Name',
};
const VSOL = {
  provisioning: WAN_ROOT + '1.WANIPConnection.1',
  internetPpp: WAN_ROOT + '2.WANPPPConnection.1',
  internetIp: WAN_ROOT + '2.WANIPConnection.1',
  managementLeaf: 'Alias',
  internetLeaf: 'Alias',
};
const WAN_LAYOUTS = { F6600R: F6600, VSOLVA74: VSOL, V2804AX15T: VSOL };

function valueAt(path) {
  const result = declare(path, { value: Date.now() });
  return result.value && result.value[0];
}

function serialSuffix(serial) {
  const cleaned = String(serial || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (cleaned.length < 6) return '';
  const suffix = cleaned.slice(-6);
  return /^[0-9A-F]{6}$/.test(suffix) ? suffix : '';
}

function sameSerial(live, expected) {
  const suffix = serialSuffix(live);
  return suffix !== '' && suffix === serialSuffix(expected);
}

function requestForDevice() {
  const request = JSON.parse(args[0]);
  if (!/^[a-zA-Z0-9-]{1,64}$/.test(request.operationId || '')) throw new Error('V2_INVALID_OPERATION');
  if (!request.expectedSerial || !request.expectedFirmware ||
      !sameSerial(valueAt('DeviceID.SerialNumber'), request.expectedSerial) || valueAt('DeviceID.ProductClass') !== request.expectedModel ||
      valueAt('InternetGatewayDevice.DeviceInfo.SoftwareVersion') !== request.expectedFirmware) throw new Error('V2_IDENTITY_MISMATCH');
  if (!WAN_LAYOUTS[request.expectedModel]) throw new Error('V2_MODEL_UNSUPPORTED');
  request.mode = request.mode || 'pppoe';
  if (request.mode !== 'pppoe' && request.mode !== 'static') throw new Error('V2_INVALID_INTERNET_MODE');
  return request;
}

function ownerMarker(operationId) {
  return 'GFv2-' + String(operationId).replace(/[^A-Za-z0-9]/g, '').slice(0, 27);
}

function isManaged(path, layout, internet, request) {
  if (path === layout.provisioning) return valueAt(path + '.' + layout.managementLeaf) === 'GF-TR069-MGMT';
  if (path === internet) return valueAt(path + '.' + layout.internetLeaf) === ownerMarker(request.operationId);
  return false;
}

function cleanup() {
  const request = requestForDevice();
  const layout = WAN_LAYOUTS[request.expectedModel];
  const internet = request.mode === 'static' ? layout.internetIp : layout.internetPpp;
  const deleted = [];
  for (const segment of ['WANPPPConnection', 'WANIPConnection']) {
    for (const instance of declare(WAN_ROOT + '*.' + segment + '.*', { path: Date.now() })) {
      if (!instance.path || isManaged(instance.path, layout, internet, request)) continue;
      declare(instance.path, null, { path: 0 });
      deleted.push(instance.path);
    }
  }
  if (deleted.length > 0) commit();
  log('GFv2_CLEANUP operation=' + request.operationId + ' model=' + request.expectedModel + ' deleted=' + deleted.length);
}

cleanup();
