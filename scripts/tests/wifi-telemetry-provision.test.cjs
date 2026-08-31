const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/gigafiber-wifi-telemetry.js'), 'utf8');
const inform = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/inform.js'), 'utf8');
for (const model of ['F6600R', 'V2804AX15T', 'HG8145V5', 'unknown']) {
  const calls = [];
  vm.runInNewContext(code, {
    Date: { now: () => 1000 },
    declare: (name, timestamps, values) => { calls.push({ name, timestamps, values }); return { value: [model] }; },
  });
  const telemetry = calls.filter(call => call.name !== 'DeviceID.ProductClass');
  assert.equal(telemetry.length > 0, ['F6600R', 'V2804AX15T'].includes(model));
  for (const call of telemetry) {
    assert.equal(call.values, undefined, 'Provision must not write any parameter');
    assert.equal(call.timestamps.path, 1000);
    assert.equal(call.timestamps.value, 1000);
    assert(!/Hosts\.Host\.|SSID|Password|PreSharedKey|Reboot|FactoryReset/.test(call.name));
    assert(/HostNumberOfEntries$|TotalAssociations$|AssociatedDevice\.\*\.[\w-]+$/.test(call.name));
  }
}

for (const pathName of [
  'InternetGatewayDevice.ManagementServer.Password',
  'InternetGatewayDevice.ManagementServer.ConnectionRequestPassword',
  'Device.ManagementServer.Password',
  'Device.ManagementServer.ConnectionRequestPassword',
]) {
  assert(inform.includes(`declare("${pathName}", null, {value: acsPass});`), `${pathName} must be ACS-owned during every Inform`);
}

const presetScript = path.join(__dirname, '../genieacs/apply-wifi-telemetry.py');
const result = spawnSync('python3', [presetScript,
  '--device-id', '5872C9-F6600R-ZTEGDC47BF8F',
  '--device-id', '5872C9-F6600R-ZTEGDC47DAD1',
], { encoding: 'utf8' });
assert.equal(result.status, 0, result.stderr);
const precondition = JSON.parse(result.stdout).preset.precondition;
assert(!precondition.includes('$in'), 'Preset precondition must not use Mongo operators');
assert(!precondition.includes('_deviceId'), 'Preset precondition must use CWMP DeviceID fields');
assert(precondition.includes('DeviceID.SerialNumber = "ZTEGDC47BF8F"'));
assert(precondition.includes('DeviceID.SerialNumber = "ZTEGDC47DAD1"'));
assert(precondition.includes('DeviceID.ProductClass = "F6600R"'));
console.log('PASS: two supported models, unsupported models, read-only declarations, fresh values and allowlisted paths');

assert.doesNotMatch(inform, /PeriodicInformTime/);
assert.doesNotMatch(inform, /PeriodicInformInterval/);
assert.doesNotMatch(inform, /PeriodicInformEnable/);
