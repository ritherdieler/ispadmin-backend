const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.';
function run(name, request, values = {}) {
  const writes = [];
  const script = fs.readFileSync(path.join(__dirname, '..', name + '.js'), 'utf8');
  const context = {
    args: [JSON.stringify(request)], log() {}, commit() {},
    declare(key, freshness, change) {
      if (change) writes.push({ path: key, change });
      if (key === 'DeviceID.ProductClass') return { value: [request.model] };
      if (key === 'DeviceID.SerialNumber') return { value: [request.serial] };
      if (key === 'InternetGatewayDevice.DeviceInfo.SoftwareVersion') return { value: [request.firmware] };
      if (key.endsWith('WANPPPConnection.*') || key.endsWith('WANIPConnection.*')) {
        const pattern = new RegExp('^' + key.split('*').map(part => part.replaceAll('.', '\\.')).join('\\d+') + '$');
        const paths = Object.keys(values).filter(p => pattern.test(p));
        return { size: paths.length, [Symbol.iterator]: function* () { for (const p of paths) yield { path: p }; } };
      }
      if (Object.hasOwn(values, key)) return { path: key, value: [values[key]], size: 1 };
      return { size: 0 };
    }
  };
  vm.runInNewContext(script, context, { timeout: 1000 });
  return writes;
}
const request = {
  operationId: 'op-123', model: 'F6600R', serial: 'ZTEGDC47BFFD', firmware: 'test-fw',
  expectedModel: 'F6600R', expectedSerial: 'ZTEGDC47BFFD', expectedFirmware: 'test-fw',
  ssid24: 'client24', ssid5: 'client5', passphrase: 'secret12345',
  username: 'client-42', password: 'pppoe-secret', vlan: 100,
};

test('WiFi writes only supported WLAN leaves and validates identity before changes', () => {
  const writes = run('gf-onboarding-v2-wifi', request);
  assert.equal(writes.length, 6);
  assert(writes.every(w => w.path.startsWith('InternetGatewayDevice.LANDevice.1.WLANConfiguration.')));
  assert.throws(() => run('gf-onboarding-v2-wifi', { ...request, expectedSerial: 'OTHER' }), /IDENTITY/);
  assert.throws(() => run('gf-onboarding-v2-wifi', { ...request, passphrase: 'short' }), /PASSPHRASE/);
});

test('VSOL uses its WiFi band layout', () => {
  const writes = run('gf-onboarding-v2-wifi', { ...request, model: 'VSOLVA74', expectedModel: 'VSOLVA74' });
  assert.equal(writes.find(w => w.path.endsWith('.5.SSID')).change.value, 'client24');
});

test('PPPoE refuses unknown WAN objects instead of deleting them', () => {
  const values = { [root + '1.WANPPPConnection.9']: true, [root + '1.WANPPPConnection.9.Name']: 'foreign' };
  assert.throws(() => run('gf-onboarding-v2-pppoe', request, values), /WAN_CONFLICT/);
});

test('PPPoE never deletes management and uses an operation-specific connection name', () => {
  const values = { [root + '1.WANIPConnection.1']: true };
  const writes = run('gf-onboarding-v2-pppoe', request, values);
  assert(writes.length > 0);
  assert(writes.every(w => !w.path.includes('WANIPConnection') && w.change.path !== 0));
  assert(writes.some(w => w.path.includes('Name:') && w.path.includes('op-123')));
});

test('compensation checks ownership before deleting and requires a known WiFi snapshot', () => {
  const pppPath = root + '1.WANPPPConnection.2';
  assert.throws(() => run('gf-onboarding-v2-compensate', { ...request, pppPath }, {
    [pppPath]: true, [pppPath + '.Name']: 'foreign',
  }), /OWNERSHIP|SNAPSHOT/);
  const writes = run('gf-onboarding-v2-compensate', {
    ...request, pppPath,
    previousWifi: { ssid24: 'old24', ssid5: 'old5', passphrase24: 'oldpass24', passphrase5: 'oldpass55', enabled24: true, enabled5: false },
  }, { [pppPath]: true, [pppPath + '.Name']: 'GFv2-op-123' });
  assert.equal(writes.filter(w => w.change.path === 0).length, 1);
  assert.equal(writes.find(w => w.change.path === 0).path, pppPath);
  assert(writes.some(w => w.change.value === 'old24'));
});

test('compensation cannot silently skip unknown Internet effects', () => {
  assert.throws(() => run('gf-onboarding-v2-compensate', { ...request, mode: 'internet' }), /SNAPSHOT/);
});

test('firmware changes and management paths are rejected before compensation', () => {
  assert.throws(() => run('gf-onboarding-v2-compensate', {
    ...request, mode: 'internet', pppPath: root + '1.WANIPConnection.1',
  }), /OWNERSHIP/);
  assert.throws(() => run('gf-onboarding-v2-wifi', { ...request, firmware: 'different-fw' }), /IDENTITY/);
});

test('PPPoE rejects an extra WAN under a third container', () => {
  assert.throws(() => run('gf-onboarding-v2-pppoe', request, {
    [root + '3.WANPPPConnection.1']: true,
    [root + '3.WANPPPConnection.1.Name']: 'other-client',
  }), /WAN_CONFLICT/);
});
