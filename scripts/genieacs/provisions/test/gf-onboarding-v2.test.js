const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.';
function run(name, request, values = {}, logs = [], metrics = {}) {
  const writes = [];
  const script = fs.readFileSync(path.join(__dirname, '..', name + '.js'), 'utf8');
  const context = {
    args: [JSON.stringify(request)], log(message) { logs.push(String(message)); }, commit() { metrics.commitCount = (metrics.commitCount || 0) + 1; },
    declare(key, freshness, change) {
      if (change) writes.push({ path: key, change });
      if (key === 'DeviceID.ProductClass') return { value: [request.model] };
      if (key === 'DeviceID.SerialNumber') return { value: [request.serial] };
      if (key === 'InternetGatewayDevice.DeviceInfo.SoftwareVersion') return { value: [request.firmware] };
      if (key.endsWith('WANConnectionDevice.*') || key.endsWith('WANPPPConnection.*') || key.endsWith('WANIPConnection.*')) {
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
  ssid24: 'client24', ssid5: 'client5', passphrase24: 'secret12345', passphrase5: 'secret12345',
  username: 'client-42', password: 'pppoe-secret', vlan: 100,
};

test('Internet accepts a VSOL when only the last 6 serial characters match', () => {
  const oltSerial = 'VSOL0031C0B6';
  const acsSerial = '12345B4641531C0B6';
  const vsol = { ...request, model: 'VSOLVA74', expectedModel: 'VSOLVA74', serial: acsSerial, expectedSerial: oltSerial };
  const writes = run('gf-onboarding-v2-pppoe', vsol, {
    [root + '1']: true,
    [root + '1.WANIPConnection.1']: true,
  });
  assert.equal(writes.find(w => w.path.endsWith('WANPPPConnection.1.Name')).change.value, '2_INTERNET_R_VID_100');
  assert.throws(() => run('gf-onboarding-v2-pppoe', { ...vsol, expectedSerial: 'VSOL00AAAAAA' }), /IDENTITY/);
  assert.throws(() => run('gf-onboarding-v2-wifi', { ...vsol, expectedSerial: 'VSOL00AAAAAA' }), /IDENTITY/);
  run('gf-onboarding-v2-wifi', vsol);
  run('gf-onboarding-v2-compensate', { ...vsol, mode: 'internet', internetWasAbsent: true }, {
    [root + '1']: true,
    [root + '1.WANIPConnection.1']: true,
  });
});

test('WiFi writes only supported WLAN leaves and validates identity before changes', () => {
  const writes = run('gf-onboarding-v2-wifi', request);
  assert.equal(writes.length, 6);
  assert(writes.every(w => w.path.startsWith('InternetGatewayDevice.LANDevice.1.WLANConfiguration.')));
  assert.throws(() => run('gf-onboarding-v2-wifi', { ...request, expectedSerial: 'OTHER' }), /IDENTITY/);
  assert.throws(() => run('gf-onboarding-v2-wifi', { ...request, passphrase24: 'short' }), /PASSPHRASE/);
});

test('VSOL uses its WiFi band layout', () => {
  const writes = run('gf-onboarding-v2-wifi', { ...request, model: 'VSOLVA74', expectedModel: 'VSOLVA74' });
  assert.equal(writes.find(w => w.path.endsWith('.5.SSID')).change.value, 'client24');
});

test('V2804AX15T selects the VSOL WiFi band layout by product class', () => {
  const writes = run('gf-onboarding-v2-wifi', {
    ...request,
    model: 'V2804AX15T',
    expectedModel: 'V2804AX15T',
  });
  assert.equal(writes.find(w => w.path.endsWith('.5.SSID')).change.value, 'client24');
  assert.equal(writes.find(w => w.path.endsWith('.1.SSID')).change.value, 'client5');
});

test('V2804AX15T uses a CPE-compatible WAN name and keeps v2 ownership in Alias', () => {
  const writes = run('gf-onboarding-v2-pppoe', {
    ...request,
    model: 'V2804AX15T',
    expectedModel: 'V2804AX15T',
  }, {
    [root + '1']: true,
    [root + '1.WANIPConnection.1']: true,
  });
  const name = writes.find(w => w.path.endsWith('WANPPPConnection.1.Name'));
  const alias = writes.find(w => w.path.endsWith('WANPPPConnection.1.Alias'));
  assert.equal(name.change.value, '2_INTERNET_R_VID_100');
  assert.equal(alias.change.value, 'GFv2-op123');
});

test('V2804 ownership Alias stays within the CPE 32-character limit', () => {
  const writes = run('gf-onboarding-v2-pppoe', {
    ...request,
    operationId: 'ba33deae-99c9-45ed-808c-b1172ae1c11f',
    model: 'V2804AX15T',
    expectedModel: 'V2804AX15T',
  }, {
    [root + '1']: true,
    [root + '1.WANIPConnection.1']: true,
  });
  const alias = writes.find(w => w.path.endsWith('WANPPPConnection.1.Alias'));
  assert.equal(alias.change.value.length, 32);
  assert.match(alias.change.value, /^GFv2-[A-Za-z0-9]{27}$/);
});

test('PPPoE trace shows the Alias sent to the CPE without exposing credentials', () => {
  const logs = [];
  run('gf-onboarding-v2-pppoe', {
    ...request,
    model: 'V2804AX15T',
    expectedModel: 'V2804AX15T',
  }, {
    [root + '1']: true,
    [root + '1.WANIPConnection.1']: true,
  }, logs);
  assert(logs.some(message => message.includes('path=' + root + '2.WANPPPConnection.1')));
  assert(logs.some(message => message.includes('Alias=GFv2-op123')));
  assert(logs.some(message => message.includes('Password=<REDACTED>')));
  assert(logs.every(message => !message.includes('pppoe-secret')));
});

test('PPPoE keeps the provisioning WAN and creates internet PPP without cleaning unrelated WANs', () => {
  const provisioning = root + '1.WANIPConnection.1';
  const values = {
    [provisioning]: true,
    [root + '1.WANPPPConnection.1']: true,
    [root + '1.WANPPPConnection.1.Name']: '',
    [root + '1.WANPPPConnection.9']: true,
    [root + '1.WANPPPConnection.9.Name']: 'foreign',
    [root + '1.WANIPConnection.3']: true,
    [root + '3.WANPPPConnection.1']: true,
    [root + '3.WANPPPConnection.1.Name']: 'other-client',
  };
  const writes = run('gf-onboarding-v2-pppoe', request, values);
  const deleted = writes.filter(w => w.change.path === 0).map(w => w.path);
  assert.deepEqual(deleted, []);
  assert.equal(writes.some(w => w.path === provisioning), false);
  assert(writes.some(w => w.path === root + '1.WANPPPConnection.*' && w.change.path === 2));
  assert.equal(writes.find(w => w.path.endsWith('WANPPPConnection.2.Name')).change.value, 'GFv2-op123');
  assert.equal(writes.find(w => w.path.endsWith('WANPPPConnection.2.Username')).change.value, 'client-42');
  assert.equal(writes.some(w => w.path.includes('[Name:')), false);
});

test('PPPoE sets the OMCI management WAN service to TR069 before creating Internet', () => {
  const provisioning = root + '1.WANIPConnection.1';
  const writes = run('gf-onboarding-v2-pppoe', request, { [provisioning]: true });
  const service = writes.find(w => w.path === provisioning + '.X_ZTE-COM_ServiceList');
  const managementAlias = writes.find(w => w.path === provisioning + '.Alias');
  const managementName = writes.find(w => w.path === provisioning + '.Name');
  const internet = writes.find(w => w.path.endsWith('WANPPPConnection.2.Name'));
  assert.equal(service.change.value, 'TR069');
  assert.equal(managementAlias, undefined);
  assert.equal(managementName.change.value, 'GF-TR069-MGMT');
  assert.ok(writes.indexOf(service) < writes.indexOf(internet));
  const already = run('gf-onboarding-v2-pppoe', request, {
    [provisioning]: true,
    [provisioning + '.X_ZTE-COM_ServiceList']: 'TR069',
    [provisioning + '.Name']: 'GF-TR069-MGMT',
  });
  assert.equal(already.some(w => w.path.endsWith('ServiceList') && w.path.startsWith(provisioning)), false);
  assert.equal(already.some(w => w.path === provisioning + '.Name'), false);
  const vsol = run('gf-onboarding-v2-pppoe', { ...request, model: 'VSOLVA74', expectedModel: 'VSOLVA74' }, {
    [root + '1']: true,
    [provisioning]: true,
  });
  const vsolService = vsol.find(w => w.path === provisioning + '.X_CT-COM_ServiceList');
  const vsolAlias = vsol.find(w => w.path === provisioning + '.Alias');
  assert.equal(vsolService.change.value, 'TR069');
  assert.equal(vsolAlias.change.value, 'GF-TR069-MGMT');
  assert.equal(vsol.find(w => w.path === provisioning + '.Name'), undefined);
  assert.ok(vsol.indexOf(vsolAlias) < vsol.indexOf(vsol.find(w => w.path.endsWith('WANPPPConnection.1.Name'))));

  const idempotentVsol = run('gf-onboarding-v2-pppoe', { ...request, model: 'V2804AX15T', expectedModel: 'V2804AX15T' }, {
    [root + '1']: true,
    [provisioning]: true,
    [provisioning + '.X_CT-COM_ServiceList']: 'TR069',
    [provisioning + '.Alias']: 'GF-TR069-MGMT',
  });
  assert.equal(idempotentVsol.some(w => w.path === provisioning + '.Alias'), false);
});

test('final cleanup removes untagged VSOL WANs after management and Internet are tagged', () => {
  const provisioning = root + '1.WANIPConnection.1';
  const stale = root + '1.WANIPConnection.3';
  const internetName = root + '2.WANPPPConnection.1.Name';
  const requestForVsol = {
    ...request,
    model: 'V2804AX15T',
    expectedModel: 'V2804AX15T',
  };
  const provisioned = run('gf-onboarding-v2-pppoe', requestForVsol, {
    [root + '1']: true,
    [provisioning]: true,
    [stale]: true,
    [root + '2']: true,
    [root + '2.WANPPPConnection.1']: true,
  });
  assert.equal(provisioned.some(w => w.path === stale && w.change.path === 0), false);
  const metrics = {};
  const writes = run('gf-onboarding-v2-cleanup', requestForVsol, {
    [root + '1']: true,
    [provisioning]: true,
    [stale]: true,
    [root + '2']: true,
    [root + '2.WANPPPConnection.1']: true,
    [internetName]: '2_INTERNET_R_VID_100',
    [internetName.replace('.Name', '.Alias')]: 'GFv2-op123',
    [provisioning + '.Alias']: 'GF-TR069-MGMT',
  }, [], metrics);
  const cleanup = writes.find(w => w.path === stale && w.change.path === 0);
  assert.ok(cleanup);
  assert.equal(writes.some(w => w.path === provisioning && w.change.path === 0), false);
  assert.equal(writes.some(w => w.path === internetName && w.change.path === 0), false);
  assert.equal(metrics.commitCount, 1);
});

test('static Internet keeps provisioning and creates a static IP without cleaning unrelated WANs', () => {
  const provisioning = root + '1.WANIPConnection.1';
  const writes = run('gf-onboarding-v2-pppoe', {
    ...request, mode: 'static', ip: '192.168.250.22', subnetMask: '255.255.252.0', gateway: '192.168.248.1', dns: '8.8.8.8',
  }, {
    [provisioning]: true,
    [root + '1.WANPPPConnection.1']: true,
    [root + '1.WANPPPConnection.2']: true,
    [root + '1.WANIPConnection.4']: true,
  });
  const deleted = writes.filter(w => w.change.path === 0).map(w => w.path);
  assert.deepEqual(deleted, []);
  assert.equal(writes.some(w => w.path === provisioning), false);
  assert(writes.some(w => w.path === root + '1.WANIPConnection.*' && w.change.path === 2));
  assert.equal(writes.find(w => w.path.endsWith('WANIPConnection.2.AddressingType')).change.value, 'Static');
  assert.equal(writes.find(w => w.path.endsWith('WANIPConnection.2.ExternalIPAddress')).change.value, '192.168.250.22');
  assert.equal(writes.some(w => w.path.includes('Username')), false);
});

test('VSOL and V2804 keep the first WAN container and create Internet on the second without cleanup', () => {
  for (const model of ['VSOLVA74', 'V2804AX15T']) {
    const writes = run('gf-onboarding-v2-pppoe', { ...request, model, expectedModel: model }, {
      [root + '1']: true,
      [root + '1.WANIPConnection.1']: true,
      [root + '2']: true,
      [root + '2.WANPPPConnection.4']: true,
      [root + '2.WANPPPConnection.4.Name']: 'foreign',
    });
    assert.equal(writes.some(w => w.path.startsWith(root + '1.') && w.change.path === 0), false);
    assert.equal(writes.some(w => w.path === root + '2.WANPPPConnection.4' && w.change.path === 0), false);
    assert.equal(writes.find(w => w.path.endsWith('WANPPPConnection.1.Name')).change.value, '2_INTERNET_R_VID_100');
  }
});

test('compensation checks ownership before deleting and requires a known WiFi snapshot', () => {
  const pppPath = root + '1.WANPPPConnection.2';
  assert.throws(() => run('gf-onboarding-v2-compensate', { ...request, pppPath }, {
    [pppPath]: true, [pppPath + '.Name']: 'foreign',
  }), /OWNERSHIP|SNAPSHOT/);
  const writes = run('gf-onboarding-v2-compensate', {
    ...request, pppPath,
    previousWifi: { ssid24: 'old24', ssid5: 'old5', passphrase24: 'oldpass24', passphrase5: 'oldpass55', enabled24: true, enabled5: false },
  }, { [pppPath]: true, [pppPath + '.Name']: 'GFv2-op123' });
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

test('compensation removes an owned static Internet WAN and keeps provisioning', () => {
  const ipPath = root + '1.WANIPConnection.2';
  const provisioning = root + '1.WANIPConnection.1';
  const writes = run('gf-onboarding-v2-compensate', {
    ...request, mode: 'internet', internetWasAbsent: true,
  }, {
    [provisioning]: true,
    [provisioning + '.Name']: 'aprovisionamiento',
    [ipPath]: true,
    [ipPath + '.Name']: 'GFv2-op123',
  });
  assert.deepEqual(writes.filter(w => w.change.path === 0).map(w => w.path), [ipPath]);
});

test('V2804 compensation recognizes ownership through Alias', () => {
  const pppPath = root + '2.WANPPPConnection.1';
  const writes = run('gf-onboarding-v2-compensate', {
    ...request,
    model: 'V2804AX15T',
    expectedModel: 'V2804AX15T',
    mode: 'internet',
    internetWasAbsent: true,
  }, {
    [root + '1']: true,
    [root + '1.WANIPConnection.1']: true,
    [pppPath]: true,
    [pppPath + '.Name']: '2_INTERNET_R_VID_100',
    [pppPath + '.Alias']: 'GFv2-op123',
  });
  assert.deepEqual(writes.filter(w => w.change.path === 0).map(w => w.path), [pppPath]);
});
