var GfVparams = (function () {
  var CR_URL = "InternetGatewayDevice.ManagementServer.ConnectionRequestURL";
  var MODELS = {
    V2804AX15T: {
      pppPath: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1",
      ipPath: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1",
      mgmtPrefixes: ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1."],
      wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
      wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
      writeAlias: true,
      gponVlanPath: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark",
    },
    F6600R: {
      pppPath: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2",
      ipPath: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.2",
      mgmtPrefixes: ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1"],
      wlan24: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1",
      wlan5: "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5",
      writeAlias: false,
      gponVlanPath: null,
    },
  };

  function productClassOf(deviceId, declare) {
    var pc = deviceId && (deviceId._ProductClass || deviceId.ProductClass);
    if (pc) return String(pc).trim();
    if (typeof declare === "function") {
      var declared = readValue(declare, "DeviceID.ProductClass");
      if (declared) return String(declared).trim();
    }
    return "";
  }

  function layoutOf(deviceId, declare) {
    var pc = productClassOf(deviceId, declare);
    var layout = MODELS[pc];
    if (!layout) {
      throw new Error("Unsupported product class: " + (pc || "unknown"));
    }
    return { productClass: pc, layout: layout };
  }

  function describeArgs(raw) {
    if (raw == null) return String(raw);
    if (typeof raw === "string") return "string(len=" + raw.length + ",head=" + raw.charAt(0) + ")";
    if (Array.isArray(raw)) {
      var parts = [];
      for (var i = 0; i < Math.min(raw.length, 4); i++) {
        parts.push(i + "=" + describeArgs(raw[i]));
      }
      return "array(len=" + raw.length + "," + parts.join(",") + ")";
    }
    if (typeof raw === "object") {
      var keys = Object.keys(raw).slice(0, 8);
      var extra = "";
      if (Object.prototype.hasOwnProperty.call(raw, "value")) {
        extra = ",value=" + describeArgs(raw.value);
      }
      return "object(keys=" + keys.join(",") + extra + ")";
    }
    return typeof raw;
  }

  function invalidPayload(args) {
    throw new Error("Invalid JSON payload (" + describeArgs(args) + ")");
  }

  function unwrapSetValue(raw) {
    var guard = 0;
    while (guard < 4) {
      if (Array.isArray(raw)) {
        raw = raw[0];
        guard++;
        continue;
      }
      if (raw && typeof raw === "object" && Object.prototype.hasOwnProperty.call(raw, "value")) {
        raw = Array.isArray(raw.value) ? raw.value[0] : raw.value;
        guard++;
        continue;
      }
      break;
    }
    return raw;
  }

  function parseJsonObject(raw) {
    var current = raw;
    var guard = 0;
    while (typeof current === "string" && guard < 4) {
      var trimmed = String(current).trim();
      if (!trimmed) {
        return null;
      }
      try {
        current = JSON.parse(trimmed);
      } catch (e) {
        return null;
      }
      guard++;
    }
    if (current && typeof current === "object" && !Array.isArray(current)) {
      return current;
    }
    return null;
  }

  function isPayloadObject(raw) {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return false;
    return (
      Object.prototype.hasOwnProperty.call(raw, "username") ||
      Object.prototype.hasOwnProperty.call(raw, "password") ||
      Object.prototype.hasOwnProperty.call(raw, "ip") ||
      Object.prototype.hasOwnProperty.call(raw, "ssid24") ||
      Object.prototype.hasOwnProperty.call(raw, "requested")
    );
  }

  function consider(raw) {
    raw = unwrapSetValue(raw);
    if (isPayloadObject(raw)) return raw;
    if (typeof raw !== "string") {
      if (raw == null) return null;
      raw = String(raw);
    }
    var parsed = parseJsonObject(raw);
    if (isPayloadObject(parsed)) return parsed;
    return null;
  }

  function readSetJson(args) {
    if (args == null || args === "") {
      return null;
    }
    var got;
    if (typeof args === "string") {
      got = consider(args);
      if (got) return got;
      invalidPayload(args);
    }
    if (Array.isArray(args)) {
      if (args.length === 0) {
        return null;
      }
      var i;
      for (i = 0; i < args.length; i++) {
        got = consider(args[i]);
        if (got) return got;
      }
      got = consider(args);
      if (got) return got;
      if (args[1] && args[1].value != null) {
        invalidPayload(args);
      }
      return null;
    }
    got = consider(args);
    if (got) return got;
    return null;
  }

  function readValue(declare, path) {
    var result = declare(path, { value: 1 });
    if (!result || result.value == null) return null;
    return Array.isArray(result.value) ? result.value[0] : result.value;
  }

  function managementHost(url) {
    if (url == null) return null;
    var raw = String(url).trim();
    if (!raw) return null;
    var match = raw.match(/(\d{1,3}(?:\.\d{1,3}){3})/);
    return match ? match[1] : null;
  }

  function isManagementHost(host) {
    if (!host) return false;
    var parts = String(host).split(".");
    if (parts.length !== 4) return false;
    if (String(host).indexOf("10.20.") === 0) {
      var third20 = Number(String(host).split(".")[2]);
      return third20 >= 0 && third20 <= 3;
    }
    if (parts[0] === "192" && parts[1] === "168") {
      var third168 = Number(parts[2]);
      return third168 >= 252 && third168 <= 255;
    }
    return false;
  }

  function wanInstanceOf(path) {
    var match = String(path).match(
      /^(InternetGatewayDevice\.WANDevice\.\d+\.WANConnectionDevice\.\d+\.WAN(?:IP|PPP)Connection\.\d+)/,
    );
    return match ? match[1] : null;
  }

  function startsWithPrefix(path, prefix) {
    if (path === prefix) return true;
    if (prefix.charAt(prefix.length - 1) === ".") return path.indexOf(prefix) === 0;
    return path === prefix || path.indexOf(prefix + ".") === 0;
  }

  function assertWritableInternetWan(layout, path, declare, crUrl) {
    var i;
    for (i = 0; i < layout.mgmtPrefixes.length; i++) {
      if (startsWithPrefix(path, layout.mgmtPrefixes[i])) {
        throw new Error("Refusing to write management WAN " + path);
      }
    }
    var host = managementHost(crUrl);
    if (!host || !isManagementHost(host)) return;
    var wanBase = wanInstanceOf(path);
    if (!wanBase) return;
    var ip = readValue(declare, wanBase + ".ExternalIPAddress");
    if (ip && String(ip) === host) {
      throw new Error("Refusing to write management WAN " + wanBase);
    }
  }

  function pppParentPath(pppPath) {
    return String(pppPath).replace(/\.\d+$/, ".");
  }

  function pppInstanceCount(pppPath) {
    var match = String(pppPath).match(/\.(\d+)$/);
    return match ? Number(match[1]) : 1;
  }

  function addObject(declare, objectPath, count) {
    var path = String(objectPath).replace(/\.+$/, "") + ".*";
    declare(path, {path: Date.now()}, {path: count});
  }

  function ensureInternetPpp(declare, layout) {
    addObject(declare, pppParentPath(layout.pppPath), pppInstanceCount(layout.pppPath));
  }

  function spv(declare, path, value) {
    declare(path, { value: Date.now() }, { value: value });
  }

  function applyVlan(declare, layout, wanPath, vlanId) {
    var vlan = vlanId == null ? 100 : vlanId;
    spv(declare, wanPath + ".X_CT-COM_VLANIDMark", vlan);
    spv(declare, wanPath + ".X_ZTE-COM_VLANID", vlan);
    spv(declare, wanPath + ".X_ZTE-COM_VLANEnable", 1);
    if (layout.gponVlanPath) {
      spv(declare, layout.gponVlanPath, vlan);
    }
  }

  function applyPppCommon(declare, layout, wanPath, payload) {
    spv(declare, wanPath + ".Enable", true);
    spv(declare, wanPath + ".ConnectionType", "IP_Routed");
    if (payload.connectionName) {
      spv(declare, wanPath + ".Name", payload.connectionName);
      if (layout.writeAlias) {
        spv(declare, wanPath + ".Alias", payload.connectionName);
      }
    }
    spv(declare, wanPath + ".ConnectionTrigger", "AlwaysOn");
    spv(declare, wanPath + ".X_CT-COM_ServiceList", "INTERNET");
    spv(declare, wanPath + ".X_ZTE-COM_ServiceList", "INTERNET");
    spv(declare, wanPath + ".NATEnabled", true);
    applyVlan(declare, layout, wanPath, payload.vlanId);
  }

  function connectionRequestUrl(declare) {
    return readValue(declare, CR_URL) || readValue(declare, "Device.ManagementServer.ConnectionRequestURL");
  }

  function handleApplyInternetPppoe(deviceId, declare, args) {
    var resolved = layoutOf(deviceId, declare);
    var layout = resolved.layout;
    var payload = readSetJson(args);
    if (!payload) {
      return { writable: true, value: ["", "xsd:string"] };
    }
    if (!payload.username || !payload.password) {
      throw new Error("PPPoE credentials required");
    }
    var crUrl = connectionRequestUrl(declare);
    assertWritableInternetWan(layout, layout.pppPath, declare, crUrl);
    assertWritableInternetWan(layout, layout.ipPath, declare, crUrl);
    ensureInternetPpp(declare, layout);
    applyPppCommon(declare, layout, layout.pppPath, payload);
    spv(declare, layout.pppPath + ".Username", payload.username);
    spv(declare, layout.pppPath + ".Password", payload.password);
    spv(declare, layout.ipPath + ".Enable", false);
    return { writable: true, value: [JSON.stringify({ ok: true }), "xsd:string"] };
  }

  function handleApplyInternetStatic(deviceId, declare, args) {
    var resolved = layoutOf(deviceId, declare);
    var layout = resolved.layout;
    var payload = readSetJson(args);
    if (!payload) {
      return { writable: true, value: ["", "xsd:string"] };
    }
    if (!payload.ip) {
      throw new Error("Static IP required");
    }
    var crUrl = connectionRequestUrl(declare);
    assertWritableInternetWan(layout, layout.ipPath, declare, crUrl);
    var wanPath = layout.ipPath;
    spv(declare, wanPath + ".Enable", true);
    spv(declare, wanPath + ".ConnectionType", "IP_Routed");
    if (payload.connectionName) {
      spv(declare, wanPath + ".Name", payload.connectionName);
      if (layout.writeAlias) {
        spv(declare, wanPath + ".Alias", payload.connectionName);
      }
    }
    spv(declare, wanPath + ".X_CT-COM_ServiceList", "INTERNET");
    spv(declare, wanPath + ".X_ZTE-COM_ServiceList", "INTERNET");
    spv(declare, wanPath + ".NATEnabled", true);
    spv(declare, wanPath + ".AddressingType", "Static");
    spv(declare, wanPath + ".ExternalIPAddress", payload.ip);
    if (payload.subnetMask) spv(declare, wanPath + ".SubnetMask", payload.subnetMask);
    if (payload.gateway) spv(declare, wanPath + ".DefaultGateway", payload.gateway);
    if (payload.dns) {
      spv(declare, wanPath + ".DNSServers", payload.dns);
      spv(declare, wanPath + ".DNSEnabled", true);
    }
    applyVlan(declare, layout, wanPath, payload.vlanId);
    return { writable: true, value: [JSON.stringify({ ok: true }), "xsd:string"] };
  }

  function handleSetWifi(deviceId, declare, args) {
    var resolved = layoutOf(deviceId, declare);
    var layout = resolved.layout;
    var payload = readSetJson(args);
    if (!payload) {
      return { writable: true, value: ["", "xsd:string"] };
    }
    if (payload.ssid24) spv(declare, layout.wlan24 + ".SSID", payload.ssid24);
    if (payload.password24) spv(declare, layout.wlan24 + ".KeyPassphrase", payload.password24);
    if (payload.ssid5) spv(declare, layout.wlan5 + ".SSID", payload.ssid5);
    if (payload.password5) spv(declare, layout.wlan5 + ".KeyPassphrase", payload.password5);
    return { writable: true, value: [JSON.stringify({ ok: true }), "xsd:string"] };
  }

  function refresh(declare, path) {
    return readValue(declare, path);
  }

  function handleInternetStatus(deviceId, declare, args) {
    var resolved = layoutOf(deviceId, declare);
    var layout = resolved.layout;
    var status = refresh(declare, layout.pppPath + ".ConnectionStatus");
    if (status == null) {
      status = refresh(declare, layout.ipPath + ".ConnectionStatus");
    }
    var ip = refresh(declare, layout.pppPath + ".ExternalIPAddress");
    if (ip == null || ip === "") {
      ip = refresh(declare, layout.ipPath + ".ExternalIPAddress");
    }
    var connected = String(status || "").toLowerCase() === "connected";
    return {
      writable: false,
      value: [
        JSON.stringify({
          connected: connected,
          ip: ip || null,
          productClass: resolved.productClass,
        }),
        "xsd:string",
      ],
    };
  }

  function handleWifiStatus(deviceId, declare, args) {
    var resolved = layoutOf(deviceId, declare);
    var layout = resolved.layout;
    return {
      writable: false,
      value: [
        JSON.stringify({
          ssid24: refresh(declare, layout.wlan24 + ".SSID"),
          ssid5: refresh(declare, layout.wlan5 + ".SSID"),
        }),
        "xsd:string",
      ],
    };
  }

  function handleReboot(_deviceId, declare, args) {
    var payload = readSetJson(args);
    if (!payload) {
      return { writable: true, value: ["ok", "xsd:string"] };
    }
    declare("Reboot", null, { value: Date.now() });
    return { writable: true, value: ["ok", "xsd:string"] };
  }

  return {
    handleApplyInternetPppoe: handleApplyInternetPppoe,
    handleApplyInternetStatic: handleApplyInternetStatic,
    handleSetWifi: handleSetWifi,
    handleInternetStatus: handleInternetStatus,
    handleWifiStatus: handleWifiStatus,
    handleReboot: handleReboot,
  };
})();

if (typeof module !== "undefined" && module.exports) {
  module.exports = GfVparams;
}
