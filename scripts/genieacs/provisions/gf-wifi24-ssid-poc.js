const productClass = declare("DeviceID.ProductClass", { value: 1 }).value[0];
if (productClass !== "F6600R") {
  return;
}

const ssid24 = args[0];
const password24 = args[1];
const ssid5 = args[2];
const password5 = args[3];

const now = Date.now();
const wifi24 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1";
const wifi5 = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5";

declare(wifi24 + ".SSID", { value: now }, { value: ssid24 });
declare(wifi24 + ".KeyPassphrase", { value: now }, { value: password24 });
declare(wifi5 + ".SSID", { value: now }, { value: ssid5 });
declare(wifi5 + ".KeyPassphrase", { value: now }, { value: password5 });
