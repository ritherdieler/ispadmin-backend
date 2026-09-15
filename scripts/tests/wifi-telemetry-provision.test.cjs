const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/gigafiber-wifi-telemetry.js'), 'utf8');
const inform = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/inform.js'), 'utf8');
const presetScript = path.join(__dirname, '../genieacs/apply-wifi-telemetry.py');

// Instances the fake CPE exposes. Stations go past TotalAssociations on purpose:
// GenieACS caches phantom AssociatedDevice indices for departed clients and the
// provision must not ship them.
const HOST_COUNT = 3;
const TOTALS = { 1: 2, 5: 1 };
const STATION_INSTANCES = 4;

function runProvision(model, serial = '12345B4641531C0B6') {
  const order = [];
  const nowArgs = [];
  const extCalls = [];
  const declare = (name, timestamps, values) => {
    order.push({ kind: 'declare', name, timestamps, values });
    if (name === 'DeviceID.ProductClass') return { value: [model] };
    if (name === 'DeviceID.SerialNumber') return { value: [serial] };
    if (name === 'DeviceID.OUI') return { value: ['B46415'] };
    if (name.endsWith('.Hosts.HostNumberOfEntries')) return { value: [String(HOST_COUNT)] };
    const total = name.match(/WLANConfiguration\.(\d+)\.TotalAssociations$/);
    if (total) return { value: [String(TOTALS[total[1]])] };
    if (name.includes('.Host.*.')) {
      const leaf = name.split('.').pop();
      return Array.from({ length: HOST_COUNT }, (_, i) => ({
        path: name.replace('.Host.*.', `.Host.${i + 1}.`),
        value: [`host-${leaf}-${i + 1}`],
      }));
    }
    if (name.includes('.AssociatedDevice.*.')) {
      const leaf = name.split('.').pop();
      return Array.from({ length: STATION_INSTANCES }, (_, i) => ({
        path: name.replace('.AssociatedDevice.*.', `.AssociatedDevice.${i + 1}.`),
        value: [`sta-${leaf}-${i + 1}`],
      }));
    }
    return { value: [model] };
  };
  vm.runInNewContext(code, {
    JSON,
    Number,
    Date: {
      now: (period) => {
        nowArgs.push(period);
        return 1000;
      },
    },
    declare,
    ext: (...args) => {
      order.push({ kind: 'ext', args });
      extCalls.push(args);
      return 'queued';
    },
  });
  const declared = order.filter((c) => c.kind === 'declare' && !c.name.startsWith('DeviceID.'));
  const informWrites = declared.filter((c) => c.name.includes('ManagementServer.PeriodicInform'));
  const telemetry = declared.filter((c) => !c.name.includes('ManagementServer.PeriodicInform'));
  return { order, telemetry, informWrites, nowArgs, extCalls };
}

for (const model of ['F6600R', 'V2804AX15T', 'HG8145V5', 'unknown']) {
  const supported = ['F6600R', 'V2804AX15T'].includes(model);
  const { telemetry, informWrites, nowArgs } = runProvision(model);
  assert.equal(telemetry.length > 0, supported);
  assert.equal(informWrites.length, 0, `${model}: PeriodicInform belongs to gigafiber-bootstrap, not to this provision`);
  for (const call of telemetry) {
    assert.equal(call.values, undefined, 'Telemetry paths must not write any parameter');
    assert.equal(call.timestamps.path, 1000);
    assert.equal(call.timestamps.value, 1000);
    assert(!/SSID|KeyPassphrase|Password|PreSharedKey|Reboot|FactoryReset/.test(call.name), call.name);
    assert(
      /HostNumberOfEntries$|Hosts\.Host\.\*\.(MACAddress|HostName)$|TotalAssociations$|AssociatedDevice\.\*\.[\w-]+$/.test(call.name),
      `unexpected declared path: ${call.name}`,
    );
  }
  if (supported) {
    assert.ok(
      nowArgs.every((period) => period === undefined),
      `${model} must declare against the session clock, got ${JSON.stringify(nowArgs)}`,
    );
  }
}

// Wildcards only: enumerating fixed indices declares absent instances, which is
// what drives too_many_commits.
for (const model of ['F6600R', 'V2804AX15T']) {
  for (const call of runProvision(model).telemetry) {
    assert(
      !/\.(Host|AssociatedDevice)\.\d+\./.test(call.name),
      `${model} must discover instances with a wildcard, not enumerate them: ${call.name}`,
    );
  }
}

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

// The ext carries the payload, so it can only run once the declares resolved.
for (const model of ['F6600R', 'V2804AX15T']) {
  const { order, extCalls } = runProvision(model);
  assert.equal(extCalls.length, 1, `${model} must notify exactly once`);
  const extAt = order.findIndex((c) => c.kind === 'ext');
  const lastDeclareAt = order.reduce((acc, c, i) => (c.kind === 'declare' ? i : acc), -1);
  assert(extAt > lastDeclareAt, `${model}: ext must run after every declare`);

  const [name, fn, serial, deviceId, raw] = extCalls[0];
  assert.equal(name, 'wifi-inform-notify');
  assert.equal(fn, 'notify');
  assert.equal(serial, '12345B4641531C0B6');
  assert.equal(deviceId, `B46415-${model}-12345B4641531C0B6`);

  const payload = JSON.parse(raw);
  assert.equal(payload.v, 1);
  assert.equal(payload.model, model);
  assert.equal(payload.at, 1000);
  assert.equal(payload.root, 'InternetGatewayDevice.LANDevice.1');
  assert.equal(payload.serial, serial);
  assert.equal(payload.deviceId, deviceId);

  const keys = Object.keys(payload.leaves);
  assert(keys.length > 0, `${model}: payload must carry leaves`);
  for (const key of keys) {
    assert(!key.startsWith('InternetGatewayDevice.'), `leaf keys are relative to root: ${key}`);
    assert(/^(Hosts|WLANConfiguration)\./.test(key), `unexpected leaf: ${key}`);
    assert(!/SSID|KeyPassphrase|Password|PreSharedKey/.test(key), `leaked secret leaf: ${key}`);
  }
  assert.equal(payload.leaves['Hosts.HostNumberOfEntries'], String(HOST_COUNT));
  assert.equal(
    keys.filter((k) => /^Hosts\.Host\.\d+\.MACAddress$/.test(k)).length,
    HOST_COUNT,
    `${model}: one host leaf per existing instance`,
  );
  for (const [radio, total] of Object.entries(TOTALS)) {
    assert.equal(payload.leaves[`WLANConfiguration.${radio}.TotalAssociations`], String(total));
    const macs = keys.filter((k) =>
      new RegExp(`^WLANConfiguration\\.${radio}\\.AssociatedDevice\\.\\d+\\.AssociatedDeviceMACAddress$`).test(k));
    assert.equal(macs.length, total, `${model} radio ${radio}: stations must be capped at TotalAssociations`);
  }
}

// Unsupported models notify without telemetry so the ACS can still close the
// loop on last_inform.
const unsupported = runProvision('HG8145V5');
assert.equal(unsupported.extCalls.length, 1);
assert.deepEqual(JSON.parse(unsupported.extCalls[0][4]).leaves, {});

assert.equal(runProvision('V2804AX15T', '').extCalls.length, 0, 'no serial means no notify');

// The Inform provision owns the ACS credentials, on the data model root the CPE
// actually exposes: declaring the absent root burns commit iterations.
function runInform(roots) {
  const calls = [];
  vm.runInNewContext(inform, {
    Date: { now: () => 1000 },
    declare: (name, timestamps, values) => {
      calls.push({ name, values });
      return { size: roots.some((r) => name.startsWith(`${r}.`)) ? 1 : 0, value: [undefined] };
    },
  });
  return calls;
}
for (const roots of [['InternetGatewayDevice'], ['Device']]) {
  const calls = runInform(roots);
  const writes = calls.filter((c) => c.values);
  assert.equal(writes.length, 5, `${roots[0]}: five credential writes`);
  for (const call of writes) {
    assert(call.name.startsWith(`${roots[0]}.ManagementServer.`), `wrote to absent root: ${call.name}`);
  }
  for (const leaf of ['Username', 'URL', 'ConnectionRequestUsername', 'Password', 'ConnectionRequestPassword']) {
    assert(
      writes.some((c) => c.name === `${roots[0]}.ManagementServer.${leaf}`),
      `${leaf} must be ACS-owned during every Inform`,
    );
  }
}
assert.equal(runInform(['InternetGatewayDevice', 'Device']).filter((c) => c.values).length, 10);

const result = spawnSync('python3', [presetScript,
  '--device-id', '5872C9-F6600R-ZTEGDC47BF8F',
  '--device-id', '5872C9-F6600R-ZTEGDC47DAD1',
], { encoding: 'utf8' });
assert.equal(result.status, 0, result.stderr);
const dual = JSON.parse(result.stdout).preset;
assert.equal(dual.channel, 'gigafiber-wifi-telemetry', 'Wi-Fi preset must not share the inform.js fault channel');
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
assert.equal(vsolPreset.channel, 'gigafiber-wifi-telemetry');
assert.deepEqual(vsolPreset.events, {});
assert(vsolPreset.precondition.includes('DeviceID.SerialNumber = "12345B4641531C0B6"'));
assert(vsolPreset.precondition.includes('DeviceID.ProductClass = "V2804AX15T"'));
assert(!vsolPreset.precondition.includes('ZTEG'));

const fleet = spawnSync('python3', [presetScript, '--all-models'], { encoding: 'utf8' });
assert.equal(fleet.status, 0, fleet.stderr);
const fleetPreset = JSON.parse(fleet.stdout).preset;
assert.equal(fleetPreset.channel, 'gigafiber-wifi-telemetry');
assert.equal(
  fleetPreset.precondition,
  'DeviceID.ProductClass = "F6600R" OR DeviceID.ProductClass = "V2804AX15T"',
  'the fleet preset self-limits to the models WifiNbiTelemetry.radios() understands',
);
assert(!fleetPreset.precondition.includes('SerialNumber'));
assert.notEqual(
  spawnSync('python3', [presetScript, '--all-models', '--device-id', '5872C9-F6600R-ZTEGDC47BF8F'], { encoding: 'utf8' }).status,
  0,
  'fleet mode and an explicit list are mutually exclusive',
);
assert.notEqual(spawnSync('python3', [presetScript], { encoding: 'utf8' }).status, 0, 'no target means no preset');

assert.doesNotMatch(inform, /PeriodicInformTime/);
assert.doesNotMatch(inform, /PeriodicInformInterval/);
assert.doesNotMatch(inform, /PeriodicInformEnable/);

// The Inform interval is the only cadence knob, and it must not align the fleet.
const bootstrap = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/gigafiber-bootstrap.js'), 'utf8');
function runBootstrap(serial, roots = ['InternetGatewayDevice']) {
  const calls = [];
  vm.runInNewContext(bootstrap, {
    Date: { now: () => 1000 },
    clear: () => {},
    declare: (name, timestamps, values) => {
      calls.push({ name, values });
      if (name === 'DeviceID.SerialNumber') return { size: 1, value: [serial] };
      return { size: roots.some((r) => name.startsWith(`${r}.`)) ? 1 : 0, value: [undefined] };
    },
    log: () => {},
  });
  return calls;
}
function interval(serial, roots) {
  const call = runBootstrap(serial, roots).find((c) => c.name.endsWith('PeriodicInformInterval'));
  return call && call.values.value;
}
const intervals = ['12345B4641531C0B6', 'ZTEGDC47BF8F', 'ZTEGDC47DAD1', '54504C474A3C6828'].map((s) => interval(s));
for (const value of intervals) {
  assert.ok(value >= 1800 && value < 2100, `interval must be 1800 plus jitter under 300 s, got ${value}`);
}
assert(new Set(intervals).size > 1, 'jitter must differ per serial so the fleet does not align');
assert.equal(interval('ZTEGDC47BF8F'), interval('ZTEGDC47BF8F'), 'jitter must be stable for a given serial');
for (const roots of [['InternetGatewayDevice'], ['Device']]) {
  const writes = runBootstrap('ZTEGDC47BF8F', roots).filter((c) => c.values && c.name.includes('ManagementServer'));
  assert.equal(writes.length, 7, `${roots[0]}: credentials plus PeriodicInform on one root only`);
  for (const call of writes) {
    assert(call.name.startsWith(`${roots[0]}.`), `bootstrap wrote to absent root: ${call.name}`);
  }
}

console.log('PASS: payload-carrying ext after declares, wildcard discovery, station cap, per-root Inform credentials');
