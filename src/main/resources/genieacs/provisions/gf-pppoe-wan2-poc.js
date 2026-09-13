// Provision de lab: sustituye la WAN IP de internet por PPPoE y, si vienen args, el WiFi.
// Args: [username, password, vlanId, connectionName, ssid24, pass24, ssid5, pass5]
// pass5 se ignora. La passphrase WiFi de ambas bandas es pass24.
//
// commit() de GenieACS vuelve a ejecutar este archivo desde la línea 1.
// Cada paso mira el data model y solo emite la acción CWMP que falta
// (AddObject / DeleteObject / SetParameterValues).
// Nunca escribir la WAN de gestión ACS:
//   F6600R → WANConnectionDevice.1.WANIPConnection.1
//   VSOL   → WANConnectionDevice.1.* (lab VLAN 1000)
// WiFi en otro SPV que la WAN: un batch grande cortó la sesión F6600R.

const WAN_DEVICE = "InternetGatewayDevice.WANDevice.1";
const WIFI_PASSPHRASE = args[5];

// Internet en WCD.2. ACS permanece en WCD.1. Hojas de vendor: CT-COM, no ZTE.
// 2.4 es WLAN.5 y 5.8 es WLAN.1. Al revés que F6600R.
const VSOL_LAYOUT = {
  pppPath: WAN_DEVICE + ".WANConnectionDevice.2.WANPPPConnection.1",
  ipPath: WAN_DEVICE + ".WANConnectionDevice.2.WANIPConnection.1",
  pppParent: WAN_DEVICE + ".WANConnectionDevice.2.WANPPPConnection",
  wcdPath: WAN_DEVICE + ".WANConnectionDevice.2",
  wcdParent: WAN_DEVICE + ".WANConnectionDevice",
  writeAlias: true,
  writeCtCom: true,
  writeZte: false,
  gponVlanPath: WAN_DEVICE + ".WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark",
  wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
  wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
};

// Internet en WCD.1 PPP.2 / IP.2. ACS es WANIPConnection.1 del mismo WCD.
// 2.4 es WLAN.1 y 5.8 es WLAN.5.
const F6600_LAYOUT = {
  pppPath: WAN_DEVICE + ".WANConnectionDevice.1.WANPPPConnection.2",
  ipPath: WAN_DEVICE + ".WANConnectionDevice.1.WANIPConnection.2",
  pppParent: WAN_DEVICE + ".WANConnectionDevice.1.WANPPPConnection",
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
  log("gf-pppoe-wan2-poc " + message);
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

// AddObject: instancias actuales + 1. Si size no es válido usa invalidAs
// (WCD: 1 porque existe ACS WCD.1; PPP: 0 para crear la primera instancia).
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

// VSOL: no se puede crear PPP bajo un WCD.2 inexistente (GPN 9005, HTTP 200 sin write).
// {path: 1} en WANConnectionDevice.* borraría extras y puede tumbar el ACS en WCD.1.
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

function deleteInternetIpWan(layout) {
  if (!pathExists(layout.ipPath)) {
    return;
  }
  logStep("delete " + layout.ipPath);
  deleteObject(layout.ipPath);
}

function ensureInternetPpp(layout) {
  if (pathExists(layout.pppPath)) {
    logStep(layout.pppPath + " exists, skip add");
    return;
  }
  const wildcard = layout.pppParent + ".*";
  const size = wildcardSize(wildcard);
  const desired = nextCount(size, 0);
  logStep("add " + wildcard + " count=" + size + " -> " + desired);
  addUntilCount(wildcard, desired);
}

// Primero las hojas, Enable al final. Un SPV con Enable incluido cortó la sesión F6600R.
function setInternetPppLeaves(layout, creds) {
  const ppp = layout.pppPath;
  logStep("set params without Enable");
  setLeaf(ppp + ".Name", creds.connectionName);
  if (layout.writeAlias) {
    setLeaf(ppp + ".Alias", creds.connectionName);
  }
  setLeaf(ppp + ".ConnectionType", "IP_Routed");
  setLeaf(ppp + ".ConnectionTrigger", "AlwaysOn");
  if (layout.writeCtCom) {
    setLeaf(ppp + ".X_CT-COM_ServiceList", "INTERNET");
    setLeaf(ppp + ".X_CT-COM_VLANIDMark", creds.vlanId);
  }
  if (layout.writeZte) {
    setLeaf(ppp + ".X_ZTE-COM_ServiceList", "INTERNET");
    setLeaf(ppp + ".X_ZTE-COM_VLANID", creds.vlanId);
    setLeaf(ppp + ".X_ZTE-COM_VLANEnable", true);
  }
  setLeaf(ppp + ".NATEnabled", true);
  if (layout.gponVlanPath) {
    setLeaf(layout.gponVlanPath, creds.vlanId);
  }
  setLeaf(ppp + ".Username", creds.username);
  setLeaf(ppp + ".Password", creds.password);
  commit();
}

function enableInternetPpp(layout) {
  logStep("set Enable");
  setLeaf(layout.pppPath + ".Enable", true);
}

function wifiOf() {
  if (!args[4] && !args[6]) {
    return null;
  }
  return {
    ssid24: args[4],
    ssid5: args[6],
  };
}

// KeyPassphrase es el leaf que la ONU tiene con valor. PreSharedKey.1 queda vacío y no se escribe.
// La passphrase de ambas bandas es pass24 (args[5]). pass5 no se escribe.
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

function applyInternetPppoe() {
  const productClass = productClassOf();
  const layout = layoutOf(productClass);
  if (!layout) {
    logStep("unsupported productClass=" + productClass);
    return;
  }
  logStep("start user=" + args[0] + " vlan=" + args[2] + " name=" + args[3] + " product=" + productClass);
  // Orden: WCD de internet (VSOL), borrar WAN IP, crear PPP, hojas, Enable.
  ensureInternetWcd(layout);
  deleteInternetIpWan(layout);
  ensureInternetPpp(layout);
  setInternetPppLeaves(layout, {
    username: args[0],
    password: args[1],
    vlanId: args[2],
    connectionName: args[3],
  });
  enableInternetPpp(layout);
  setWifi(layout, wifiOf());
  logStep("end");
}

applyInternetPppoe();
