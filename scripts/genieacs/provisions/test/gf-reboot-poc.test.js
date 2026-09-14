const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const SCRIPT = fs.readFileSync(path.join(__dirname, "..", "gf-reboot-poc.js"), "utf8");

function runProvision() {
  const ops = [];
  function declare(p, _timestamps, values) {
    if (values && Object.prototype.hasOwnProperty.call(values, "value")) {
      ops.push({ op: "set", path: p, value: values.value });
    }
    return { path: p };
  }
  vm.runInNewContext(
    "(function () {\n" + SCRIPT + "\n})()",
    { args: [], declare, commit() { ops.push({ op: "commit" }); }, log() {}, Date },
    { filename: "gf-reboot-poc.js" },
  );
  return ops;
}

describe("gf-reboot-poc", () => {
  it("schedules Reboot and nothing else", () => {
    const ops = runProvision();
    assert.equal(ops.length, 1);
    assert.equal(ops[0].op, "set");
    assert.equal(ops[0].path, "Reboot");
  });
});
