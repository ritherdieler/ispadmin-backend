# Onboarding v2: perfiles ONU revisados el 2026-10-01

## HG8145X6

El flujo ACS v2 reconoce `HG8145X6` sin habilitarlo en el provisionador legado. La WAN TR-069 existente permanece en `WANConnectionDevice.1.WANIPConnection.1`; el servicio de Internet se crea en `WANConnectionDevice.2` y usa `X_HW_SERVICELIST=INTERNET`, `X_HW_VLAN`, NAT y `X_HW_LANBIND`. Las radios primarias son `WLANConfiguration.1` (2,4 GHz) y `.5` (5 GHz). El cleanup conserva las WAN ajenas y la compensación elimina únicamente la conexión marcada con el ID de operación. Si la WAN de gestión no declara `TR069` o el espacio de Internet ya está ocupado por otra configuración, el provision aborta.

Base: exportación GenieACS `00259E-HG8145X6` del 2026-10-01 y prueba anterior de WAN IP estática en `tr069-e2e-validacion-modelo.md` / `tr069-huawei-spv-aislado-l3.md`. La ruta PPPoE se cubre con pruebas simuladas de las hojas generadas, pero aún no se ha validado con un equipo real. Tampoco se ha ejecutado un alta completa contra ACS/OLT.

## TP-Link pendientes de árbol completo

- `54AF97-IGD` identifica por `HardwareVersion` un `XC220-G3v v1`. La exportación contiene las WLAN `.1` y `.2`, una WAN IP de Internet en WCD `.6` y la de gestión en `.7`, pero no expone los valores/rutas VLAN de Internet ni un objeto PPP en WCD `.6`.
- `5C628B-XC220-G3v` identifica un `XC220-G3v v2`. Contiene las WLAN `.1` y `.3`, una WAN IP de Internet en WCD `.4` y la de gestión en `.5`. La exportación no expone el objeto PPP ni los valores/rutas de VLAN de la WAN de Internet.
- `F0A731-XC220-G3` contiene solo `Device.ManagementServer` e IP. No contiene el árbol WAN/PPP ni las radios Wi-Fi.

Los índices WCD observados no son un contrato para toda la flota. No se añadieron estos modelos a la puerta de registro: hacerlo permitiría crear o sobrescribir una WAN sin verificar servicio, VLAN, titularidad ni capacidad PPP. Para completar el soporte, refrescar y exportar `InternetGatewayDevice.WANDevice.1.WANConnectionDevice.*` (incluidas `WANPPPConnection`, `WANEthernetLinkConfig.X_TP_VID`, `X_TP_WANPonLinkConfig.X_TP_VID`, `X_TP_ServiceType`) y `InternetGatewayDevice.LANDevice.1.WLANConfiguration.*` (`X_TP_Band`, `Enable`, `KeyPassphrase`). Para `XC220-G3` se necesita además el árbol TR-181 equivalente.

## Verificación

Pruebas de los scripts GenieACS: `node --test scripts/genieacs/provisions/test/*.test.js`. Pruebas ACS: `./gradlew :acs:test`. Ninguna prueba toca un equipo real.
