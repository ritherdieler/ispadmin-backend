const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const SCRIPT = fs.readFileSync(
  path.join(__dirname, "..", "gf-tr069-vlan1000.js"),
  "utf8",
);

const WCD1 = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1";
const WCD2 = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2";
const VSOL_INET = WCD1 + ".WANPPPConnection.1";
const VSOL_TR069 = WCD2 + ".WANIPConnection.1";
const ZTE_INET = WCD1 + ".WANIPConnection.1";
const ZTE_TR069 = WCD1 + ".WANIPConnection.2";

function runProvision(productClass, values, extraArgs, options) {
  const ops = [];
  const store = Object.assign({}, values);
  const genieacsDeclare = !options || options.genieacsDeclare !== false;

  function readStored(p) {
    if (Object.prototype.hasOwnProperty.call(store, p)) return store[p];
    return undefined;
  }

  function wildcardRegex(wild) {
    const token = "\u0000";
    const marked = String(wild).split("*").join(token);
    const escaped = marked.replace(/[.+?^${}()|[\]\\]/g, "\\$&");
    return new RegExp("^" + escaped.split(token).join("[^.]+") + "$");
  }

  function wildcardMatches(wild) {
    const re = wildcardRegex(wild);
    return Object.keys(store)
      .filter((key) => re.test(key))
      .sort()
      .map((key) => ({ path: key, value: [store[key]] }));
  }

  function genieWrapper(declaredPath, matches) {
    return {
      path: matches.length ? matches[0].path : declaredPath,
      value: matches.length ? matches[0].value : undefined,
      size: matches.length,
    };
  }

  function declare(p, timestamps, nextValues) {
    if (p === "DeviceID.ProductClass") {
      return { value: [productClass], path: p };
    }
    if (nextValues && Object.prototype.hasOwnProperty.call(nextValues, "value")) {
      ops.push({ op: "set", path: p, value: nextValues.value });
      store[p] = nextValues.value;
      return { value: [nextValues.value], path: p };
    }
    if (String(p).indexOf("*") !== -1) {
      const items = wildcardMatches(p);
      if (genieacsDeclare) return genieWrapper(p, items);
      items.size = items.length;
      items.path = p;
      return items;
    }
    if (timestamps && Object.prototype.hasOwnProperty.call(timestamps, "path")) {
      const found = Object.keys(store).some((key) => key === p || key.indexOf(p + ".") === 0);
      return { path: found ? p : undefined, size: found ? 1 : undefined };
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
      args: extraArgs || [1000],
      declare,
      commit() {
        ops.push({ op: "commit" });
      },
      log() {},
      Date,
    },
    { filename: "gf-tr069-vlan1000.js" },
  );
  return ops;
}

function setsOf(ops) {
  return Object.fromEntries(ops.filter((item) => item.op === "set").map((item) => [item.path, item.value]));
}

function vsolInverted() {
  const values = {};
  values["InternetGatewayDevice.ManagementServer.ConnectionRequestURL"] = "http://192.168.254.206:7547/tr069";
  values[VSOL_INET + ".ExternalIPAddress"] = "10.64.0.40";
  values[VSOL_INET + ".X_CT-COM_VLANIDMark"] = 100;
  values[WCD1 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"] = 100;
  values[VSOL_TR069 + ".ExternalIPAddress"] = "192.168.254.206";
  values[VSOL_TR069 + ".X_CT-COM_VLANIDMark"] = 100;
  values[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"] = 100;
  return values;
}

function zteInverted() {
  const values = {};
  values["InternetGatewayDevice.ManagementServer.ConnectionRequestURL"] = "http://192.168.252.10:7547/tr069";
  values[ZTE_INET + ".ExternalIPAddress"] = "192.168.30.20";
  values[ZTE_INET + ".X_ZTE-COM_VLANID"] = 100;
  values[ZTE_TR069 + ".ExternalIPAddress"] = "192.168.252.10";
  values[ZTE_TR069 + ".X_ZTE-COM_VLANID"] = 100;
  return values;
}

describe("gf-tr069-vlan1000", () => {
  it("VSOL inverted retags only WCD.2 TR-069 CT-COM leaves to 1000", () => {
    const sets = setsOf(runProvision("V2804AX15T", vsolInverted()));
    assert.equal(sets[VSOL_TR069 + ".X_CT-COM_VLANIDMark"], 1000);
    assert.equal(sets[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"], 1000);
    assert.equal(sets[VSOL_INET + ".X_CT-COM_VLANIDMark"], undefined);
    assert.equal(sets[WCD1 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"], undefined);
    assert.equal(sets[VSOL_TR069 + ".X_ZTE-COM_VLANID"], undefined);
  });

  it("VSOLVA74 uses the same CT-COM leaves as V2804AX15T", () => {
    const sets = setsOf(runProvision("VSOLVA74", vsolInverted()));
    assert.equal(sets[VSOL_TR069 + ".X_CT-COM_VLANIDMark"], 1000);
    assert.equal(sets[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"], 1000);
  });

  it("F6600R inverted retags only the WAN whose IP matches the CR host", () => {
    const sets = setsOf(runProvision("F6600R", zteInverted()));
    assert.equal(sets[ZTE_TR069 + ".X_ZTE-COM_VLANID"], 1000);
    assert.equal(sets[ZTE_INET + ".X_ZTE-COM_VLANID"], undefined);
    assert.equal(sets[ZTE_TR069 + ".X_CT-COM_VLANIDMark"], undefined);
  });

  it("does not write when TR-069 VLAN is already 1000", () => {
    const values = vsolInverted();
    values[VSOL_TR069 + ".ExternalIPAddress"] = "10.20.0.2";
    values["InternetGatewayDevice.ManagementServer.ConnectionRequestURL"] = "http://10.20.0.2:7547/tr069";
    values[VSOL_TR069 + ".X_CT-COM_VLANIDMark"] = 1000;
    values[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"] = 1000;
    const ops = runProvision("V2804AX15T", values);
    assert.deepEqual(ops.filter((item) => item.op === "set"), []);
  });

  it("finds TR-069 by VLAN 100 DHCP IP even when it is not WCD.2", () => {
    const oddWcd = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4";
    const oddWan = oddWcd + ".WANIPConnection.4";
    const values = vsolInverted();
    delete values[VSOL_TR069 + ".ExternalIPAddress"];
    delete values[VSOL_TR069 + ".X_CT-COM_VLANIDMark"];
    delete values[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"];
    values[oddWan + ".ExternalIPAddress"] = "192.168.253.40";
    values[oddWan + ".X_CT-COM_VLANIDMark"] = 100;
    values[oddWcd + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"] = 100;
    values["InternetGatewayDevice.ManagementServer.ConnectionRequestURL"] = "http://192.168.253.40:7547/tr069";
    const sets = setsOf(runProvision("V2804AX15T", values));
    assert.equal(sets[oddWan + ".X_CT-COM_VLANIDMark"], 1000);
    assert.equal(sets[oddWcd + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"], 1000);
    assert.equal(sets[VSOL_INET + ".X_CT-COM_VLANIDMark"], undefined);
    assert.equal(sets[VSOL_TR069 + ".X_CT-COM_VLANIDMark"], undefined);
  });

  it("does not write when no WAN has a TR-069 DHCP IP on VLAN 100", () => {
    const values = vsolInverted();
    values[VSOL_TR069 + ".ExternalIPAddress"] = "192.168.30.9";
    values["InternetGatewayDevice.ManagementServer.ConnectionRequestURL"] = "http://192.168.30.9:7547/tr069";
    const ops = runProvision("V2804AX15T", values);
    assert.deepEqual(ops.filter((item) => item.op === "set"), []);
  });

  it("unsupported productClass writes nothing", () => {
    const ops = runProvision("IGD", vsolInverted());
    assert.deepEqual(ops.filter((item) => item.op === "set"), []);
  });

  it("VSOL retags when declare() is a GenieACS ParameterWrapper not an array", () => {
    const sets = setsOf(runProvision("V2804AX15T", vsolInverted()));
    assert.equal(sets[VSOL_TR069 + ".X_CT-COM_VLANIDMark"], 1000);
    assert.equal(sets[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"], 1000);
    assert.equal(sets[VSOL_INET + ".X_CT-COM_VLANIDMark"], undefined);
  });

  it("finds TR-069 when CT-COM VLAN leaves are strings", () => {
    const values = vsolInverted();
    values[VSOL_TR069 + ".X_CT-COM_VLANIDMark"] = "100";
    values[WCD2 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"] = "100";
    values[VSOL_INET + ".X_CT-COM_VLANIDMark"] = "100";
    values[WCD1 + ".X_CT-COM_WANGponLinkConfig.VLANIDMark"] = "100";
    const sets = setsOf(runProvision("V2804AX15T", values));
    assert.equal(sets[VSOL_TR069 + ".X_CT-COM_VLANIDMark"], 1000);
    assert.equal(sets[VSOL_INET + ".X_CT-COM_VLANIDMark"], undefined);
  });
});
