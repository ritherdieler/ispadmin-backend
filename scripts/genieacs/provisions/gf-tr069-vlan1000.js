// Lab/fleet: retag only the WAN that already has a TR-069 DHCP IP on VLAN 100.
// Args: [vlanId] default 1000.
// Discovers WANs by WCD/WAN instance .path (GenieACS declare is not an array).
// Selects 192.168.252.0/22 + VLAN 100. Does not write the internet WAN.
// commit() restarts this file; skip if already 1000.

const TARGET_VLAN = args && args[0] != null && String(args[0]) !== "" ? Number(args[0]) : 1000;
const WAN_DEVICE = "InternetGatewayDevice.WANDevice.1";
const CR_URL = "InternetGatewayDevice.ManagementServer.ConnectionRequestURL";

const LAYOUTS = {
  F6600R: { writeCtCom: false, writeZte: true },
  V2804AX15T: { writeCtCom: true, writeZte: false },
  VSOLVA74: { writeCtCom: true, writeZte: false },
};

function logStep(message) {
  log("gf-tr069-vlan1000 " + message);
}

function productClassOf() {
  const declared = declare("DeviceID.ProductClass", { value: 1 });
  return declared && declared.value ? declared.value[0] : "";
}

function layoutOf(productClass) {
  return LAYOUTS[productClass] || null;
}

function readValue(path) {
  const declared = declare(path, { value: 1 });
  if (!declared || !declared.value) return "";
  const value = declared.value[0];
  return value == null ? "" : String(value);
}

function ipToInt(ip) {
  const parts = String(ip).split(".");
  if (parts.length !== 4) return null;
  let n = 0;
  for (let i = 0; i < 4; i++) {
    const octet = Number(parts[i]);
    if (!Number.isFinite(octet) || octet < 0 || octet > 255) return null;
    n = (n << 8) + octet;
  }
  return n >>> 0;
}

function inCidr(ip, base, prefix) {
  const addr = ipToInt(ip);
  const net = ipToInt(base);
  if (addr == null || net == null) return false;
  const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
  return (addr & mask) === (net & mask);
}

function isTr069Vlan100Ip(ip) {
  return inCidr(ip, "192.168.252.0", 22);
}

function crHost(url) {
  const match = String(url || "").match(/^https?:\/\/\[?([0-9a-fA-F.:]+)\]?:/);
  return match ? match[1] : "";
}

function wcdPathOf(wanPath) {
  return String(wanPath).replace(/\.WAN(?:IP|PPP)Connection\.\d+$/, "");
}

function vlanLeaves(layout, wanPath) {
  const leaves = [];
  if (layout.writeCtCom) {
    leaves.push(wanPath + ".X_CT-COM_VLANIDMark");
    leaves.push(wcdPathOf(wanPath) + ".X_CT-COM_WANGponLinkConfig.VLANIDMark");
  }
  if (layout.writeZte) {
    leaves.push(wanPath + ".X_ZTE-COM_VLANID");
  }
  return leaves;
}

function appendDeclaredPaths(listed, into) {
  if (!listed) return;
  if (typeof listed.length === "number") {
    for (let i = 0; i < listed.length; i++) {
      const p = listed[i] && listed[i].path;
      if (p && String(p).indexOf("*") === -1) into.push(String(p));
    }
    return;
  }
  const getIterator = listed[Symbol.iterator];
  if (typeof getIterator !== "function") return;
  const iterator = getIterator.call(listed);
  if (!iterator || typeof iterator.next !== "function") return;
  let step = iterator.next();
  while (step && !step.done) {
    const p = step.value && step.value.path;
    if (p && String(p).indexOf("*") === -1) into.push(String(p));
    step = iterator.next();
  }
}

function instanceSegment(prefix, path) {
  if (!path || String(path).indexOf(prefix) !== 0) return "";
  const n = String(path).slice(prefix.length).split(".")[0];
  if (!/^\d+$/.test(n)) return "";
  return prefix + n;
}

function instancePaths(wildcard) {
  const paths = [];
  const listed = declare(wildcard, { path: 1 });
  appendDeclaredPaths(listed, paths);
  const prefix = String(wildcard).replace(/\*$/, "");
  const first = listed && listed.path ? instanceSegment(prefix, String(listed.path)) : "";
  if (first && paths.indexOf(first) === -1) paths.push(first);
  const size = Number(listed && listed.size) || 0;
  for (let i = 1; i <= size; i++) {
    const path = prefix + i;
    if (paths.indexOf(path) === -1) paths.push(path);
  }
  return paths;
}

function collectFromParent(layout, wans, wildcard) {
  const conns = instancePaths(wildcard);
  for (let i = 0; i < conns.length; i++) {
    const wanPath = conns[i];
    const ip = readValue(wanPath + ".ExternalIPAddress");
    if (!ip || ip === "0.0.0.0") continue;
    wans.push({
      path: wanPath,
      ip: ip,
      vlan: readWanVlan(layout, wanPath),
    });
  }
}

function collectKind(layout, kind) {
  const wans = [];
  const wcds = instancePaths(WAN_DEVICE + ".WANConnectionDevice.*");
  for (let w = 0; w < wcds.length; w++) {
    collectFromParent(layout, wans, wcds[w] + "." + kind + ".*");
  }
  return wans;
}

function readWanVlan(layout, wanPath) {
  const leaves = vlanLeaves(layout, wanPath);
  for (let i = 0; i < leaves.length; i++) {
    const n = currentVlan(leaves[i]);
    if (n != null) return n;
  }
  return null;
}

function findTr069Wan(wans, crUrl) {
  const host = crHost(crUrl);
  const hits = [];
  for (let i = 0; i < wans.length; i++) {
    if (Number(wans[i].vlan) === 100 && isTr069Vlan100Ip(wans[i].ip)) hits.push(wans[i]);
  }
  if (host) {
    for (let i = 0; i < hits.length; i++) {
      if (hits[i].ip === host) return hits[i];
    }
  }
  return hits.length ? hits[0] : null;
}

function currentVlan(path) {
  const n = Number(readValue(path));
  return Number.isFinite(n) ? n : null;
}

function alreadyOnTarget(layout, wanPath, vlanId) {
  const leaves = vlanLeaves(layout, wanPath);
  if (!leaves.length) return false;
  for (let i = 0; i < leaves.length; i++) {
    if (currentVlan(leaves[i]) !== vlanId) return false;
  }
  return true;
}

function applyTr069Vlan(layout, wanPath, vlanId) {
  const leaves = vlanLeaves(layout, wanPath);
  let wrote = false;
  for (let i = 0; i < leaves.length; i++) {
    if (currentVlan(leaves[i]) === vlanId) continue;
    declare(leaves[i], null, { value: vlanId });
    wrote = true;
  }
  if (wrote) commit();
}

logStep("start vlan=" + TARGET_VLAN);
const productClass = productClassOf();
const layout = layoutOf(productClass);
if (!layout) {
  logStep("unsupported productClass=" + productClass);
} else if (!Number.isFinite(TARGET_VLAN) || TARGET_VLAN <= 0) {
  logStep("invalid vlan");
} else {
  const cr = readValue(CR_URL);
  let wans = collectKind(layout, "WANIPConnection");
  let wan = findTr069Wan(wans, cr);
  if (!wan) {
    wans = wans.concat(collectKind(layout, "WANPPPConnection"));
    wan = findTr069Wan(wans, cr);
  }
  logStep("wans=" + wans.length + " cr=" + cr);
  if (!wan) {
    logStep("tr069 wan not found");
  } else if (alreadyOnTarget(layout, wan.path, TARGET_VLAN)) {
    logStep("skip already vlan=" + TARGET_VLAN + " wan=" + wan.path);
  } else {
    logStep("retag " + wan.path + " ip=" + wan.ip + " vlan=" + TARGET_VLAN);
    applyTr069Vlan(layout, wan.path, TARGET_VLAN);
    logStep("end");
  }
}
