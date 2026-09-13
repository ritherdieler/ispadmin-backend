const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const GfVparams = require("../lib/gf-vparams-core.js");

const V2804_PPP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1";
const V2804_IP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1";
const V2804_MGMT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1";
const F6600_PPP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2";
const F6600_IP = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2";
const F6600_MGMT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1";
const WLAN24_V2804 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";
const WLAN5_V2804 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";
const WLAN24_F6600 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";
const WLAN5_F6600 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";

function mockDeclare(tree) {
  const writes = [];
  const addObjects = [];
  const reads = [];
  function declare(path, _timestamps, values) {
    reads.push(path);
    if (values && Object.prototype.hasOwnProperty.call(values, "path")) {
      addObjects.push({ path, count: values.path });
    }
    if (values && Object.prototype.hasOwnProperty.call(values, "value")) {
      writes.push({ path, value: values.value });
    }
    const found = tree[path];
    return { value: found === undefined ? undefined : [found] };
  }
  declare.writes = writes;
  declare.addObjects = addObjects;
  declare.reads = reads;
  return declare;
}

function pppoePayload() {
  return JSON.stringify({
    username: "gf2398",
    password: "secret-pppoe",
    vlanId: 100,
    connectionName: "2_INTERNET_R_VID_100",
  });
}

function staticPayload(ip) {
  return JSON.stringify({
    ip,
    subnetMask: "255.255.255.0",
    gateway: "192.168.1.1",
    dns: "8.8.8.8,8.8.4.4",
    vlanId: 1,
    connectionName: "2_INTERNET_R_VID_1",
  });
}

function f6600Declare() {
  return mockDeclare({
    "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.253.10:7547/",
    [F6600_MGMT + ".ExternalIPAddress"]: "192.168.253.10",
    [F6600_PPP + ".Username"]: "",
    [F6600_PPP + ".Enable"]: false,
  });
}

function assertPppoeCredentials(declare) {
  const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
  assert.equal(byPath[F6600_PPP + ".ConnectionType"], "IP_Routed");
  assert.equal(byPath[F6600_PPP + ".Username"], "gf2398");
  assert.equal(byPath[F6600_PPP + ".Password"], "secret-pppoe");
  assert.equal(byPath[F6600_PPP + ".Enable"], undefined);
  assert.equal(byPath[F6600_PPP + ".X_ZTE-COM_VLANID"], undefined);
  assert.equal(byPath[F6600_PPP + ".X_CT-COM_VLANIDMark"], undefined);
  assert.equal(declare.addObjects.length, 0);
}

describe("GfApplyInternetPppoe payload parse", () => {
  it("parses a plain JSON string", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe({ _ProductClass: "F6600R" }, declare, [pppoePayload()]);
    assertPppoeCredentials(declare);
  });

  it("parses a double-escaped JSON string", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [JSON.stringify(pppoePayload())],
    );
    assertPppoeCredentials(declare);
  });

  it("parses a GenieACS [value, xsd:string] tuple", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [[pppoePayload(), "xsd:string"]],
    );
    assertPppoeCredentials(declare);
  });

  it("parses an already-decoded object", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [JSON.parse(pppoePayload())],
    );
    assertPppoeCredentials(declare);
  });

  it("parses a bare JSON string args (not wrapped in array)", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe({ _ProductClass: "F6600R" }, declare, pppoePayload());
    assertPppoeCredentials(declare);
  });

  it("parses a top-level [json, xsd:string] args array", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [pppoePayload(), "xsd:string"],
    );
    assertPppoeCredentials(declare);
  });

  it("parses GenieACS SET args of attribute objects with value/writable", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [
        { value: [pppoePayload(), "xsd:string"], writable: true },
        { value: Date.now() },
        { notification: 0 },
        { accessList: [] },
      ],
    );
    assertPppoeCredentials(declare);
  });

  it("GET-shaped GenieACS args do not AddObject or SPV", () => {
    const declare = f6600Declare();
    const result = GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [
        { value: Date.now(), writable: Date.now() },
        {},
        { object: 1 },
        { object: 1 },
      ],
    );
    assert.equal(declare.writes.length, 0);
    assert.equal(declare.addObjects.length, 0);
    assert.equal(result.writable, true);
  });

  it("parses GenieACS 1.2 SET provision args[1].value", () => {
    const declare = f6600Declare();
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [
        {},
        { value: [pppoePayload(), "xsd:string"] },
        { value: Date.now(), writable: Date.now() },
        { value: ["", "xsd:string"], writable: true },
      ],
    );
    assertPppoeCredentials(declare);
  });
});

describe("GfApplyInternetPppoe mapping", () => {
  it("V2804AX15T applies PPP on WCD.2 and never writes WCD.1", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://10.20.0.2:7547/",
      [V2804_MGMT + ".ExternalIPAddress"]: "10.20.0.2",
      [V2804_PPP + ".Username"]: "",
      [V2804_PPP + ".Enable"]: false,
    });
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "V2804AX15T" },
      declare,
      [pppoePayload()],
    );
    const paths = declare.writes.map((w) => w.path);
    const addPaths = declare.addObjects.map((a) => a.path);
    assert.equal(addPaths.length, 0, `ready VSOL must not AddObject, got ${JSON.stringify(addPaths)}`);
    assert.ok(addPaths.every((p) => !p.includes("WANConnectionDevice.1.")));
    assert.ok(paths.some((p) => p.startsWith(V2804_PPP + ".")));
    assert.equal(
      declare.writes.find((w) => w.path === V2804_IP + ".Enable")?.value,
      false,
    );
    assert.ok(paths.some((p) => p === V2804_PPP + ".X_CT-COM_VLANIDMark"));
    assert.ok(paths.every((p) => !p.includes("WANConnectionDevice.1.")));
  });

  it("F6600R applies PPP on WCD.1 PPP.2 and does not write management WANIP.1", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.253.10:7547/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.253.10",
      [F6600_PPP + ".Username"]: "gf2398",
      [F6600_PPP + ".Enable"]: false,
    });
    GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [pppoePayload()],
    );
    const paths = declare.writes.map((w) => w.path);
    const addPaths = declare.addObjects.map((a) => a.path);
    assert.equal(addPaths.length, 0, `ready F6600R must not AddObject, got ${JSON.stringify(addPaths)}`);
    assert.ok(
      addPaths.every((p) => !p.includes("WANIPConnection")),
      `F6600R must not AddObject WANIP, got ${JSON.stringify(addPaths)}`,
    );
    assert.ok(paths.some((p) => p.startsWith(F6600_PPP + ".")));
    assert.equal(
      declare.writes.find((w) => w.path === F6600_IP + ".Enable")?.value,
      false,
    );
    assert.ok(paths.every((p) => p !== F6600_MGMT + ".Enable" && !p.startsWith(F6600_MGMT + ".")));
  });

  it("F6600R defers Username Password Enable until PPP instance exists", () => {
    const missing = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
    });
    const first = GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      missing,
      [pppoePayload()],
    );
    const addPaths = missing.addObjects.map((a) => a.path);
    assert.ok(
      addPaths.some((p) => p === "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.*"),
      `expected AddObject WANPPP, got ${JSON.stringify(addPaths)}`,
    );
    const firstPppWrites = missing.writes.filter(
      (w) => w.path === F6600_PPP || w.path.startsWith(F6600_PPP + "."),
    );
    assert.equal(
      firstPppWrites.length,
      0,
      `AddObject cycle must not SPV PPP children, got ${JSON.stringify(firstPppWrites)}`,
    );
    assert.equal(JSON.parse(first.value[0]).pending, "addObject");

    const ready = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
      [F6600_PPP + ".Username"]: "",
      [F6600_PPP + ".Enable"]: false,
    });
    const second = GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      ready,
      [pppoePayload()],
    );
    const credPaths = ready.writes.map((w) => w.path);
    assert.equal(ready.addObjects.length, 0, `ready cycle must not AddObject, got ${JSON.stringify(ready.addObjects)}`);
    assert.equal(Object.fromEntries(ready.writes.map((w) => [w.path, w.value]))[F6600_PPP + ".Username"], "gf2398");
    assert.equal(Object.fromEntries(ready.writes.map((w) => [w.path, w.value]))[F6600_PPP + ".Password"], "secret-pppoe");
    assert.equal(Object.fromEntries(ready.writes.map((w) => [w.path, w.value]))[F6600_PPP + ".ConnectionType"], "IP_Routed");
    assert.ok(!credPaths.includes(F6600_PPP + ".Enable"), `credentials cycle must not Enable, got ${JSON.stringify(credPaths)}`);
    assert.ok(!credPaths.some((p) => p.includes("VLAN")), `credentials cycle must not set VLAN, got ${JSON.stringify(credPaths)}`);
    assert.equal(JSON.parse(second.value[0]).pending, "credentials");

    const armed = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
      [F6600_PPP + ".Username"]: "gf2398",
      [F6600_PPP + ".Enable"]: false,
    });
    const third = GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      armed,
      [pppoePayload()],
    );
    const byPath = Object.fromEntries(armed.writes.map((w) => [w.path, w.value]));
    assert.equal(armed.addObjects.length, 0);
    assert.equal(byPath[F6600_PPP + ".Enable"], true);
    assert.equal(byPath[F6600_IP + ".Enable"], false);
    assert.equal(byPath[F6600_PPP + ".X_ZTE-COM_VLANID"], 100);
    assert.equal(byPath[F6600_PPP + ".Username"], undefined);
    const allowed = new Set([
      F6600_PPP + ".Enable",
      F6600_PPP + ".X_ZTE-COM_VLANID",
      F6600_PPP + ".X_ZTE-COM_VLANEnable",
      F6600_IP + ".Enable",
    ]);
    const extra = armed.writes.map((w) => w.path).filter((p) => !allowed.has(p));
    assert.equal(extra.length, 0, `F6600R enable cycle extra leaves ${JSON.stringify(extra)}`);
    assert.ok(armed.writes.every((w) => w.path !== F6600_MGMT + ".Enable" && !w.path.startsWith(F6600_MGMT + ".")));
    assert.ok(!JSON.parse(third.value[0]).pending);
  });

  it("F6600R does not declare TR-181 Device.ManagementServer when IGD CR URL is unread", () => {
    const declare = mockDeclare({
      [F6600_PPP + ".Username"]: "",
      [F6600_PPP + ".Enable"]: false,
    });
    GfVparams.handleApplyInternetPppoe({ _ProductClass: "F6600R" }, declare, [pppoePayload()]);
    assert.ok(
      declare.reads.every((p) => !String(p).startsWith("Device.")),
      `F6600R must not declare Device.*, got ${JSON.stringify(declare.reads)}`,
    );
  });

  it("F6600R slim does not GPV internet WAN ExternalIPAddress", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
      [F6600_PPP + ".Username"]: "",
      [F6600_PPP + ".Enable"]: false,
    });
    GfVparams.handleApplyInternetPppoe({ _ProductClass: "F6600R" }, declare, [pppoePayload()]);
    assert.ok(
      !declare.reads.includes(F6600_PPP + ".ExternalIPAddress"),
      `slim must not GPV PPP ExternalIP, got ${JSON.stringify(declare.reads)}`,
    );
    assert.ok(
      !declare.reads.includes(F6600_IP + ".ExternalIPAddress"),
      `slim must not GPV WANIP.2 ExternalIP, got ${JSON.stringify(declare.reads)}`,
    );
  });

  it("F6600R enable cycle writes nothing when PPP already enabled", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
      [F6600_PPP + ".Username"]: "gf2398",
      [F6600_PPP + ".Enable"]: true,
      [F6600_PPP + ".X_ZTE-COM_VLANID"]: 100,
      [F6600_PPP + ".X_ZTE-COM_VLANEnable"]: 1,
      [F6600_IP + ".Enable"]: false,
    });
    const result = GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [pppoePayload()],
    );
    assert.equal(declare.writes.length, 0, `settled enable must not SPV, got ${JSON.stringify(declare.writes)}`);
    assert.ok(!JSON.parse(result.value[0]).pending);
  });

  it("F6600R enable cycle skips VLAN and WANIP.2 when already applied", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
      [F6600_PPP + ".Username"]: "gf2398",
      [F6600_PPP + ".Enable"]: false,
      [F6600_PPP + ".X_ZTE-COM_VLANID"]: 100,
      [F6600_PPP + ".X_ZTE-COM_VLANEnable"]: 1,
      [F6600_IP + ".Enable"]: false,
    });
    const result = GfVparams.handleApplyInternetPppoe(
      { _ProductClass: "F6600R" },
      declare,
      [pppoePayload()],
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[F6600_PPP + ".Enable"], true);
    assert.equal(byPath[F6600_PPP + ".X_ZTE-COM_VLANID"], undefined);
    assert.equal(byPath[F6600_PPP + ".X_ZTE-COM_VLANEnable"], undefined);
    assert.equal(byPath[F6600_IP + ".Enable"], undefined);
    assert.ok(!JSON.parse(result.value[0]).pending);
  });

  it("IGD is rejected without SPV", () => {
    const declare = mockDeclare({});
    assert.throws(
      () => GfVparams.handleApplyInternetPppoe({ _ProductClass: "IGD" }, declare, [pppoePayload()]),
      /unsupported product class/i,
    );
    assert.equal(declare.writes.length, 0);
  });

  it("HG8145X6 is rejected without SPV", () => {
    const declare = mockDeclare({});
    assert.throws(
      () => GfVparams.handleApplyInternetPppoe({ _ProductClass: "HG8145X6" }, declare, [pppoePayload()]),
      /unsupported product class/i,
    );
    assert.equal(declare.writes.length, 0);
  });

  it("set targeting the management WAN aborts", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://10.20.0.2:7547/",
      [V2804_IP + ".ExternalIPAddress"]: "10.20.0.2",
    });
    assert.throws(
      () => GfVparams.handleApplyInternetPppoe(
        { _ProductClass: "V2804AX15T" },
        declare,
        [pppoePayload()],
      ),
      /management wan/i,
    );
    assert.equal(declare.writes.length, 0);
  });
});

describe("GfApplyInternetStatic mapping", () => {
  it("V2804AX15T writes static IP on WCD.2 not WCD.1", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://10.20.0.2:7547/",
      [V2804_MGMT + ".ExternalIPAddress"]: "10.20.0.2",
    });
    GfVparams.handleApplyInternetStatic(
      { _ProductClass: "V2804AX15T" },
      declare,
      [staticPayload("192.168.1.50")],
    );
    const paths = declare.writes.map((w) => w.path);
    assert.ok(paths.includes(V2804_IP + ".ExternalIPAddress"));
    assert.ok(paths.every((p) => !p.includes("WANConnectionDevice.1.")));
  });
});

describe("GfSetWifi mapping", () => {
  it("V2804AX15T uses WLAN.5 for 2.4 and WLAN.1 for 5", () => {
    const declare = mockDeclare({});
    GfVparams.handleSetWifi(
      { _ProductClass: "V2804AX15T" },
      declare,
      [JSON.stringify({
        ssid24: "GIGA-24",
        password24: "pass-24xx",
        ssid5: "GIGA-5",
        password5: "pass-5xxx",
      })],
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[WLAN24_V2804 + ".SSID"], "GIGA-24");
    assert.equal(byPath[WLAN5_V2804 + ".SSID"], "GIGA-5");
  });

  it("F6600R uses WLAN.1 for 2.4 and WLAN.5 for 5", () => {
    const declare = mockDeclare({});
    GfVparams.handleSetWifi(
      { _ProductClass: "F6600R" },
      declare,
      [JSON.stringify({
        ssid24: "ZTE-24",
        password24: "pass-24xx",
        ssid5: "ZTE-5",
        password5: "pass-5xxx",
      })],
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[WLAN24_F6600 + ".SSID"], "ZTE-24");
    assert.equal(byPath[WLAN5_F6600 + ".SSID"], "ZTE-5");
  });
});

describe("status vparams", () => {
  it("GfInternetStatus resolves product class from DeviceID when sandbox has no _deviceId", () => {
    const declare = mockDeclare({
      "DeviceID.ProductClass": "V2804AX15T",
      [V2804_PPP + ".ConnectionStatus"]: "Connected",
      [V2804_PPP + ".ExternalIPAddress"]: "10.64.0.2",
    });
    const result = GfVparams.handleInternetStatus(null, declare, []);
    const parsed = JSON.parse(result.value[0]);
    assert.equal(parsed.connected, true);
    assert.equal(parsed.ip, "10.64.0.2");
    assert.equal(parsed.productClass, "V2804AX15T");
  });

  it("GfInternetStatus reports connected and ip from the client WAN", () => {
    const declare = mockDeclare({
      [V2804_PPP + ".ConnectionStatus"]: "Connected",
      [V2804_PPP + ".ExternalIPAddress"]: "10.64.0.22",
    });
    const result = GfVparams.handleInternetStatus({ _ProductClass: "V2804AX15T" }, declare, []);
    const parsed = JSON.parse(result.value[0]);
    assert.equal(parsed.connected, true);
    assert.equal(parsed.ip, "10.64.0.22");
    assert.equal(parsed.productClass, "V2804AX15T");
  });

  it("GfWifiStatus reads SSIDs without writing", () => {
    const declare = mockDeclare({
      [WLAN24_V2804 + ".SSID"]: "keep-24",
      [WLAN5_V2804 + ".SSID"]: "keep-5",
    });
    const result = GfVparams.handleWifiStatus({ _ProductClass: "V2804AX15T" }, declare, []);
    const parsed = JSON.parse(result.value[0]);
    assert.equal(parsed.ssid24, "keep-24");
    assert.equal(parsed.ssid5, "keep-5");
    assert.equal(declare.writes.length, 0);
  });
});

describe("GfReboot", () => {
  it("declares Reboot and writes nothing else", () => {
    const declare = mockDeclare({});
    GfVparams.handleReboot({ _ProductClass: "V2804AX15T" }, declare, ['{"requested":true}']);
    assert.equal(declare.writes.length, 1);
    assert.equal(declare.writes[0].path, "Reboot");
  });
});

function genieSet(value) {
  return [{}, { value }];
}

function genieGet() {
  return [{ value: Date.now(), writable: Date.now() }, {}, { object: 1 }, { object: 1 }];
}

function f6600ReadyTree(extra) {
  return {
    "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
    [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
    [F6600_PPP + ".Username"]: "",
    [F6600_PPP + ".Enable"]: false,
    ...extra,
  };
}

function vsolReadyTree(extra) {
  return {
    "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://10.20.0.2:7547/",
    [V2804_MGMT + ".ExternalIPAddress"]: "10.20.0.2",
    [V2804_PPP + ".Username"]: "",
    [V2804_PPP + ".Enable"]: false,
    ...extra,
  };
}

describe("GfPppoeUsername", () => {
  it("SET writes Username on F6600R PPP.2 and never WANIP.1", () => {
    const declare = mockDeclare(f6600ReadyTree());
    const result = GfVparams.handlePppoeUsername(
      { _ProductClass: "F6600R" },
      declare,
      genieSet(["gflabzte", "xsd:string"]),
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[F6600_PPP + ".Username"], "gflabzte");
    assert.equal(result.value[0], "gflabzte");
    assert.equal(result.value[1], "xsd:string");
    assert.ok(declare.writes.every((w) => w.path !== F6600_MGMT && !w.path.startsWith(F6600_MGMT + ".")));
    assert.equal(declare.addObjects.length, 0);
  });

  it("SET writes Username on VSOL WCD.2 and never WCD.1", () => {
    const declare = mockDeclare(vsolReadyTree());
    GfVparams.handlePppoeUsername(
      { _ProductClass: "V2804AX15T" },
      declare,
      genieSet(["gflabzte", "xsd:string"]),
    );
    const paths = declare.writes.map((w) => w.path);
    assert.ok(paths.includes(V2804_PPP + ".Username"));
    assert.ok(paths.every((p) => !p.includes("WANConnectionDevice.1.")));
  });

  it("GET returns scalar username not JSON", () => {
    const declare = mockDeclare(f6600ReadyTree({ [F6600_PPP + ".Username"]: "gflabzte" }));
    const result = GfVparams.handlePppoeUsername({ _ProductClass: "F6600R" }, declare, genieGet());
    assert.equal(result.value[0], "gflabzte");
    assert.equal(result.value[1], "xsd:string");
    assert.equal(declare.writes.length, 0);
    assert.equal(declare.addObjects.length, 0);
    assert.equal(typeof result.value[0], "string");
    assert.throws(() => JSON.parse(result.value[0]));
  });

  it("SET AddObject when PPP missing and does not SPV Username", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
    });
    const result = GfVparams.handlePppoeUsername(
      { _ProductClass: "F6600R" },
      declare,
      genieSet(["gflabzte", "xsd:string"]),
    );
    assert.ok(
      declare.addObjects.some(
        (a) => a.path === "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.*",
      ),
    );
    assert.equal(declare.writes.length, 0);
    assert.equal(result.value[0], "");
    assert.equal(result.value[1], "xsd:string");
  });
});

describe("GfPppoePassword", () => {
  it("SET writes Password on F6600R PPP.2", () => {
    const declare = mockDeclare(f6600ReadyTree());
    const result = GfVparams.handlePppoePassword(
      { _ProductClass: "F6600R" },
      declare,
      genieSet(["secret-pppoe", "xsd:string"]),
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[F6600_PPP + ".Password"], "secret-pppoe");
    assert.equal(result.value[0], "secret-pppoe");
    assert.equal(result.value[1], "xsd:string");
    assert.equal(declare.addObjects.length, 0);
    assert.ok(declare.writes.every((w) => w.path !== F6600_MGMT && !w.path.startsWith(F6600_MGMT + ".")));
  });

  it("GET returns scalar password not JSON", () => {
    const declare = mockDeclare(f6600ReadyTree({ [F6600_PPP + ".Password"]: "secret-pppoe" }));
    const result = GfVparams.handlePppoePassword({ _ProductClass: "F6600R" }, declare, genieGet());
    assert.equal(result.value[0], "secret-pppoe");
    assert.equal(result.value[1], "xsd:string");
    assert.equal(declare.writes.length, 0);
  });

  it("SET without PPP instance does not AddObject", () => {
    const declare = mockDeclare({
      "InternetGatewayDevice.ManagementServer.ConnectionRequestURL": "http://192.168.255.236:58000/",
      [F6600_MGMT + ".ExternalIPAddress"]: "192.168.255.236",
    });
    GfVparams.handlePppoePassword(
      { _ProductClass: "F6600R" },
      declare,
      genieSet(["secret-pppoe", "xsd:string"]),
    );
    assert.equal(declare.addObjects.length, 0);
    assert.equal(declare.writes.length, 0);
  });
});

describe("GfPppoeVlanId", () => {
  it("SET writes ZTE VLAN leaves on F6600R and not CT-COM or WANIP.1", () => {
    const declare = mockDeclare(f6600ReadyTree());
    const result = GfVparams.handlePppoeVlanId(
      { _ProductClass: "F6600R" },
      declare,
      genieSet([100, "xsd:unsignedInt"]),
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[F6600_PPP + ".X_ZTE-COM_VLANID"], 100);
    assert.equal(byPath[F6600_PPP + ".X_ZTE-COM_VLANEnable"], 1);
    assert.equal(byPath[F6600_PPP + ".X_CT-COM_VLANIDMark"], undefined);
    assert.equal(result.value[0], 100);
    assert.equal(result.value[1], "xsd:unsignedInt");
    assert.ok(declare.writes.every((w) => w.path !== F6600_MGMT && !w.path.startsWith(F6600_MGMT + ".")));
  });

  it("SET writes ZTE and CT-COM VLAN on VSOL WCD.2 never WCD.1", () => {
    const declare = mockDeclare(vsolReadyTree());
    GfVparams.handlePppoeVlanId({ _ProductClass: "V2804AX15T" }, declare, genieSet(100));
    const paths = declare.writes.map((w) => w.path);
    assert.ok(paths.includes(V2804_PPP + ".X_ZTE-COM_VLANID"));
    assert.ok(paths.includes(V2804_PPP + ".X_ZTE-COM_VLANEnable"));
    assert.ok(paths.includes(V2804_PPP + ".X_CT-COM_VLANIDMark"));
    assert.ok(paths.includes("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark"));
    assert.ok(paths.every((p) => !p.includes("WANConnectionDevice.1.")));
  });

  it("GET returns scalar vlan not JSON", () => {
    const declare = mockDeclare(f6600ReadyTree({ [F6600_PPP + ".X_ZTE-COM_VLANID"]: 100 }));
    const result = GfVparams.handlePppoeVlanId({ _ProductClass: "F6600R" }, declare, genieGet());
    assert.equal(result.value[0], 100);
    assert.equal(result.value[1], "xsd:unsignedInt");
    assert.equal(declare.writes.length, 0);
  });
});

describe("GfPppoeConnectionName", () => {
  it("SET writes Name on F6600R PPP.2", () => {
    const declare = mockDeclare(f6600ReadyTree());
    const result = GfVparams.handlePppoeConnectionName(
      { _ProductClass: "F6600R" },
      declare,
      genieSet(["2_INTERNET_R_VID_100", "xsd:string"]),
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[F6600_PPP + ".Name"], "2_INTERNET_R_VID_100");
    assert.equal(byPath[F6600_PPP + ".Alias"], undefined);
    assert.equal(result.value[0], "2_INTERNET_R_VID_100");
    assert.equal(result.value[1], "xsd:string");
    assert.ok(declare.writes.every((w) => w.path !== F6600_MGMT && !w.path.startsWith(F6600_MGMT + ".")));
  });

  it("SET writes Name and Alias on VSOL", () => {
    const declare = mockDeclare(vsolReadyTree());
    GfVparams.handlePppoeConnectionName(
      { _ProductClass: "V2804AX15T" },
      declare,
      genieSet(["2_INTERNET_R_VID_100", "xsd:string"]),
    );
    const byPath = Object.fromEntries(declare.writes.map((w) => [w.path, w.value]));
    assert.equal(byPath[V2804_PPP + ".Name"], "2_INTERNET_R_VID_100");
    assert.equal(byPath[V2804_PPP + ".Alias"], "2_INTERNET_R_VID_100");
    assert.ok(declare.writes.every((w) => !w.path.includes("WANConnectionDevice.1.")));
  });

  it("GET returns scalar Name not JSON", () => {
    const declare = mockDeclare(f6600ReadyTree({ [F6600_PPP + ".Name"]: "2_INTERNET_R_VID_100" }));
    const result = GfVparams.handlePppoeConnectionName({ _ProductClass: "F6600R" }, declare, genieGet());
    assert.equal(result.value[0], "2_INTERNET_R_VID_100");
    assert.equal(result.value[1], "xsd:string");
    assert.equal(declare.writes.length, 0);
  });
});
