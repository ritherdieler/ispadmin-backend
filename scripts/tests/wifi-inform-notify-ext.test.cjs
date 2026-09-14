const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

test("wifi-inform-notify skips when env missing", async () => {
  const source = fs.readFileSync(
    path.join(__dirname, "../genieacs/ext/wifi-inform-notify.js"),
    "utf8",
  );
  const sandbox = { module: { exports: {} }, exports: {}, require, process: { env: {} }, Buffer, console };
  vm.runInNewContext(source, sandbox, { filename: "wifi-inform-notify.js" });
  const notify = sandbox.module.exports.notify || sandbox.exports.notify;
  assert.equal(typeof notify, "function");
  await new Promise((resolve, reject) => {
    notify(["12345B4641531C0B6", "B46415-V2804AX15T-12345B4641531C0B6"], (err, result) => {
      try {
        assert.equal(err, null);
        assert.equal(result, "skipped");
        resolve();
      } catch (e) {
        reject(e);
      }
    });
  });
});

test("pilot provision calls wifi-inform-notify ext before WLAN declares and without try/catch", () => {
  const source = fs.readFileSync(
    path.join(__dirname, "../genieacs/provisions/gigafiber-wifi-telemetry.js"),
    "utf8",
  );
  assert.match(source, /ext\("wifi-inform-notify", "notify"/);
  assert.match(source, /DeviceID\.SerialNumber/);
  assert.doesNotMatch(source, /try\s*\{[\s\S]*ext\(/);
  const extAt = source.indexOf('ext("wifi-inform-notify"');
  const wlanAt = source.indexOf("WLANConfiguration");
  assert.ok(extAt >= 0 && wlanAt > extAt, "ext must run before WLAN declares");
});
