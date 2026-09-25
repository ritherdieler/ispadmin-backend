const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const SCRIPT = fs.readFileSync(
  path.join(__dirname, "..", "gf-tr069-bind-mgmt.js"),
  "utf8",
);

const WCD_INET = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.5";
const WCD_MGMT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.6";
const INET = WCD_INET + ".WANIPConnection.1";
const MGMT = WCD_MGMT + ".WANIPConnection.1";
const CR_URL = "InternetGatewayDevice.ManagementServer.ConnectionRequestURL";
const FWD = "InternetGatewayDevice.Layer3Forwarding.Forwarding";

function runProvision(productClass, values, extraArgs) {
  const ops = [];
  const declared = [];
  const store = Object.assign({}, values);

  function readStored(p) {
    if (Object.prototype.hasOwnProperty.call(store, p)) return store[p];
    return undefined;
  }

  function declare(p, timestamps, nextValues) {
    declared.push(p);
    if (p === "DeviceID.ProductClass") {
      return { value: [productClass], path: p };
    }
    if (nextValues && Object.prototype.hasOwnProperty.call(nextValues, "path")) {
      ops.push({ op: "add", path: p, value: nextValues.path });
      const prefix = String(p).replace(/\*$/, "");
      const idx = Number(nextValues.path);
      store[prefix + idx + ".DestIPAddress"] = store[prefix + idx + ".DestIPAddress"] || "";
      return { path: prefix + idx, size: idx };
    }
    if (nextValues && Object.prototype.hasOwnProperty.call(nextValues, "value")) {
      ops.push({ op: "set", path: p, value: nextValues.value });
      store[p] = nextValues.value;
      return { value: [nextValues.value], path: p };
    }
    if (String(p).indexOf("*") !== -1) {
      const prefix = String(p).replace(/\*$/, "");
      const inst = new Map();
      Object.keys(store).forEach((key) => {
        if (key.indexOf(prefix) !== 0) return;
        const n = key.slice(prefix.length).split(".")[0];
        if (!/^\d+$/.test(n)) return;
        const instPath = prefix + n;
        if (!inst.has(instPath)) inst.set(instPath, { path: instPath, value: [store[key]] });
      });
      const matches = Array.from(inst.values()).sort((a, b) => a.path.localeCompare(b.path));
      return {
        path: matches.length ? matches[0].path : p,
        value: matches.length ? matches[0].value : undefined,
        size: matches.length,
        [Symbol.iterator]() {
          return matches[Symbol.iterator]();
        },
      };
    }
    const current = readStored(p);
    if (current === undefined) {
      return { path: undefined, value: undefined };
    }
    return { path: p, value: [current] };
  }

  vm.runInNewContext(
    "(function () {\n" + SCRIPT + "\n})()",
    {
      args: extraArgs || ["203.0.113.10"],
      declare,
      commit() {
        ops.push({ op: "commit" });
      },
      log() {},
      Date,
    },
    { filename: "gf-tr069-bind-mgmt.js" },
  );
  ops.declared = declared;
  return ops;
}

function setsOf(ops) {
  return Object.fromEntries(ops.filter((item) => item.op === "set").map((item) => [item.path, item.value]));
}

function xc220Zone2() {
  const values = {};
  values[CR_URL] = "http://192.168.30.133:7547/tr069";
  values[INET + ".ExternalIPAddress"] = "192.168.30.133";
  values[INET + ".DefaultGateway"] = "192.168.30.1";
  values[WCD_INET + ".WANEthernetLinkConfig.X_TP_VID"] = 100;
  values[WCD_INET + ".X_TP_WANPonLinkConfig.X_TP_VID"] = 100;
  values[WCD_INET + ".X_TP_ServiceType"] = "Internet";
  values[WCD_INET + ".X_TP_IfName"] = "nas1_100";
  values[MGMT + ".ExternalIPAddress"] = "10.20.0.80";
  values[MGMT + ".DefaultGateway"] = "10.20.0.1";
  values[WCD_MGMT + ".WANEthernetLinkConfig.X_TP_VID"] = 1000;
  values[WCD_MGMT + ".X_TP_WANPonLinkConfig.X_TP_VID"] = 1000;
  values[WCD_MGMT + ".X_TP_ServiceType"] = "Internet";
  values[WCD_MGMT + ".X_TP_IfName"] = "nas2_1000";
  return values;
}

describe("gf-tr069-bind-mgmt", () => {
  it("XC220-G3v sets TR069 service on the 10.20 WAN and does not touch internet VLAN", () => {
    const sets = setsOf(runProvision("XC220-G3v", xc220Zone2()));
    assert.equal(sets[WCD_MGMT + ".X_TP_ServiceType"], "TR069");
    assert.equal(sets[WCD_INET + ".X_TP_ServiceType"], undefined);
    assert.equal(sets[WCD_INET + ".WANEthernetLinkConfig.X_TP_VID"], undefined);
    assert.equal(sets[WCD_INET + ".X_TP_WANPonLinkConfig.X_TP_VID"], undefined);
  });

  it("IGD writes ethernet VLAN 1000 on the 10.20 WAN only", () => {
    const values = xc220Zone2();
    values[WCD_MGMT + ".WANEthernetLinkConfig.X_TP_VID"] = 100;
    delete values[WCD_MGMT + ".X_TP_WANPonLinkConfig.X_TP_VID"];
    delete values[WCD_MGMT + ".X_TP_ServiceType"];
    const sets = setsOf(runProvision("IGD", values));
    assert.equal(sets[WCD_MGMT + ".WANEthernetLinkConfig.X_TP_VID"], 1000);
    assert.equal(sets[WCD_MGMT + ".X_TP_ServiceType"], undefined);
    assert.equal(sets[WCD_INET + ".WANEthernetLinkConfig.X_TP_VID"], undefined);
  });

  it("adds a host route for the ACS IP via the 10.20 WAN path when no slots exist", () => {
    const sets = setsOf(runProvision("XC220-G3v", xc220Zone2(), ["203.0.113.10"]));
    assert.equal(sets[FWD + ".1.DestIPAddress"], "203.0.113.10");
    assert.equal(sets[FWD + ".1.DestSubnetMask"], "255.255.255.255");
    assert.equal(sets[FWD + ".1.GatewayIPAddress"], "10.20.0.1");
    assert.equal(sets[FWD + ".1.Interface"], MGMT);
    assert.equal(sets[FWD + ".1.Type"], "Network");
    assert.equal(sets[FWD + ".1.Enable"], true);
  });

  it("writes the ACS host route on an empty mgmt slot and does not use the internet forwarding entry", () => {
    const values = xc220Zone2();
    values[FWD + ".1.DestIPAddress"] = "";
    values[FWD + ".1.GatewayIPAddress"] = "192.168.30.1";
    values[FWD + ".1.Interface"] = INET + ".";
    values[FWD + ".1.Type"] = "Network";
    values[FWD + ".2.DestIPAddress"] = "";
    values[FWD + ".2.GatewayIPAddress"] = "10.20.0.1";
    values[FWD + ".2.Interface"] = MGMT + ".";
    values[FWD + ".2.Type"] = "Network";
    const sets = setsOf(runProvision("XC220-G3v", values, ["203.0.113.10"]));
    assert.equal(sets[FWD + ".1.DestIPAddress"], undefined);
    assert.equal(sets[FWD + ".2.DestIPAddress"], "203.0.113.10");
    assert.equal(sets[FWD + ".2.DestSubnetMask"], "255.255.255.255");
    assert.equal(sets[FWD + ".2.Interface"], MGMT);
    assert.equal(sets[FWD + ".2.Enable"], true);
  });

  it("does not rewrite internet or add a route when CR is already 10.20", () => {
    const values = xc220Zone2();
    values[CR_URL] = "http://10.20.0.80:7547/tr069";
    values[WCD_MGMT + ".X_TP_ServiceType"] = "TR069";
    const ops = runProvision("XC220-G3v", values, ["203.0.113.10"]);
    const sets = setsOf(ops);
    assert.equal(sets[WCD_INET + ".X_TP_ServiceType"], undefined);
    assert.equal(sets[FWD + ".1.DestIPAddress"], undefined);
  });

  it("unsupported productClass writes nothing", () => {
    const ops = runProvision("F6600R", xc220Zone2());
    assert.deepEqual(ops.filter((item) => item.op === "set" || item.op === "add"), []);
  });
});
