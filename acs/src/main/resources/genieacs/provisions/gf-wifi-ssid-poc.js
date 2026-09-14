// Provision de lab: cambia SSID 2.4 y 5.8 con una sola passphrase para ambas bandas.
// Args: [ssid24, ssid5, passphrase]
//
// F6600R: 2.4 = WLANConfiguration.1, 5.8 = WLANConfiguration.5.
// VSOL:   2.4 = WLANConfiguration.5, 5.8 = WLANConfiguration.1.
// KeyPassphrase es el leaf con valor. PreSharedKey.1 queda vacío y no se escribe.
// Passphrase < 8: la ONU la rechaza; no se escribe ninguna banda.
// No toca WAN ni ACS.

const LAYOUTS = {
  F6600R: {
    wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
    wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
  },
  V2804AX15T: {
    wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
    wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
  },
};

LAYOUTS.VSOLVA74 = LAYOUTS.V2804AX15T;

const MIN_PASSPHRASE = 8;

function logStep(message) {
  log("gf-wifi-ssid-poc " + message);
}

function productClassOf() {
  const declared = declare("DeviceID.ProductClass", { value: 1 });
  return declared && declared.value ? declared.value[0] : "";
}

function layoutOf(productClass) {
  return LAYOUTS[productClass] || null;
}

function setLeaf(path, value) {
  declare(path, null, { value: value });
}

function setWifiBand(wlanPath, ssid, passphrase) {
  if (!ssid) {
    return false;
  }
  setLeaf(wlanPath + ".SSID", ssid);
  setLeaf(wlanPath + ".KeyPassphrase", passphrase);
  setLeaf(wlanPath + ".Enable", true);
  return true;
}

function setWifi(layout, ssid24, ssid5, passphrase) {
  logStep("set wifi");
  const wrote24 = setWifiBand(layout.wlan24, ssid24, passphrase);
  const wrote5 = setWifiBand(layout.wlan5, ssid5, passphrase);
  if (wrote24 || wrote5) {
    commit();
  }
}

function applyWifi() {
  const productClass = productClassOf();
  const layout = layoutOf(productClass);
  if (!layout) {
    logStep("unsupported productClass=" + productClass);
    return;
  }
  const ssid24 = args[0];
  const ssid5 = args[1];
  const passphrase = args[2];
  if (!passphrase || String(passphrase).length < MIN_PASSPHRASE) {
    logStep("skip wifi passphrase shorter than 8");
    return;
  }
  logStep("start ssid24=" + ssid24 + " ssid5=" + ssid5 + " product=" + productClass);
  setWifi(layout, ssid24, ssid5, passphrase);
  logStep("end");
}

applyWifi();
