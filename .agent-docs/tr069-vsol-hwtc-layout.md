# Layout TR-069 de VSOL y HWTC

## Evidencia del GenieACS legacy

Consulta de solo lectura al NBI local del VPS legacy (`127.0.0.1:7557`) sobre dispositivos de producción:

- `VSOL` y `HWTC` reportan `ProductClass=V2804AX15T`.
- Ambos exponen el mismo árbol relevante para WAN y Wi-Fi.
- La selección del layout de provisión se hace únicamente por `productClass`; `manufacturer` y `OUI` no participan.

## Layout `V2804AX15T`

| Función | Parámetro TR-069 |
|---|---|
| WAN de gestión ACS | `InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1` |
| Internet PPPoE | `InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1` |
| Internet IP alternativa | `InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1` |
| VLAN Internet | `...WANPPPConnection.1.X_CT-COM_VLANIDMark` |
| VLAN GPON asociada | `...WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark` |
| Wi-Fi 2.4 GHz | `InternetGatewayDevice.LANDevice.1.WLANConfiguration.5` |
| Wi-Fi 5 GHz | `InternetGatewayDevice.LANDevice.1.WLANConfiguration.1` |
| SSID | `WLANConfiguration.{1|5}.SSID` |
| Clave Wi-Fi | `WLANConfiguration.{1|5}.KeyPassphrase` |

Las hojas Wi-Fi usadas para configuración son `SSID`, `KeyPassphrase` y `Enable`. La provisión no escribe `PreSharedKey`.

## Implementación

- `gf-onboarding-v2-wifi.js` acepta `V2804AX15T` y usa el layout VSOL.
- `OnboardingV2TaskService` usa los mismos índices al capturar y verificar el baseline Wi-Fi.
- PPPoE, parámetros virtuales y provisiones existentes ya resuelven `V2804AX15T` con el mismo layout.
