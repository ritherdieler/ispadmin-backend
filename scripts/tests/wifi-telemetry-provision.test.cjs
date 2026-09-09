const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/gigafiber-wifi-telemetry.js'), 'utf8');
const inform = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/inform.js'), 'utf8');
const presetScript = path.join(__dirname, '../genieacs/apply-wifi-telemetry.py');

function runProvision(model, serial = '12345B4641531C0B6') {
  const calls = [];
  const nowArgs = [];
  vm.runInNewContext(code, {
    Date: {
      now: (period) => {
        nowArgs.push(period);
        return 1000;
      },
    },
    declare: (name, timestamps, values) => {
      calls.push({ name, timestamps, values });
      if (name === 'DeviceID.ProductClass') return { value: [model] };
      if (name === 'DeviceID.SerialNumber') return { value: [serial] };
      if (name === 'DeviceID.OUI') return { value: ['B46415'] };
      return { value: [model] };
    },
    ext: () => 'queued',
  });
  const declared = calls.filter((call) => !call.name.startsWith('DeviceID.'));
  const informWrites = declared.filter((call) => call.name.includes('ManagementServer.PeriodicInform'));
  const telemetry = declared.filter((call) => !call.name.includes('ManagementServer.PeriodicInform'));
  return { calls: declared, telemetry, informWrites, nowArgs };
}

for (const model of ['F6600R', 'V2804AX15T', 'HG8145V5', 'unknown']) {
  const { telemetry, informWrites, nowArgs } = runProvision(model);
  assert.equal(telemetry.length > 0, ['F6600R', 'V2804AX15T'].includes(model));
  assert.equal(informWrites.length, 2, `${model} lab VSOL serial must set PeriodicInform Enable+Interval`);
  assert.deepEqual(
    informWrites.map((c) => [c.name.split('.').pop(), c.values.value]),
    [['PeriodicInformEnable', true], ['PeriodicInformInterval', 180]],
  );
  for (const call of telemetry) {
    assert.equal(call.values, undefined, 'Telemetry paths must not write any parameter');
    assert.equal(call.timestamps.path, 1000);
    assert.equal(call.timestamps.value, 1000);
    assert(!/Hosts\.Host\.|SSID|Password|PreSharedKey|Reboot|FactoryReset/.test(call.name));
    assert(/HostNumberOfEntries$|TotalAssociations$|AssociatedDevice\.\*\.[\w-]+$/.test(call.name));
  }
  if (telemetry.length) {
    assert.ok(
      nowArgs.every((period) => period === undefined || period <= 300000),
      `${model} freshness must use Date.now() or period <=300s for Inform lag, got ${JSON.stringify(nowArgs)}`,
    );
  }
}

assert.equal(runProvision('V2804AX15T', 'OTHERSERIAL').informWrites.length, 0);

const f6600 = runProvision('F6600R').telemetry.map((c) => c.name);
assert(f6600.some((n) => n.endsWith('WLANConfiguration.1.TotalAssociations')));
assert(f6600.some((n) => n.endsWith('WLANConfiguration.5.TotalAssociations')));

const vsol = runProvision('V2804AX15T').telemetry.map((c) => c.name);
assert(
  vsol.some((n) => n.endsWith('WLANConfiguration.1.TotalAssociations')),
  'V2804AX15T must declare WLAN 1 (5 GHz)',
);
assert(
  vsol.some((n) => n.endsWith('WLANConfiguration.5.TotalAssociations')),
  'V2804AX15T must declare WLAN 5 (2.4 GHz), not only [1]',
);

for (const pathName of [
  'InternetGatewayDevice.ManagementServer.Password',
  'InternetGatewayDevice.ManagementServer.ConnectionRequestPassword',
  'Device.ManagementServer.Password',
  'Device.ManagementServer.ConnectionRequestPassword',
]) {
  assert(inform.includes(`declare("${pathName}", null, {value: acsPass});`), `${pathName} must be ACS-owned during every Inform`);
}

const result = spawnSync('python3', [presetScript,
  '--device-id', '5872C9-F6600R-ZTEGDC47BF8F',
  '--device-id', '5872C9-F6600R-ZTEGDC47DAD1',
], { encoding: 'utf8' });
assert.equal(result.status, 0, result.stderr);
const dual = JSON.parse(result.stdout).preset;
assert.equal(dual.channel, 'inform', 'Pilot preset must run on Inform channel');
assert.deepEqual(dual.events, {}, 'Inform-channel pilot must not filter to 2 PERIODIC only');
const precondition = dual.precondition;
assert(!precondition.includes('$in'), 'Preset precondition must not use Mongo operators');
assert(!precondition.includes('_deviceId'), 'Preset precondition must use CWMP DeviceID fields');
assert(precondition.includes('DeviceID.SerialNumber = "ZTEGDC47BF8F"'));
assert(precondition.includes('DeviceID.SerialNumber = "ZTEGDC47DAD1"'));
assert(precondition.includes('DeviceID.ProductClass = "F6600R"'));

const vsolPilot = spawnSync('python3', [presetScript,
  '--device-id', 'B46415-V2804AX15T-12345B4641531C0B6',
], { encoding: 'utf8' });
assert.equal(vsolPilot.status, 0, vsolPilot.stderr);
const vsolPreset = JSON.parse(vsolPilot.stdout).preset;
assert.equal(vsolPreset.channel, 'inform');
assert.deepEqual(vsolPreset.events, {});
assert(vsolPreset.precondition.includes('DeviceID.SerialNumber = "12345B4641531C0B6"'));
assert(vsolPreset.precondition.includes('DeviceID.ProductClass = "V2804AX15T"'));
assert(!vsolPreset.precondition.includes('ZTEG'));

console.log('PASS: VSOL radios 1+5, Inform channel, freshness <=300s, lab interval 180, allowlisted paths');

assert.doesNotMatch(inform, /PeriodicInformTime/);
assert.doesNotMatch(inform, /PeriodicInformInterval/);
assert.doesNotMatch(inform, /PeriodicInformEnable/);
