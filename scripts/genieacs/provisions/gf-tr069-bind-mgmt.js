const ACS_IP = args && args[0] != null ? String(args[0]) : "";
const WAN_DEVICE = "InternetGatewayDevice.WANDevice.1";
const CR_URL = "InternetGatewayDevice.ManagementServer.ConnectionRequestURL";
const FORWARDING = "InternetGatewayDevice.Layer3Forwarding.Forwarding";

const TP_LAYOUT = { writeTpServiceType: true, writePonVlan: true };
const IGD_LAYOUT = { writeTpServiceType: false, writePonVlan: false };

const LAYOUTS = {
  IGD: IGD_LAYOUT,
  "XC220-G3v": TP_LAYOUT,
  "XC220-G3": TP_LAYOUT,
};

function logStep(message) {
  log("gf-tr069-bind-mgmt " + message);
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

function isMgmtIp(ip) {
  return inCidr(ip, "10.20.0.0", 22);
}

function crHost(url) {
  const match = String(url || "").match(/^https?:\/\/\[?([0-9a-fA-F.:]+)\]?:/);
  return match ? match[1] : "";
}

function wcdPathOf(wanPath) {
  return String(wanPath).replace(/\.WAN(?:IP|PPP)Connection\.\d+$/, "");
}

function vlanLeaves(layout, wanPath) {
  const wcd = wcdPathOf(wanPath);
  const leaves = [wcd + ".WANEthernetLinkConfig.X_TP_VID"];
  if (layout.writePonVlan) {
    leaves.push(wcd + ".X_TP_WANPonLinkConfig.X_TP_VID");
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

function collectFromParent(wans, wildcard) {
  const conns = instancePaths(wildcard);
  for (let i = 0; i < conns.length; i++) {
    const wanPath = conns[i];
    const ip = readValue(wanPath + ".ExternalIPAddress");
    if (!ip || ip === "0.0.0.0") continue;
    wans.push({
      path: wanPath,
      ip: ip,
      gateway: readValue(wanPath + ".DefaultGateway"),
      wcd: wcdPathOf(wanPath),
    });
  }
}

function collectWans() {
  const wans = [];
  const wcds = instancePaths(WAN_DEVICE + ".WANConnectionDevice.*");
  for (let w = 0; w < wcds.length; w++) {
    collectFromParent(wans, wcds[w] + ".WANIPConnection.*");
  }
  return wans;
}

function findMgmtWan(wans) {
  for (let i = 0; i < wans.length; i++) {
    if (isMgmtIp(wans[i].ip)) return wans[i];
  }
  return null;
}

function currentVlan(path) {
  const n = Number(readValue(path));
  return Number.isFinite(n) ? n : null;
}

function ensureMgmtVlan(layout, wan) {
  const leaves = vlanLeaves(layout, wan.path);
  let wrote = false;
  for (let i = 0; i < leaves.length; i++) {
    if (currentVlan(leaves[i]) === 1000) continue;
    declare(leaves[i], null, { value: 1000 });
    wrote = true;
  }
  if (wrote) commit();
}

function ensureMgmtServiceType(layout, wan) {
  if (!layout.writeTpServiceType) return;
  const leaf = wan.wcd + ".X_TP_ServiceType";
  if (readValue(leaf) === "TR069") return;
  declare(leaf, null, { value: "TR069" });
  commit();
}

function routeBoundToWan(routePath, wan) {
  const iface = readValue(routePath + ".Interface");
  const gw = readValue(routePath + ".GatewayIPAddress");
  if (gw && gw === wan.gateway) return true;
  if (iface && iface.indexOf(wan.path) === 0) return true;
  if (iface && iface.indexOf(wan.wcd) === 0) return true;
  return false;
}

function routeLooksInternet(routePath, wan) {
  const iface = readValue(routePath + ".Interface");
  const gw = readValue(routePath + ".GatewayIPAddress");
  if (routeBoundToWan(routePath, wan)) return false;
  if (iface || gw) return true;
  return false;
}

function findForwarding(acsIp, wan) {
  const paths = instancePaths(FORWARDING + ".*");
  let empty = "";
  let mgmtEmpty = "";
  for (let i = 0; i < paths.length; i++) {
    const dest = readValue(paths[i] + ".DestIPAddress");
    if (dest === acsIp && routeBoundToWan(paths[i], wan)) return paths[i];
    if (dest === acsIp && routeLooksInternet(paths[i], wan)) {
      continue;
    }
    if (!dest && routeBoundToWan(paths[i], wan) && !mgmtEmpty) mgmtEmpty = paths[i];
    if (!dest && !ifaceOrGw(paths[i]) && !empty) empty = paths[i];
  }
  return mgmtEmpty || empty;
}

function ifaceOrGw(routePath) {
  return !!(readValue(routePath + ".Interface") || readValue(routePath + ".GatewayIPAddress"));
}

function addForwardingInstance() {
  const wildcard = FORWARDING + ".*";
  const listed = declare(wildcard, { path: 1 });
  const size = Number(listed && listed.size) || 0;
  declare(wildcard, { path: 1 }, { path: size + 1 });
  commit();
}

function setLeafIfChanged(path, value) {
  if (readValue(path) === String(value)) return false;
  declare(path, null, { value: value });
  return true;
}

function writeRouteLeaves(routePath, wan, acsIp) {
  let wrote = false;
  if (setLeafIfChanged(routePath + ".DestIPAddress", acsIp)) wrote = true;
  if (setLeafIfChanged(routePath + ".DestSubnetMask", "255.255.255.255")) wrote = true;
  if (setLeafIfChanged(routePath + ".GatewayIPAddress", wan.gateway || "10.20.0.1")) wrote = true;
  if (setLeafIfChanged(routePath + ".Interface", wan.path)) wrote = true;
  if (setLeafIfChanged(routePath + ".Type", "Network")) wrote = true;
  if (setLeafIfChanged(routePath + ".Enable", true)) wrote = true;
  if (wrote) commit();
}

function clearAcsDestFromInternetSlots(wan, acsIp) {
  const paths = instancePaths(FORWARDING + ".*");
  let wrote = false;
  for (let i = 0; i < paths.length; i++) {
    if (readValue(paths[i] + ".DestIPAddress") !== acsIp) continue;
    if (!routeLooksInternet(paths[i], wan)) continue;
    declare(paths[i] + ".DestIPAddress", null, { value: "" });
    wrote = true;
  }
  if (wrote) commit();
}

function ensureAcsHostRoute(wan, acsIp) {
  if (!acsIp) return;
  clearAcsDestFromInternetSlots(wan, acsIp);
  let routePath = findForwarding(acsIp, wan);
  if (!routePath) {
    addForwardingInstance();
    routePath = findForwarding(acsIp, wan);
  }
  if (!routePath) return;
  writeRouteLeaves(routePath, wan, acsIp);
}

logStep("start");
const productClass = productClassOf();
const layout = layoutOf(productClass);
if (!layout) {
  logStep("unsupported productClass=" + productClass);
} else {
  const cr = readValue(CR_URL);
  const wans = collectWans();
  const wan = findMgmtWan(wans);
  logStep("wans=" + wans.length + " cr=" + cr);
  if (!wan) {
    logStep("mgmt wan not found");
  } else {
    ensureMgmtVlan(layout, wan);
    ensureMgmtServiceType(layout, wan);
    if (isMgmtIp(crHost(cr))) {
      logStep("skip route cr already mgmt");
    } else {
      ensureAcsHostRoute(wan, ACS_IP);
    }
    logStep("end wan=" + wan.path + " ip=" + wan.ip);
  }
}
