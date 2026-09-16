const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const SCRIPT = fs.readFileSync(
  path.join(__dirname, "..", "gf-static-wan2-poc.js"),
  "utf8",
);

const F6600_PPP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2";
const F6600_IP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2";
const F6600_MGMT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1";
const F6600_IP_PARENT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.*";
const VSOL_PPP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1";
const VSOL_IP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1";
const VSOL_MGMT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1";
const VSOL_IP_PARENT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.*";
const VSOL_WCD_PARENT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.*";
const VSOL_GPON_VLAN =
  "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark";
const VSOL_MGMT_PREFIX = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.";

const ARGS = ["192.168.250.10", "255.255.255.0", "192.168.250.1", "8.8.8.8,8.8.4.4", 100, "2_INTERNET_R_VID_100"];

function instancePrefix(wildcardPath) {
  return String(wildcardPath).slice(0, -1);
}

const WIFI = ["lab-zte-24", "labwifi24", "lab-zte-5", "labwifi5g"];
const F6600_WLAN24 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";
const F6600_WLAN5 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";
const VSOL_WLAN24 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";
const VSOL_WLAN5 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";

function runProvision(productClass, existingInstances, extraArgs) {
  const existing = new Set(existingInstances);
  const ops = [];

  function declare(p, _timestamps, values) {
    if (p === "DeviceID.ProductClass") {
      return { value: [productClass], path: p };
    }
    if (values && values.path === 0) {
      ops.push({ op: "delete", path: p });
      existing.delete(p);
      return {};
    }
    if (values && Object.prototype.hasOwnProperty.call(values, "path") && String(p).endsWith(".*")) {
      ops.push({ op: "add", path: p, count: values.path });
      return { path: p, size: values.path };
    }
    if (values && Object.prototype.hasOwnProperty.call(values, "value")) {
      ops.push({ op: "set", path: p, value: values.value });
    }
    if (String(p).endsWith(".*")) {
      const prefix = instancePrefix(p);
      const ids = new Set();
      for (const inst of existing) {
        if (inst.indexOf(prefix) === 0) {
          const n = inst.slice(prefix.length).split(".")[0];
          if (n) ids.add(n);
        }
      }
      return { path: ids.size ? p : undefined, size: ids.size };
    }
    const prefix = p.endsWith(".") ? p : p + ".";
    const found = existing.has(p) || [...existing].some((inst) => inst.indexOf(prefix) === 0);
    return { path: found ? p : undefined, size: undefined };
  }

  vm.runInNewContext(
    "(function () {\n" + SCRIPT + "\n})()",
    {
      args: extraArgs ? ARGS.concat(extraArgs) : ARGS,
      declare,
      commit() {},
      log() {},
      Date,
    },
    { filename: "gf-static-wan2-poc.js" },
  );
  return ops;
}

function pathsOf(ops, op) {
  return ops.filter((item) => item.op === op).map((item) => item.path);
}

function bySetPath(ops) {
  return Object.fromEntries(ops.filter((item) => item.op === "set").map((item) => [item.path, item.value]));
}

function assertNeverTouches(ops, prefix) {
  for (const item of ops) {
    assert.equal(
      String(item.path).indexOf(prefix) === 0,
      false,
      "must not touch " + prefix + " via " + item.op + " " + item.path,
    );
  }
}

describe("gf-static-wan2-poc layouts", () => {
  it("F6600R deletes leftover PPP.2, adds WANIP.2, never writes management WANIP.1", () => {
    const ops = runProvision("F6600R", [
      F6600_MGMT,
      "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1",
      F6600_PPP,
    ]);
    assert.deepEqual(pathsOf(ops, "delete"), [F6600_PPP]);
    const adds = ops.filter((item) => item.op === "add");
    assert.equal(adds.length, 1);
    assert.equal(adds[0].path, F6600_IP_PARENT);
    assert.equal(adds[0].count, 2);
    const sets = bySetPath(ops);
    assert.equal(sets[F6600_IP + ".ExternalIPAddress"], "192.168.250.10");
    assert.equal(sets[F6600_IP + ".AddressingType"], "Static");
    assert.equal(sets[F6600_IP + ".SubnetMask"], "255.255.255.0");
    assert.equal(sets[F6600_IP + ".DefaultGateway"], "192.168.250.1");
    assert.equal(sets[F6600_IP + ".Enable"], true);
    assert.equal(sets[F6600_IP + ".X_ZTE-COM_VLANID"], 100);
    assert.equal(sets[F6600_IP + ".X_CT-COM_VLANIDMark"], undefined);
    assert.equal(sets[F6600_MGMT + ".Enable"], undefined);
  });

  it("V2804AX15T deletes leftover internet PPP on WCD.2 and never touches WCD.1", () => {
    const ops = runProvision("V2804AX15T", [VSOL_MGMT, VSOL_PPP]);
    assert.deepEqual(pathsOf(ops, "delete"), [VSOL_PPP]);
    const adds = ops.filter((item) => item.op === "add");
    assert.equal(adds.length, 1);
    assert.equal(adds[0].path, VSOL_IP_PARENT);
    assert.equal(adds[0].count, 1);
    const sets = bySetPath(ops);
    assert.equal(sets[VSOL_IP + ".ExternalIPAddress"], "192.168.250.10");
    assert.equal(sets[VSOL_IP + ".AddressingType"], "Static");
    assert.equal(sets[VSOL_IP + ".Name"], "2_INTERNET_R_VID_100");
    assert.equal(sets[VSOL_IP + ".Alias"], "2_INTERNET_R_VID_100");
    assert.equal(sets[VSOL_IP + ".X_CT-COM_ServiceList"], "INTERNET");
    assert.equal(sets[VSOL_IP + ".X_CT-COM_VLANIDMark"], 100);
    assert.equal(sets[VSOL_GPON_VLAN], 100);
    assert.equal(sets[VSOL_IP + ".Enable"], true);
    assert.equal(sets[VSOL_IP + ".X_ZTE-COM_VLANEnable"], undefined);
    assertNeverTouches(ops, VSOL_MGMT_PREFIX);
  });

  it("V2804AX15T creates WCD.2 when only management WAN exists", () => {
    const ops = runProvision("V2804AX15T", [VSOL_MGMT]);
    const adds = ops.filter((item) => item.op === "add");
    assert.equal(adds.length, 2);
    assert.equal(adds[0].path, VSOL_WCD_PARENT);
    assert.equal(adds[0].count, 2);
    assert.equal(adds[1].path, VSOL_IP_PARENT);
    assert.equal(adds[1].count, 1);
    assert.deepEqual(pathsOf(ops, "delete"), []);
    assertNeverTouches(ops, VSOL_MGMT_PREFIX);
  });

  it("V2804AX15T reuses existing WANIP.1 and does not add a third WAN", () => {
    const ops = runProvision("V2804AX15T", [VSOL_MGMT, VSOL_IP]);
    assert.deepEqual(pathsOf(ops, "delete"), []);
    assert.deepEqual(pathsOf(ops, "add"), []);
    const sets = bySetPath(ops);
    assert.equal(sets[VSOL_IP + ".ExternalIPAddress"], "192.168.250.10");
    assert.equal(sets[VSOL_IP + ".Enable"], true);
    assertNeverTouches(ops, VSOL_MGMT_PREFIX);
  });

  it("VSOLVA74 uses the V2804 layout", () => {
    const ops = runProvision("VSOLVA74", [VSOL_MGMT, VSOL_PPP]);
    assert.deepEqual(pathsOf(ops, "delete"), [VSOL_PPP]);
    assert.equal(ops.filter((item) => item.op === "add")[0].path, VSOL_IP_PARENT);
    assertNeverTouches(ops, VSOL_MGMT_PREFIX);
  });

  it("F6600R writes 2.4 on WLAN.1 and 5.8 on WLAN.5", () => {
    const ops = runProvision("F6600R", [F6600_MGMT, F6600_IP], WIFI);
    const sets = bySetPath(ops);
    assert.equal(sets[F6600_WLAN24 + ".SSID"], "lab-zte-24");
    assert.equal(sets[F6600_WLAN24 + ".KeyPassphrase"], "labwifi24");
    assert.equal(sets[F6600_WLAN5 + ".SSID"], "lab-zte-5");
    assert.equal(sets[F6600_WLAN5 + ".KeyPassphrase"], "labwifi24");
    assert.equal(sets[F6600_MGMT + ".Enable"], undefined);
  });

  it("V2804AX15T writes 2.4 on WLAN.5 and 5.8 on WLAN.1", () => {
    const ops = runProvision("V2804AX15T", [VSOL_MGMT, VSOL_IP], WIFI);
    const sets = bySetPath(ops);
    assert.equal(sets[VSOL_WLAN24 + ".SSID"], "lab-zte-24");
    assert.equal(sets[VSOL_WLAN24 + ".KeyPassphrase"], "labwifi24");
    assert.equal(sets[VSOL_WLAN5 + ".SSID"], "lab-zte-5");
    assert.equal(sets[VSOL_WLAN5 + ".KeyPassphrase"], "labwifi24");
    assertNeverTouches(ops, VSOL_MGMT_PREFIX);
  });

  it("F6600R deletes leftover WANIP.3 so only mgmt plus static WAN2 remain", () => {
    const leftoverIp3 = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.3";
    const ops = runProvision("F6600R", [F6600_MGMT, F6600_IP, leftoverIp3, F6600_PPP]);
    assert.deepEqual(pathsOf(ops, "delete"), [F6600_PPP, leftoverIp3]);
    assert.deepEqual(pathsOf(ops, "add"), []);
    assert.equal(bySetPath(ops)[F6600_IP + ".ExternalIPAddress"], "192.168.250.10");
    assert.equal(bySetPath(ops)[F6600_MGMT + ".Enable"], undefined);
  });

  it("unknown product class writes nothing", () => {
    const ops = runProvision("HG8245H", [F6600_IP, VSOL_PPP]);
    assert.deepEqual(ops, []);
  });
});
