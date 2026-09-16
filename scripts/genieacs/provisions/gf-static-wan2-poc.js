const WAN_DEVICE = "InternetGatewayDevice.WANDevice.1";
const WIFI_PASSPHRASE = args[7];

const VSOL_LAYOUT = {
  pppPath: WAN_DEVICE + ".WANConnectionDevice.2.WANPPPConnection.1",
  ipPath: WAN_DEVICE + ".WANConnectionDevice.2.WANIPConnection.1",
  ipParent: WAN_DEVICE + ".WANConnectionDevice.2.WANIPConnection",
  ipInvalidAs: 0,
  wcdPath: WAN_DEVICE + ".WANConnectionDevice.2",
  wcdParent: WAN_DEVICE + ".WANConnectionDevice",
  writeAlias: true,
  writeCtCom: true,
  writeZte: false,
  extraIpPaths: [],
  gponVlanPath: WAN_DEVICE + ".WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark",
  wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
  wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
};

const F6600_LAYOUT = {
  pppPath: WAN_DEVICE + ".WANConnectionDevice.1.WANPPPConnection.2",
  ipPath: WAN_DEVICE + ".WANConnectionDevice.1.WANIPConnection.2",
  extraIpPaths: [WAN_DEVICE + ".WANConnectionDevice.1.WANIPConnection.3"],
  ipParent: WAN_DEVICE + ".WANConnectionDevice.1.WANIPConnection",
  ipInvalidAs: 1,
  wcdPath: null,
  wcdParent: null,
  writeAlias: false,
  writeCtCom: false,
  writeZte: true,
  gponVlanPath: null,
  wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
  wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
};

const LAYOUTS = {
  F6600R: F6600_LAYOUT,
  V2804AX15T: VSOL_LAYOUT,
  VSOLVA74: VSOL_LAYOUT,
};

function logStep(message) {
  log("gf-static-wan2-poc " + message);
}

function productClassOf() {
  const declared = declare("DeviceID.ProductClass", { value: 1 });
  return declared && declared.value ? declared.value[0] : "";
}

function layoutOf(productClass) {
  return LAYOUTS[productClass] || null;
}

function pathExists(path) {
  const declared = declare(path, { path: Date.now() });
  return !!(declared && declared.path);
}

function wildcardSize(wildcard) {
  const listed = declare(wildcard, { path: Date.now() });
  return listed ? listed.size : undefined;
}

function nextCount(size, invalidAs) {
  const n = Number(size);
  const current = Number.isFinite(n) && n > 0 ? n : invalidAs;
  return current + 1;
}

function addUntilCount(wildcard, desired) {
  declare(wildcard, { path: Date.now() }, { path: desired });
  commit();
}

function deleteObject(path) {
  declare(path, null, { path: 0 });
  commit();
}

function setLeaf(path, value) {
  declare(path, null, { value: value });
}

function ensureInternetWcd(layout) {
  if (!layout.wcdPath) {
    return;
  }
  if (pathExists(layout.wcdPath)) {
    logStep(layout.wcdPath + " exists, skip add");
    return;
  }
  const wildcard = layout.wcdParent + ".*";
  const size = wildcardSize(wildcard);
  const desired = nextCount(size, 1);
  logStep("add " + wildcard + " count=" + size + " -> " + desired);
  addUntilCount(wildcard, desired);
}

function deleteInternetPppWan(layout) {
  if (!pathExists(layout.pppPath)) {
    return;
  }
  logStep("delete " + layout.pppPath);
  deleteObject(layout.pppPath);
}

function deleteExtraInternetIpWans(layout) {
  const paths = layout.extraIpPaths || [];
  for (let i = 0; i < paths.length; i++) {
    const path = paths[i];
    if (!pathExists(path)) {
      continue;
    }
    logStep("delete " + path);
    deleteObject(path);
  }
}

function ensureInternetIp(layout) {
  if (pathExists(layout.ipPath)) {
    logStep(layout.ipPath + " exists, skip add");
    return;
  }
  const wildcard = layout.ipParent + ".*";
  const size = wildcardSize(wildcard);
  const desired = nextCount(size, layout.ipInvalidAs);
  logStep("add " + wildcard + " count=" + size + " -> " + desired);
  addUntilCount(wildcard, desired);
}

function setInternetIpLeaves(layout, cfg) {
  const ip = layout.ipPath;
  logStep("set params without Enable");
  setLeaf(ip + ".Name", cfg.connectionName);
  if (layout.writeAlias) {
    setLeaf(ip + ".Alias", cfg.connectionName);
  }
  setLeaf(ip + ".ConnectionType", "IP_Routed");
  if (layout.writeCtCom) {
    setLeaf(ip + ".X_CT-COM_ServiceList", "INTERNET");
    setLeaf(ip + ".X_CT-COM_VLANIDMark", cfg.vlanId);
  }
  if (layout.writeZte) {
    setLeaf(ip + ".X_ZTE-COM_ServiceList", "INTERNET");
    setLeaf(ip + ".X_ZTE-COM_VLANID", cfg.vlanId);
    setLeaf(ip + ".X_ZTE-COM_VLANEnable", true);
  }
  setLeaf(ip + ".NATEnabled", true);
  setLeaf(ip + ".AddressingType", "Static");
  setLeaf(ip + ".ExternalIPAddress", cfg.ip);
  if (cfg.subnetMask) {
    setLeaf(ip + ".SubnetMask", cfg.subnetMask);
  }
  if (cfg.gateway) {
    setLeaf(ip + ".DefaultGateway", cfg.gateway);
  }
  if (cfg.dns) {
    setLeaf(ip + ".DNSServers", cfg.dns);
    setLeaf(ip + ".DNSEnabled", true);
  }
  if (layout.gponVlanPath) {
    setLeaf(layout.gponVlanPath, cfg.vlanId);
  }
  commit();
}

function enableInternetIp(layout) {
  logStep("set Enable");
  setLeaf(layout.ipPath + ".Enable", true);
}

function wifiOf() {
  if (!args[6] && !args[8]) {
    return null;
  }
  return {
    ssid24: args[6],
    ssid5: args[8],
  };
}

function setWifiBand(wlanPath, ssid) {
  if (!ssid) {
    return false;
  }
  setLeaf(wlanPath + ".SSID", ssid);
  setLeaf(wlanPath + ".KeyPassphrase", WIFI_PASSPHRASE);
  setLeaf(wlanPath + ".Enable", true);
  return true;
}

function setWifi(layout, wifi) {
  if (!wifi) {
    return;
  }
  logStep("set wifi");
  const wrote24 = setWifiBand(layout.wlan24, wifi.ssid24);
  const wrote5 = setWifiBand(layout.wlan5, wifi.ssid5);
  if (wrote24 || wrote5) {
    commit();
  }
}

function applyInternetStatic() {
  const productClass = productClassOf();
  const layout = layoutOf(productClass);
  if (!layout) {
    logStep("unsupported productClass=" + productClass);
    return;
  }
  if (!args[0]) {
    logStep("missing static ip");
    return;
  }
  logStep("start ip=" + args[0] + " vlan=" + args[4] + " name=" + args[5] + " product=" + productClass);
  ensureInternetWcd(layout);
  deleteInternetPppWan(layout);
  deleteExtraInternetIpWans(layout);
  ensureInternetIp(layout);
  setInternetIpLeaves(layout, {
    ip: args[0],
    subnetMask: args[1],
    gateway: args[2],
    dns: args[3],
    vlanId: args[4],
    connectionName: args[5],
  });
  enableInternetIp(layout);
  setWifi(layout, wifiOf());
  logStep("end");
}

applyInternetStatic();
