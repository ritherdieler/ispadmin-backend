const http = require("http");
const https = require("https");
const { URL } = require("url");

// GenieACS abandons an ext call at EXT_TIMEOUT (3000 ms by default), so waiting
// longer than that only loses the notify without telling anyone.
const REQUEST_TIMEOUT_MS = 2500;

function notify(args, callback) {
  const serial = args[0];
  const deviceId = args[1];
  const payload = args[2];
  const base = process.env.GENIEACS_TO_ACS_NOTIFY_URL;
  const key = process.env.GENIEACS_TO_ACS_API_KEY;
  if (!base || !key || !serial) {
    callback(null, "skipped");
    return;
  }
  let url;
  try {
    url = new URL(base.replace(/\/$/, "") + "/api/acs/v1/cpe/inform-notify");
  } catch (err) {
    callback(null, "bad-url");
    return;
  }
  const body = JSON.stringify({
    serial: serial,
    deviceId: deviceId || null,
    payload: payload || null,
  });
  const lib = url.protocol === "https:" ? https : http;
  const req = lib.request(
    {
      hostname: url.hostname,
      port: url.port || (url.protocol === "https:" ? 443 : 80),
      path: url.pathname,
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(body),
        "X-Acs-Key": key,
      },
      timeout: REQUEST_TIMEOUT_MS,
    },
    (res) => {
      res.resume();
      callback(null, "http-" + res.statusCode);
    },
  );
  req.on("timeout", () => {
    req.destroy();
    callback(null, "timeout");
  });
  req.on("error", () => {
    callback(null, "error");
  });
  req.write(body);
  req.end();
}

exports.notify = notify;
