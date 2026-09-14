const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const SCRIPT = fs.readFileSync(path.join(__dirname, "..", "gf-wifi-ssid-poc.js"), "utf8");

const F6600_WLAN24 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";
const F6600_WLAN5 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";
const VSOL_WLAN24 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";
const VSOL_WLAN5 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";
const VSOL_MGMT_PREFIX = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.";
const F6600_MGMT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1";
const F6600_PPP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2";
const VSOL_PPP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1";

const ARGS = ["lab-zte-24", "lab-zte-5", "11111111"];

function runProvision(productClass, args) {
  const ops = [];
  function declare(p, _timestamps, values) {
    if (p === "DeviceID.ProductClass") {
      return { value: [productClass], path: p };
    }
    if (values && Object.prototype.hasOwnProperty.call(values, "value")) {
      ops.push({ op: "set", path: p, value: values.value });
    }
    return { path: p };
  }
  vm.runInNewContext(
    "(function () {\n" + SCRIPT + "\n})()",
    { args: args || ARGS, declare, commit() {}, log() {}, Date },
    { filename: "gf-wifi-ssid-poc.js" },
  );
  return ops;
}

function bySetPath(ops) {
  return Object.fromEntries(ops.filter((item) => item.op === "set").map((item) => [item.path, item.value]));
}

describe("gf-wifi-ssid-poc layouts", () => {
  it("F6600R writes 2.4 on WLAN.1 and 5.8 on WLAN.5 with one passphrase", () => {
    const sets = bySetPath(runProvision("F6600R"));
    assert.equal(sets[F6600_WLAN24 + ".SSID"], "lab-zte-24");
    assert.equal(sets[F6600_WLAN24 + ".KeyPassphrase"], "11111111");
    assert.equal(sets[F6600_WLAN24 + ".Enable"], true);
    assert.equal(sets[F6600_WLAN5 + ".SSID"], "lab-zte-5");
    assert.equal(sets[F6600_WLAN5 + ".KeyPassphrase"], "11111111");
    assert.equal(sets[F6600_WLAN5 + ".Enable"], true);
    assert.equal(sets[F6600_WLAN24 + ".PreSharedKey.1.KeyPassphrase"], undefined);
    assert.equal(sets[F6600_MGMT + ".Enable"], undefined);
    assert.equal(sets[F6600_PPP + ".Username"], undefined);
    assert.equal(sets["InternetGatewayDevice.LANDevice.1.WLANConfiguration.2.SSID"], undefined);
  });

  it("V2804AX15T writes 2.4 on WLAN.5 and 5.8 on WLAN.1 with the same passphrase", () => {
    const sets = bySetPath(runProvision("V2804AX15T", ["vsol", "vsol-5", "11111111"]));
    assert.equal(sets[VSOL_WLAN24 + ".SSID"], "vsol");
    assert.equal(sets[VSOL_WLAN24 + ".KeyPassphrase"], "11111111");
    assert.equal(sets[VSOL_WLAN5 + ".SSID"], "vsol-5");
    assert.equal(sets[VSOL_WLAN5 + ".KeyPassphrase"], "11111111");
    assert.equal(sets[F6600_WLAN24 + ".SSID"], "vsol-5");
    assert.equal(sets[VSOL_PPP + ".Username"], undefined);
    for (const path of Object.keys(sets)) {
      assert.equal(path.indexOf(VSOL_MGMT_PREFIX) === 0, false, "must not touch " + path);
    }
  });

  it("VSOLVA74 uses the V2804 wifi layout", () => {
    const sets = bySetPath(runProvision("VSOLVA74", ["vsol", "vsol-5", "sharedpass"]));
    assert.equal(sets[VSOL_WLAN24 + ".SSID"], "vsol");
    assert.equal(sets[VSOL_WLAN5 + ".SSID"], "vsol-5");
    assert.equal(sets[VSOL_WLAN24 + ".KeyPassphrase"], "sharedpass");
    assert.equal(sets[VSOL_WLAN5 + ".KeyPassphrase"], "sharedpass");
  });

  it("writes nothing when the passphrase is shorter than 8", () => {
    assert.deepEqual(runProvision("F6600R", ["lab-zte-24", "lab-zte-5", "short"]), []);
  });

  it("unknown product class writes nothing", () => {
    assert.deepEqual(runProvision("HG8245H"), []);
  });
});
