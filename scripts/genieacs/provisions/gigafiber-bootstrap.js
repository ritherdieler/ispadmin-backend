var acsUser = "gigafiber-acs";
var acsPass = "3mtc0MhiudthIQMnBiIsCF5D";
var acsUrl = "http://acs.gigafiberperu.cloud/";
var informInterval = 3600;
var now = Date.now();

clear("Device", now);
clear("InternetGatewayDevice", now);
declare("Tags", {value: now}, {value: ["gigafiber"]});

declare("InternetGatewayDevice.ManagementServer.Username", {value: now}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.URL", {value: now}, {value: acsUrl});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {value: now}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.PeriodicInformEnable", {value: now}, {value: true});
declare("InternetGatewayDevice.ManagementServer.PeriodicInformInterval", {value: now}, {value: informInterval});
declare("InternetGatewayDevice.ManagementServer.Password", null, {value: acsPass});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", null, {value: acsPass});

declare("Device.ManagementServer.Username", {value: now}, {value: acsUser});
declare("Device.ManagementServer.URL", {value: now}, {value: acsUrl});
declare("Device.ManagementServer.ConnectionRequestUsername", {value: now}, {value: acsUser});
declare("Device.ManagementServer.PeriodicInformEnable", {value: now}, {value: true});
declare("Device.ManagementServer.PeriodicInformInterval", {value: now}, {value: informInterval});
declare("Device.ManagementServer.Password", null, {value: acsPass});
declare("Device.ManagementServer.ConnectionRequestPassword", null, {value: acsPass});
