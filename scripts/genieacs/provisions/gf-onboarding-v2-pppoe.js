// args[0]: JSON {operationId, expectedSerial, expectedModel, expectedFirmware, vlan, mode, username, password, ip, subnetMask, gateway, dns}.
// Block 1: tag the management WAN and create/tag the Internet WAN. Block 3 removes foreign WANs after Wi-Fi completes.
const WAN_ROOT = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.';
const F6600 = {
  wcd: 1,
  provisioning: WAN_ROOT + '1.WANIPConnection.1',
  internetPpp: WAN_ROOT + '1.WANPPPConnection.2',
  internetIp: WAN_ROOT + '1.WANIPConnection.2',
  pppParent: WAN_ROOT + '1.WANPPPConnection',
  ipParent: WAN_ROOT + '1.WANIPConnection',
  vendor: 'X_ZTE-COM_',
  vlan: 'VLANID',
  service: 'ServiceList',
  vlanEnable: true,
  managementName: 'GF-TR069-MGMT',
};
const VSOL = {
  wcd: 2,
  provisioning: WAN_ROOT + '1.WANIPConnection.1',
  preserveFirstContainer: true,
  internetPpp: WAN_ROOT + '2.WANPPPConnection.1',
  internetIp: WAN_ROOT + '2.WANIPConnection.1',
  pppParent: WAN_ROOT + '2.WANPPPConnection',
  ipParent: WAN_ROOT + '2.WANIPConnection',
  vendor: 'X_CT-COM_',
  vlan: 'VLANIDMark',
  service: 'ServiceList',
  writeAlias: true,
  managementAlias: 'GF-TR069-MGMT',
  gponVlan: WAN_ROOT + '2.X_CT-COM_WANGponLinkConfig.VLANIDMark',
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
function ownerMarker(operationId) {
  return 'GFv2-' + String(operationId).replace(/[^A-Za-z0-9]/g, '').slice(0, 27);
}
function requestForDevice() {
  const request = JSON.parse(args[0]);
  if (!/^[a-zA-Z0-9-]{1,64}$/.test(request.operationId || '')) throw new Error('V2_INVALID_OPERATION');
  if (!request.expectedSerial || !request.expectedFirmware ||
      !sameSerial(valueAt('DeviceID.SerialNumber'), request.expectedSerial) || valueAt('DeviceID.ProductClass') !== request.expectedModel ||
      valueAt('InternetGatewayDevice.DeviceInfo.SoftwareVersion') !== request.expectedFirmware) throw new Error('V2_IDENTITY_MISMATCH');
  if (!WAN_LAYOUTS[request.expectedModel]) throw new Error('V2_MODEL_UNSUPPORTED');
  if (!Number.isInteger(request.vlan) || request.vlan < 1 || request.vlan > 4094 || request.vlan === 1000) throw new Error('V2_INVALID_INTERNET_VLAN');
  request.mode = request.mode || 'pppoe';
  if (request.mode !== 'pppoe' && request.mode !== 'static') throw new Error('V2_INVALID_INTERNET_MODE');
  if (request.mode === 'static') {
    if (typeof request.ip !== 'string' || !request.ip) throw new Error('V2_MISSING_STATIC_IP');
  } else if (typeof request.username !== 'string' || !request.username || typeof request.password !== 'string' || !request.password) {
    throw new Error('V2_MISSING_PPPOE_CREDENTIALS');
  }
  return request;
}
function ensureInternetContainer(layout) {
  if (layout.wcd === 1) return;
  if (declare(WAN_ROOT + layout.wcd, { path: Date.now() }).path) return;
  const listed = declare(WAN_ROOT + '*', { path: Date.now() });
  const size = Number(listed && listed.size);
  const known = Number.isFinite(size) && size > 0 ? size : 1;
  declare(WAN_ROOT + '*', null, { path: Math.max(known, layout.wcd) });
  commit();
}
function ensureInstance(parent, instancePath) {
  if (declare(instancePath, { path: Date.now() }).path) return;
  const listed = declare(parent + '.*', { path: Date.now() });
  const size = Number(listed && listed.size);
  const known = Number.isFinite(size) && size > 0 ? size : 0;
  const slot = Number(instancePath.split('.').pop());
  declare(parent + '.*', { path: Date.now() }, { path: Math.max(known, slot) });
  commit();
}
function writeLeaves(path, leaves) {
  for (const key of Object.keys(leaves)) declare(path + '.' + key, null, { value: leaves[key] });
}
function traceLeaves(request, path, leaves) {
  const entries = Object.keys(leaves).sort().map(key => {
    const value = key === 'Username' || key === 'Password' ? '<REDACTED>' : String(leaves[key]);
    return key + '=' + value;
  });
  log('GFv2_SPV operation=' + request.operationId + ' model=' + request.expectedModel + ' path=' + path + ' leaves=' + entries.join(','));
}
function serviceLeaves(layout, request, name) {
  const leaves = { ConnectionType: 'IP_Routed', NATEnabled: true, Name: name };
  leaves[layout.vendor + layout.service] = 'INTERNET';
  leaves[layout.vendor + layout.vlan] = request.vlan;
  if (layout.vlanEnable) leaves[layout.vendor + 'VLANEnable'] = true;
  return leaves;
}
function setInternetLeaves(path, layout, request) {
  const owner = ownerMarker(request.operationId);
  const name = layout.writeAlias ? '2_INTERNET_R_VID_' + request.vlan : owner;
  const leaves = serviceLeaves(layout, request, name);
  leaves.Enable = true;
  if (layout.writeAlias) leaves.Alias = owner;
  if (request.mode === 'static') {
    leaves.AddressingType = 'Static';
    leaves.ExternalIPAddress = request.ip;
    if (request.subnetMask) leaves.SubnetMask = request.subnetMask;
    if (request.gateway) leaves.DefaultGateway = request.gateway;
    if (request.dns) {
      leaves.DNSServers = request.dns;
      leaves.DNSEnabled = true;
    }
  } else {
    leaves.ConnectionTrigger = 'AlwaysOn';
    leaves.Username = request.username;
    leaves.Password = request.password;
  }
  traceLeaves(request, path, leaves);
  writeLeaves(path, leaves);
  if (layout.gponVlan) declare(layout.gponVlan, null, { value: request.vlan });
  commit();
}
function ensureProvisioningServiceTr069(layout, request) {
  let changed = false;
  const marker = layout.managementAlias
    ? { leaf: 'Alias', value: layout.managementAlias }
    : layout.managementName
      ? { leaf: 'Name', value: layout.managementName }
      : null;
  if (marker) {
    const markerPath = layout.provisioning + '.' + marker.leaf;
    if (valueAt(markerPath) !== marker.value) {
      declare(markerPath, null, { value: marker.value });
      log('GFv2_MGMT operation=' + request.operationId + ' model=' + request.expectedModel + ' path=' + layout.provisioning + ' ' + marker.leaf + '=' + marker.value);
      changed = true;
    }
  }
  const leaf = layout.provisioning + '.' + layout.vendor + layout.service;
  if (valueAt(leaf) !== 'TR069') {
    declare(leaf, null, { value: 'TR069' });
    changed = true;
  }
  if (changed) commit();
}
function applyInternet() {
  const request = requestForDevice();
  const layout = WAN_LAYOUTS[request.expectedModel];
  const internet = request.mode === 'static' ? layout.internetIp : layout.internetPpp;
  const parent = request.mode === 'static' ? layout.ipParent : layout.pppParent;
  ensureProvisioningServiceTr069(layout, request);
  ensureInternetContainer(layout);
  ensureInstance(parent, internet);
  setInternetLeaves(internet, layout, request);
  log('gf-onboarding-v2-pppoe applied operation=' + request.operationId + ' mode=' + request.mode);
}
applyInternet();
