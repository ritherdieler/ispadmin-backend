const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../genieacs/provisions/gigafiber-wifi-telemetry.js'), 'utf8');
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
console.log('PASS: two supported models, unsupported models, read-only declarations, fresh values and allowlisted paths');
