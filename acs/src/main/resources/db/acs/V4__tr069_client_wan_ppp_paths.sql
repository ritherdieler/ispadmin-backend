UPDATE tr069_model_profile
SET client_wan_ppp_connection_path = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1'
WHERE product_class = 'V2804AX15T';

UPDATE tr069_model_profile
SET client_wan_ppp_connection_path = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2'
WHERE product_class = 'F6600R';
