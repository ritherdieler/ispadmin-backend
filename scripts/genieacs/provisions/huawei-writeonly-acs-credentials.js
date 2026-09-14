var acsUser = "gigafiber-acs";
var acsPass = "3mtc0MhiudthIQMnBiIsCF5D";

declare("InternetGatewayDevice.ManagementServer.Password", {value: 1}, {value: acsPass});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestPassword", {value: 1}, {value: acsPass});
declare("Device.ManagementServer.Password", {value: 1}, {value: acsPass});
declare("Device.ManagementServer.ConnectionRequestPassword", {value: 1}, {value: acsPass});

declare("InternetGatewayDevice.ManagementServer.Username", {value: 1}, {value: acsUser});
declare("InternetGatewayDevice.ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
declare("Device.ManagementServer.Username", {value: 1}, {value: acsUser});
declare("Device.ManagementServer.ConnectionRequestUsername", {value: 1}, {value: acsUser});
