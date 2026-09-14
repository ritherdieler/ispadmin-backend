var acsUser = "gigafiber-acs";
var acsPass = "3mtc0MhiudthIQMnBiIsCF5D";
var acsUrl = "http://acs.gigafiberperu.cloud/";

declare("InternetGatewayDevice.ManagementServer.Username", {value: 1}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.URL", {value: 1}, {value: acsUrl});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.Password", null, {value: acsPass});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", null, {value: acsPass});

declare("Device.ManagementServer.Username", {value: 1}, {value: acsUser});
declare("Device.ManagementServer.URL", {value: 1}, {value: acsUrl});
declare("Device.ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
declare("Device.ManagementServer.Password", null, {value: acsPass});
declare("Device.ManagementServer.ConnectionRequestPassword", null, {value: acsPass});
