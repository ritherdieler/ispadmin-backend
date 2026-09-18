const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const vm = require("node:vm");

const extPath = path.join(__dirname, "../genieacs/ext/wifi-inform-notify.js");
const provisionPath = path.join(__dirname, "../genieacs/provisions/gigafiber-wifi-telemetry.js");

function loadExt(env) {
  const source = fs.readFileSync(extPath, "utf8");
  const sandbox = { module: { exports: {} }, exports: {}, require, process: { env }, Buffer, console };
  vm.runInNewContext(source, sandbox, { filename: "wifi-inform-notify.js" });
  return sandbox.module.exports.notify || sandbox.exports.notify;
}

test("wifi-inform-notify skips when env missing", async () => {
  const notify = loadExt({});
  assert.equal(typeof notify, "function");
  const result = await new Promise((resolve, reject) => {
    notify(["12345B4641531C0B6", "B46415-V2804AX15T-12345B4641531C0B6"], (err, value) => {
      if (err) reject(err);
      else resolve(value);
    });
  });
  assert.equal(result, "skipped");
});

test("wifi-inform-notify forwards the provision payload", async () => {
  const received = [];
  const server = http.createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => { body += chunk; });
    req.on("end", () => {
      received.push({ url: req.url, key: req.headers["x-acs-key"], body: JSON.parse(body) });
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end("{}");
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();

  try {
    const notify = loadExt({
      GENIEACS_TO_ACS_NOTIFY_URL: `http://127.0.0.1:${port}/ispadmin-acs`,
      GENIEACS_TO_ACS_API_KEY: "test-key",
    });
    const payload = JSON.stringify({ v: 1, at: 1000, root: "InternetGatewayDevice.LANDevice.1", leaves: { "Hosts.HostNumberOfEntries": "3" } });
    const result = await new Promise((resolve, reject) => {
      notify(["12345B4641531C0B6", "B46415-V2804AX15T-12345B4641531C0B6", payload], (err, value) => {
        if (err) reject(err);
        else resolve(value);
      });
    });
    assert.equal(result, "http-200");
    assert.equal(received.length, 1);
    assert.equal(received[0].url, "/ispadmin-acs/api/acs/v1/cpe/inform-notify");
    assert.equal(received[0].key, "test-key");
    assert.equal(received[0].body.serial, "12345B4641531C0B6");
    assert.equal(received[0].body.deviceId, "B46415-V2804AX15T-12345B4641531C0B6");
    assert.equal(received[0].body.payload, payload);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test("wifi-inform-notify sends a null payload when the provision omits it", async () => {
  const received = [];
  const server = http.createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => { body += chunk; });
    req.on("end", () => {
      received.push(JSON.parse(body));
      res.writeHead(200);
      res.end("{}");
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try {
    const notify = loadExt({
      GENIEACS_TO_ACS_NOTIFY_URL: `http://127.0.0.1:${port}`,
      GENIEACS_TO_ACS_API_KEY: "test-key",
    });
    await new Promise((resolve, reject) => {
      notify(["SN1", null], (err, value) => (err ? reject(err) : resolve(value)));
    });
    assert.equal(received[0].payload, null, "the ACS falls back to the NBI when there is no payload");
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test("fleet serial notifies only prod even when staging url is set", async () => {
  const prodHits = [];
  const stagingHits = [];
  const prod = http.createServer((req, res) => {
    prodHits.push(req.url);
    res.writeHead(200);
    res.end("{}");
  });
  const staging = http.createServer((req, res) => {
    stagingHits.push(req.url);
    res.writeHead(200);
    res.end("{}");
  });
  await new Promise((resolve) => prod.listen(0, "127.0.0.1", resolve));
  await new Promise((resolve) => staging.listen(0, "127.0.0.1", resolve));
  try {
    const notify = loadExt({
      GENIEACS_TO_ACS_NOTIFY_URL: `http://127.0.0.1:${prod.address().port}/ispadmin`,
      GENIEACS_TO_ACS_STAGING_NOTIFY_URL: `http://127.0.0.1:${staging.address().port}/ispadmin-staging`,
      GENIEACS_TO_ACS_API_KEY: "test-key",
    });
    const result = await new Promise((resolve, reject) => {
      notify(["HWTC15F5BB66", "HWTC-V2804AX15T-HWTC15F5BB66", "{\"v\":1}"], (err, value) => {
        if (err) reject(err);
        else resolve(value);
      });
    });
    assert.equal(result, "http-200");
    assert.equal(prodHits.length, 1);
    assert.equal(stagingHits.length, 0);
    assert.equal(prodHits[0], "/ispadmin/api/acs/v1/cpe/inform-notify");
  } finally {
    await new Promise((resolve) => prod.close(resolve));
    await new Promise((resolve) => staging.close(resolve));
  }
});

test("lab serial notifies prod and staging in the same ext call", async () => {
  const received = [];
  const prod = http.createServer((req, res) => {
    received.push("prod:" + req.url);
    res.writeHead(200);
    res.end("{}");
  });
  const staging = http.createServer((req, res) => {
    received.push("staging:" + req.url);
    res.writeHead(202);
    res.end("{}");
  });
  await new Promise((resolve) => prod.listen(0, "127.0.0.1", resolve));
  await new Promise((resolve) => staging.listen(0, "127.0.0.1", resolve));
  try {
    const notify = loadExt({
      GENIEACS_TO_ACS_NOTIFY_URL: `http://127.0.0.1:${prod.address().port}/ispadmin`,
      GENIEACS_TO_ACS_STAGING_NOTIFY_URL: `http://127.0.0.1:${staging.address().port}/ispadmin-staging`,
      GENIEACS_TO_ACS_API_KEY: "test-key",
    });
    const result = await new Promise((resolve, reject) => {
      notify(["ZTEGDC47BFFD", "ZTEG-F6600R-ZTEGDC47BFFD", "{\"v\":1}"], (err, value) => {
        if (err) reject(err);
        else resolve(value);
      });
    });
    assert.equal(result, "http-200");
    assert.deepEqual(received.sort(), [
      "prod:/ispadmin/api/acs/v1/cpe/inform-notify",
      "staging:/ispadmin-staging/api/acs/v1/cpe/inform-notify",
    ]);
  } finally {
    await new Promise((resolve) => prod.close(resolve));
    await new Promise((resolve) => staging.close(resolve));
  }
});

test("lab still notifies prod when staging is down", async () => {
  const prodHits = [];
  const prod = http.createServer((req, res) => {
    prodHits.push(req.url);
    res.writeHead(200);
    res.end("{}");
  });
  await new Promise((resolve) => prod.listen(0, "127.0.0.1", resolve));
  try {
    const notify = loadExt({
      GENIEACS_TO_ACS_NOTIFY_URL: `http://127.0.0.1:${prod.address().port}/ispadmin`,
      GENIEACS_TO_ACS_STAGING_NOTIFY_URL: "http://127.0.0.1:1/ispadmin-staging",
      GENIEACS_TO_ACS_API_KEY: "test-key",
    });
    const result = await new Promise((resolve, reject) => {
      notify(["12345B4641531C0B6", "B46415-V2804AX15T-12345B4641531C0B6", "{\"v\":1}"], (err, value) => {
        if (err) reject(err);
        else resolve(value);
      });
    });
    assert.equal(result, "http-200");
    assert.equal(prodHits.length, 1);
  } finally {
    await new Promise((resolve) => prod.close(resolve));
  }
});

test("ext waits less than the GenieACS EXT_TIMEOUT default", () => {
  const source = fs.readFileSync(extPath, "utf8");
  const match = source.match(/REQUEST_TIMEOUT_MS\s*=\s*(\d+)/);
  assert.ok(match, "the HTTP timeout must be a named constant");
  const timeout = Number(match[1]);
  assert.ok(timeout < 3000, `GenieACS abandons the ext at 3000 ms, got ${timeout}`);
  assert.match(source, /timeout: REQUEST_TIMEOUT_MS/);
});

test("pilot provision calls the ext after the WLAN declares and without try/catch", () => {
  const source = fs.readFileSync(provisionPath, "utf8");
  assert.match(source, /ext\("wifi-inform-notify", "notify"/);
  assert.match(source, /DeviceID\.SerialNumber/);
  assert.doesNotMatch(source, /try\s*\{[\s\S]*ext\(/);
  const extAt = source.indexOf('ext("wifi-inform-notify"');
  const wlanAt = source.lastIndexOf("WLANConfiguration");
  assert.ok(extAt > 0 && extAt > wlanAt, "the ext carries the declared values, so it must run after them");
});
