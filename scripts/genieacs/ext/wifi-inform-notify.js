const http = require("http");
const https = require("https");
const { URL } = require("url");

const REQUEST_TIMEOUT_MS = 2500;
const LAB_SERIALS = {
  ZTEGDC47BFFD: true,
  "12345B4641531C0B6": true,
};

function notify(args, callback) {
  const serial = args[0];
  const deviceId = args[1];
  const payload = args[2];
  const prodBase = process.env.GENIEACS_TO_ACS_NOTIFY_URL;
  const stagingBase = process.env.GENIEACS_TO_ACS_STAGING_NOTIFY_URL;
  const key = process.env.GENIEACS_TO_ACS_API_KEY;
  if (!prodBase || !key || !serial) {
    callback(null, "skipped");
    return;
  }
  const targets = [prodBase];
  if (LAB_SERIALS[serial] && stagingBase) {
    targets.push(stagingBase);
  }
  const body = JSON.stringify({
    serial: serial,
    deviceId: deviceId || null,
    payload: payload || null,
  });
  let remaining = targets.length;
  const results = [];
  targets.forEach((base, index) => {
    postNotify(base, key, body, (status) => {
      results[index] = status;
      remaining -= 1;
      if (remaining === 0) {
        callback(null, results[0]);
      }
    });
  });
}

function postNotify(base, key, body, done) {
  let url;
  try {
    url = new URL(String(base).replace(/\/$/, "") + "/api/acs/v1/cpe/inform-notify");
  } catch (err) {
    done("bad-url");
    return;
  }
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
      done("http-" + res.statusCode);
    },
  );
  req.on("timeout", () => {
    req.destroy();
    done("timeout");
  });
  req.on("error", () => {
    done("error");
  });
  req.write(body);
  req.end();
}

exports.notify = notify;
