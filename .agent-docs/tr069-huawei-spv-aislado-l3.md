# TR-069 Huawei — SPV aislado de L3 (NAT, DNS, máscara)

Estrategia de reparación cuando la WAN cliente queda `Connected` con IP y gateway, pero **no hay Internet** porque NAT, DNS o máscara no persistieron.

Caso validado: HG8145X6 FW `V5R021C10S165` · 2026-08-25 · device `00259E-HG8145X6-48575443C6FBA6AA`. Alta WAN: [tr069-e2e-validacion-modelo.md](./tr069-e2e-validacion-modelo.md) (sección Lab HG8145X6).

## Síntoma

Tras un `setParameterValues` mixto (WAN + VLAN + LANBIND, a veces WiFi):

| Hoja | Se envió | GPV / UI |
|------|----------|----------|
| `AddressingType` | Static | Static |
| `ExternalIPAddress` | pool (p. ej. `192.168.30.250`) | aplicado |
| `DefaultGateway` | `192.168.30.1` | aplicado |
| `X_HW_VLAN` / `X_HW_SERVICELIST` | 100 / INTERNET | aplicado |
| `ConnectionStatus` | — | `Connected` |
| `NATEnabled` | `true` | **`false`** |
| `DNSServers` | `8.8.8.8,8.8.4.4` | **fábrica** (`192.168.0.1`) |
| `SubnetMask` | `255.255.255.0` | **vacío** |

`Connected` **no** implica Internet. Sin NAT, la LAN sale con IP privada y MK2 no tiene ruta de retorno.

Causa habitual: el SPV mixto respondió HTTP 200 (o 9002 en canal `inform` / task) y el CPE aplicó solo parte de las hojas. El ACS puede cachear valores viejos; hay que **GPV con CR**, no fiarse de la proyección Mongo.

## Qué no hacer primero

| Acción | Por qué |
|--------|---------|
| POST NBI con **array** de tasks + `connection_request` | HTTP 200 falso: cola vacía, el CPE no aplica nada |
| Recrear WCD.2 / ciclo `Enable=false` → L3 → `Enable=true` | No hizo falta en este firmware; es el **escalado**, no el primer intento |
| Confiar en GPV de caché | `SubnetMask` vacío con timestamp fresco sí es real; LANBIND en `0` en caché puede ser `1` en el CPE |
| Fetch HTTP a la IP WAN Huawei | El HG8145X6 no publica UI en WAN (timeout esperado) |

## Estrategia (la que funcionó)

Un parámetro (o un par DNS) por sesión CWMP. Purgar el fault `inform` 9002 **antes de cada** SPV. Cada paso: SPV con `connection_request` → GPV inmediato de **esa** hoja.

```text
purgar /faults/{device}:inform
    → SPV NATEnabled=true
    → SPV DNSServers + DNSEnabled
    → SPV SubnetMask
    → verificar L3 (MK2 + IPPing)
```

Orden: NAT primero (es lo que corta Internet), luego DNS, luego máscara. La máscara en `/24` classful puede ser cosmética; en este lab **sí** persistió y el IPPing a `8.8.8.8` pasó 4/4.

### NBI (plantilla)

Device id GenieACS: `{OUI}-{ProductClass}-{Serial}`. Túnel `127.0.0.1:7557`. Purgar:

```bash
ENC=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$DEVICE', safe=''))")
curl -sS -X DELETE "http://127.0.0.1:7557/faults/${ENC}%3Ainform"
```

SPV de una hoja + CR (`WAN` = path de la WAN cliente, p. ej. `...WANConnectionDevice.2.WANIPConnection.1`):

```bash
curl -sS -m 60 -X POST \
  "http://127.0.0.1:7557/devices/$ENC/tasks?timeout=45000&connection_request" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"setParameterValues\",\"parameterValues\":[[\"$WAN.SubnetMask\",\"255.255.255.0\",\"xsd:string\"]]}"
```

Tipos: `NATEnabled` / `DNSEnabled` → `xsd:boolean`; `SubnetMask` / `DNSServers` → `xsd:string`.

Luego GPV de la misma hoja (mismo POST, `name: getParameterValues`, `parameterNames: ["$WAN.SubnetMask"]`). Si el valor no cambió, el CPE la rechazó: pasar al escalado.

### Tiempos lab HG8145X6 (2026-08-25)

| Hoja | SPV + CR | HTTP | GPV |
|------|----------|------|-----|
| `NATEnabled=true` | ~2.3 s | 200 | `True` |
| `DNSServers=8.8.8.8,8.8.4.4` + `DNSEnabled=true` | ~2.3 s | 200 | `8.8.8.8,8.8.4.4` |
| `SubnetMask=255.255.255.0` | ~3.6 s | 200 | `255.255.255.0` |

El `inform` 9002 **reaparece** tras cada CR (provision GenieACS / passwords write-only). No bloquea el SPV. Purgarlo igual para no mezclar retries.

## Verificar Internet (independiente del ACS)

1. MK2 (vía VPS; SSH/API en allowlist): ARP de la IP cliente = MAC de **WCD.2**, no la de staging. Ping 5/5.
2. En el CPE: `IPPingDiagnostics` Host `8.8.8.8`, Interface = path de la WAN cliente. Esperado: `Complete`, `SuccessCount` = repeticiones.
3. LANBIND: GPV con CR. En Huawei, `Lan{1-4}Enable=1` y `SSID1Enable=1` (2.4 GHz). `X_HW_LANBIND` **no** tiene `SSID5Enable`; el 5 GHz puede no salir por esta WAN.
4. UI ONU WAN cliente: máscara `255.255.255.0`, NAT on, DNS `8.8.8.8`. PC en **2.4 GHz o LAN**: ping `8.8.8.8` + un dominio.

Lab: ARP `192.168.30.250` → `F8:53:29:D4:B1:D6` en `sfp-sfpplus2`; ping 5/5 ~1.5 ms; IPPing 4/4 ~34 ms.

## Escalado (si una hoja no persiste)

1. Ciclo encolado (2 POST 202 + 1 CR): `Enable=false` → SPV L3 completo → `Enable=true`.
2. `refreshObject` de la WAN cliente; revisar si `MaxMTUSize` / `X_HW_LowerLayers` siguen `None` (objeto a medias).
3. `deleteObject` WCD.2 y recrear: 2 AddObject 202 + SPV con CR, `Enable` al final; si 9002 otra vez, repetir SPVs aislados sobre el slot nuevo.

## Impacto backend (implementado)

[Tr069ProvisioningService](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069ProvisioningService.kt) / [Tr069ModelProfile](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/genieacs/Tr069ModelProfile.kt):

- Alta Huawei: WAN+LANBIND encolado; WiFi (SSID + `KeyPassphrase`, sin `BeaconType`) en un CR aparte.
- Tras GPV, si `NATEnabled=false` o `SubnetMask` vacío o DNS contiene `192.168.0.1` (valor **presente** y mal; `null` no repara): un SPV L3 aislado (NAT + DNS + máscara, sin WLAN) con CR.
- Escalado manual si ese SPV no persiste: las tres hojas **una por sesión** (esta página). No recrear WCD como primer intento.
