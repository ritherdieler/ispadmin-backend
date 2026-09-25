// args[0]: JSON {operationId, expectedSerial, expectedModel, expectedFirmware, username, password, vlan}.
// Own only Name=GFv2-{operationId}; never delete factory or management objects.
const WAN_ROOT = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.';
const WAN_LAYOUTS = {
  F6600R: { wcd: 1, vendor: 'X_ZTE-COM_', vlan: 'VLANID', service: 'ServiceList' },
  VSOLVA74: { wcd: 2, vendor: 'X_CT-COM_', vlan: 'VLANIDMark', service: 'ServiceList' },
};
function valueAt(path) {
  const result = declare(path, { value: Date.now() });
  return result.value && result.value[0];
}
function requestForDevice() {
  const request = JSON.parse(args[0]);
  if (!/^[a-zA-Z0-9-]{1,64}$/.test(request.operationId || '')) throw new Error('V2_INVALID_OPERATION');
  if (!request.expectedSerial || !request.expectedFirmware ||
      valueAt('DeviceID.SerialNumber') !== request.expectedSerial || valueAt('DeviceID.ProductClass') !== request.expectedModel ||
      valueAt('InternetGatewayDevice.DeviceInfo.SoftwareVersion') !== request.expectedFirmware) throw new Error('V2_IDENTITY_MISMATCH');
  if (!WAN_LAYOUTS[request.expectedModel]) throw new Error('V2_MODEL_UNSUPPORTED');
  if (!Number.isInteger(request.vlan) || request.vlan < 1 || request.vlan > 4094 || request.vlan === 1000) throw new Error('V2_INVALID_INTERNET_VLAN');
  if (typeof request.username !== 'string' || !request.username || typeof request.password !== 'string' || !request.password) throw new Error('V2_MISSING_PPPOE_CREDENTIALS');
  return request;
}
function checkKnownWans(layout, name) {
  const prefix = WAN_ROOT + layout.wcd + '.WANPPPConnection.';
  for (const instance of declare(WAN_ROOT + '*.WANPPPConnection.*', { path: Date.now() })) {
    if (!instance.path.startsWith(prefix) || valueAt(instance.path + '.Name') !== name) throw new Error('V2_WAN_CONFLICT');
  }
  for (const instance of declare(WAN_ROOT + '*.WANIPConnection.*', { path: Date.now() })) {
    if (instance.path !== WAN_ROOT + '1.WANIPConnection.1') throw new Error('V2_WAN_CONFLICT');
  }
}
function ensureInternetContainer(layout) {
  if (layout.wcd === 1) return;
  if (declare(WAN_ROOT + layout.wcd, { path: Date.now() }).path) return;
  const containers = declare(WAN_ROOT + '*', { path: Date.now() });
  if (containers.size !== 1 || !declare(WAN_ROOT + '1', { path: Date.now() }).path) throw new Error('V2_WAN_CONTAINER_CONFLICT');
  declare(WAN_ROOT + '*', null, { path: 2 });
  commit();
  if (!declare(WAN_ROOT + layout.wcd, { path: Date.now() }).path) throw new Error('V2_WAN_CONTAINER_UNCONFIRMED');
}
function ensureOwnedPpp(layout, name) {
  const path = WAN_ROOT + layout.wcd + '.WANPPPConnection.[Name:' + JSON.stringify(name) + ']';
  declare(path, { path: Date.now() }, { path: 1 });
  commit();
  return path;
}
function setInternetLeaves(path, layout, request) {
  const leaves = { ConnectionType: 'IP_Routed', ConnectionTrigger: 'AlwaysOn', NATEnabled: true,
    Username: request.username, Password: request.password };
  leaves[layout.vendor + layout.service] = 'INTERNET';
  leaves[layout.vendor + layout.vlan] = request.vlan;
  if (layout.wcd === 1) leaves[layout.vendor + 'VLANEnable'] = true;
  for (const key of Object.keys(leaves)) declare(path + '.' + key, null, { value: leaves[key] });
  if (layout.wcd === 2) declare(WAN_ROOT + '2.X_CT-COM_WANGponLinkConfig.VLANIDMark', null, { value: request.vlan });
  commit();
  declare(path + '.Enable', null, { value: true });
  commit();
}
function applyInternet() {
  const request = requestForDevice();
  const layout = WAN_LAYOUTS[request.expectedModel];
  const name = 'GFv2-' + request.operationId;
  checkKnownWans(layout, name);
  ensureInternetContainer(layout);
  const path = ensureOwnedPpp(layout, name);
  setInternetLeaves(path, layout, request);
  log('gf-onboarding-v2-pppoe applied operation=' + request.operationId);
}
applyInternet();
