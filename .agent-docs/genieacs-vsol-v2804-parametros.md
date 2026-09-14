# Catálogo TR-069 — ONU VSOL V2804AX15T (GenieACS)

Parámetros **writable** descubiertos en lab (ago 2026) para provisionar o ampliar la config remota de esta HGU. No es el data model TR-098 completo: el ACS solo cachea lo que llegó en Inform o en `refreshObject`.

Complementa: [genieacs-despliegue-gigafiber.md](./genieacs-despliegue-gigafiber.md) · script [probe-vsol-wan.sh](../scripts/genieacs/probe-vsol-wan.sh) · staging [mk2-red-aprovisionamiento-255.md](./mk2-red-aprovisionamiento-255.md).

## Identidad del CPE de referencia

| Campo | Valor |
|-------|--------|
| ProductClass | `V2804AX15T` |
| Manufacturer / OUI | Realtek / `B46415` |
| Firmware / HW | `V1.1.00-251017` / `V1.1` |
| DeviceId ACS (lab) | `B46415-V2804AX15T-12345B4641531C0B6` |
| Root | `InternetGatewayDevice.` (TR-098 + extensiones `X_CT-COM_*` y `X_ZTE-COM_*`) |
| Snapshot NBI | 2026-08-20 · ~655 nodos `_writable:true` · ~126 hojas con valor (sin arrays DHCP OPTION125/16/17) |
| Staging MK2 (aprovisionamiento) | Red **`192.168.255.0/24`** — lease DHCP `.255.100–250`, GW `192.168.255.1`. **Todas las ONUs arrancan aquí.** Ver [mk2-red-aprovisionamiento-255.md](./mk2-red-aprovisionamiento-255.md) |
| Prod MK2 | Red `192.168.30.0/24` — IP estática post-alta (`vlan=100`) |
| DNS por defecto | `8.8.8.8,8.8.4.4` (`genieacs.default-dns`) |
| WAN VLAN backend | **`subscription.vlan` de la app** (1 o 100), no `genieacs.wan-vlan-id` fijo |

Otras unidades del mismo modelo usan **`WANConnectionDevice.1`** en fábrica (único slot WAN). El backend descubre el índice vía GenieACS (`listWanConnectionIndices`) y cae a **`.1`** si el árbol aún no está disponible.

## Cómo aplicar un cambio

1. Túnel SSH a NBI: `ssh -L 7557:127.0.0.1:7557 -L 3000:127.0.0.1:3000 root@212.85.13.47`
2. Encolar task NBI con **`?connection_request`** cuando el pool del abonado es alcanzable desde el VPS vía **`wg-olt`** (prod `192.168.x` y lab `192.168.123.x`).
3. Respuesta esperada: **HTTP 202** sin `Incorrect connection request credentials`.
4. Verificar valor en NBI (`GET /devices/?query=…`) y en el panel web de la ONU.
5. Alternativa sin CR: POST **sin** `?connection_request` + **Inform manual** en VSOL (Diagnostics → TR-069).
6. **GPV (lectura):** usar `"parameterNames": [...]` — **no** `parameterValues` (cuelga el NBI GenieACS 1.2.x).
7. Antes de reprovisionar en lab: purgar cola (`purgeDeviceQueue` en backend, o `DELETE /tasks/{id}` + `DELETE /faults/{id}` vía NBI).

Credenciales ACS y Connection Request deben ser **las mismas** (`ACS_CPE_USERNAME` / `ACS_CPE_PASSWORD` en `/opt/gigafiber/genieacs/.env`). El provision `inform` las mantiene alineadas (no regenerar password aleatorio por Inform). Ver [genieacs-despliegue-gigafiber.md](./genieacs-despliegue-gigafiber.md).

`refreshObject` para traer ramas vacías:

```json
{"name":"refreshObject","objectName":"InternetGatewayDevice.LANDevice"}
```

UI GenieACS: device → **All parameters** → lápiz en writable. Filtro por `VLAN`, `ExternalIP`, `SSID`.

---

## Preconfiguración de fábrica — staging DHCP + VLAN 100 + TR-069

Política Gigafiber para VSOL V2804AX15T **antes del alta prod**:

| Parámetro | Valor |
|-----------|--------|
| Red | **Staging MK2** `192.168.255.0/24` (red de aprovisionamiento) |
| `AddressingType` | **DHCP** |
| Lease típico | `192.168.255.100` – `192.168.255.250` |
| `DefaultGateway` | `192.168.255.1` |
| VLAN (CT + ZTE + GPON link) | **100** |
| ACS URL | `http://acs.gigafiberperu.cloud/` |
| CR URL | `http://192.168.255.x:7547/tr069` (x = lease DHCP) |
| WiFi | SSIDs default VSOL hasta el alta |

**No confundir:** VLAN **100** en el CPE no implica IP `.30.x` todavía. En staging la ONU sigue en **DHCP `.255.x`** hasta que OLT + TR-069 la migran a prod.

Verificado **2026-08-21** (`B46415-V2804AX15T-12345B4641531C0B6`, GPV tras CR): `DHCP`, VLAN **100**, IP **`192.168.255.249`**.

Runbook staging: [mk2-red-aprovisionamiento-255.md](./mk2-red-aprovisionamiento-255.md).

### Post-alta prod → SPV hacia `192.168.30.x`

Tras `oltProvisionStatus=COMPLETE` y `vlan=100` en la app, el backend aplica WAN **Static** + IP del pool **`192.168.30.0/24`** + WiFi en GenieACS (`Tr069ProvisioningService`).

| Origen WAN | SPV monolítico 1 disparo | Notas |
|------------|----------------------|--------|
| Staging **DHCP `.255.x`** + VLAN 100 | Falla **`9001 Request denied`** en `ExternalIPAddress` (GenieACS HTTP **202** + fault `cwmp.9003`) | Validado prod 2026-08-21 / 2026-08-22 |
| Secuencia automática | SPV unlock → SPV **Static+`.30.x`+WiFi** si WAN ∈ `.255.0/24` | `Tr069ProvisioningService` |
| **Dual-WAN (WCD.1 staging + WCD.2 prod)** | **Recomendado** — ver sección siguiente | Validado prod **2026-08-22** |

### Estrategia dual-WAN (recomendada) — prod sin perder ACS

**Problema del SPV monolítico en WCD.1:** al migrar la única WAN de staging (`192.168.255.x` DHCP) a prod (`192.168.30.x` Static), el CPE pierde temporalmente la ruta al ACS. El **Connection Request** deja de funcionar (`ConnectionRequestURL` apunta a la IP vieja), la cola CWMP se atasca y el aprovisionamiento queda a ciegas hasta un Inform manual o visita técnica.

**Solución validada:** mantener **dos `WANConnectionDevice`** en paralelo:

| Slot | Rol | Red | ServiceList | NAT | ¿Se toca en alta prod? |
|------|-----|-----|-------------|-----|------------------------|
| **WCD.1** | TR-069 + aprovisionamiento | Staging **`192.168.255.0/24`** · DHCP · VLAN 100 | **`TR069`** (o `TR069,INTERNET` en fábrica) | `false` | **No** — perfil fijo de staging |
| **WCD.2** | Internet del abonado | Prod **`192.168.30.0/24`** · Static · VLAN 100 | **`INTERNET`** | `true` | **Sí** — IP, GW, DNS del cliente |

**CPE de referencia:** `B46415-V2804AX15T-12345B4641531C0B6` · FW `V1.1.00-251017` · suscripción **#2295** · prod **2026-08-22**.

**Resultado verificado:**

- WCD.1 sigue **Connected** en `192.168.255.249` → CR responde en ~3 s (`ConnectionRequestURL` = `http://192.168.255.249:7547/tr069`).
- WCD.2 queda **Connected** con IP estática del pool (ej. `192.168.30.216`) → Internet del cliente en VLAN 100.
- Cambiar **solo** la IP de WCD.2 (`ExternalIPAddress`) funciona con un SPV de un parámetro, sin unlock.
- **No aplica unlock CWMP** en WCD.2: es un objeto WAN **nuevo**; el unlock de `AddressingType=DHCP` solo fue necesario al editar Static **en WCD.1** ya existente.

**Prerrequisitos**

1. OLT con `oltProvisionStatus=COMPLETE` y service-port VLAN **100** hacia la ONU.
2. IP del abonado reservada en BD (`subscription.ip`, pool `192.168.30.0/24`, `vlan=100`).
3. Cola ACS limpia (`purgeDeviceQueue` / `DELETE` tasks y faults) antes de encadenar tasks.
4. Túnel o acceso NBI en VPS (`http://127.0.0.1:7557`).

**Runbook — alta prod dual-WAN**

Sustituir `{DEVICE}`, `{IP}` (ej. `192.168.30.216`), `{SSID24}`, `{SSID5}`, `{PASS}`.

**1. Crear WCD.2** (omitir si ya existe índice `2`):

```bash
curl -sS -X POST 'http://127.0.0.1:7557/devices/{DEVICE}/tasks?timeout=45000&connection_request' \
  -H 'Content-Type: application/json' \
  -d '{"name":"addObject","objectName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice"}'
```

Esperar ~10 s (fin de sesión CWMP).

**2. Crear WANIPConnection en WCD.2** (omitir si ya hay `WANIPConnection.1`):

```bash
curl -sS -X POST 'http://127.0.0.1:7557/devices/{DEVICE}/tasks?timeout=45000&connection_request' \
  -H 'Content-Type: application/json' \
  -d '{"name":"addObject","objectName":"InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection"}'
```

Esperar ~10 s. Si fault `9004 Resources exceeded`, comprobar GPV: a veces el objeto se crea igual.

**3. SPV Internet Static en WCD.2** — **no modificar WCD.1**:

```bash
curl -sS -X POST 'http://127.0.0.1:7557/devices/{DEVICE}/tasks?timeout=45000&connection_request' \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.Enable", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.ConnectionType", "IP_Routed", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.Name", "2_INTERNET_R_VID_100", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_CT-COM_ServiceList", "INTERNET", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_ZTE-COM_ServiceList", "INTERNET", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.NATEnabled", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.AddressingType", "Static", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.ExternalIPAddress", "{IP}", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.SubnetMask", "255.255.255.0", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.DefaultGateway", "192.168.30.1", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.DNSServers", "8.8.8.8,8.8.4.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.DNSEnabled", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_CT-COM_VLANIDMark", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_ZTE-COM_VLANID", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_ZTE-COM_VLANEnable", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.X_CT-COM_WANGponLinkConfig.VLANIDMark", "100", "xsd:unsignedInt"]
  ]
}'
```

**4. WiFi (opcional, mismo CR)** — parámetros en `LANDevice`, independientes de la WAN:

```bash
# WLANConfiguration.5 = 2.4 GHz · .1 = 5 GHz (VSOL V2804AX15T)
["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID", "{SSID24}", "xsd:string"],
["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase", "{PASS}", "xsd:string"],
["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", "{SSID5}", "xsd:string"],
["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", "{PASS}", "xsd:string"]
```

**5. Verificar (GPV tras CR)**

| Parámetro GPV | WCD.1 (staging) | WCD.2 (prod) |
|---------------|-----------------|--------------|
| `ConnectionStatus` | `Connected` | `Connected` |
| `ExternalIPAddress` | `192.168.255.x` | `{IP}` `.30.x` |
| `X_CT-COM_ServiceList` | `TR069` | `INTERNET` |
| `ManagementServer.ConnectionRequestURL` | `http://192.168.255.x:7547/tr069` | — |

**Cambio de IP solo en WCD.2** (reassign, prueba, corrección):

```bash
curl -sS -X POST 'http://127.0.0.1:7557/devices/{DEVICE}/tasks?timeout=45000&connection_request' \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.ExternalIPAddress", "{NUEVA_IP}", "xsd:string"]
  ]
}'
```

**Qué evitar**

- **No** aplicar SPV Static prod en WCD.1 si WCD.1 debe seguir siendo el canal ACS de staging.
- **No** quitar `INTERNET` de WCD.1 hasta confirmar WCD.2 **Connected** (si WCD.1 aún lleva `TR069,INTERNET` de fábrica).
- **No** confundir HTTP **200/202** del NBI con éxito CWMP: revisar `/faults` y GPV.

**Backend (implementado):** `Tr069ProvisioningService` aplica dual-WAN en cada alta FIBER:

1. `purgeDeviceQueue`
2. Si falta WCD.2 → `AddObject` `WANConnectionDevice`; si falta WANIP → `AddObject` `WANConnectionDevice.2.WANIPConnection` (fault `9004` se ignora si el objeto ya existe)
3. SPV Static INTERNET en **WCD.2** (`buildClientInternetWanParameterValues`) — **cero tasks sobre WCD.1**
4. SPV WiFi en `LANDevice` (`buildWifiParameterValues`)
5. Verifica cache ACS: `WCD.2.ExternalIPAddress` + SSIDs

Índices configurables: `genieacs.staging-wan-index=1`, `genieacs.client-wan-index=2`, `genieacs.client-wan-name-pattern=2_INTERNET_R_VID_{vlan}`.

### Unlock CWMP — caso confirmado (2026-08-22)

**CPE:** `B46415-V2804AX15T-12345B4641531C0B6` · FW `V1.1.00-251017` · WAN índice `.1`

**Hallazgo:** con la ONU ya en staging (`AddressingType=DHCP`, VLAN **100** en CT/ZTE/GPON, IP `192.168.255.x`), el SPV Static **solo** falla. No es porque falte esa config en el árbol: GPV tras CR mostró los mismos leaves que un SPV “DHCP+VLAN100”. Lo que desbloquea la edición de `ExternalIPAddress` / `DefaultGateway` es un **SetParameterValues previo** que reescribe `AddressingType=DHCP` (aunque el valor no cambie). Connection Request o GPV **no** bastan.

| Prueba | Resultado |
|--------|-----------|
| Solo SPV Static (sin paso previo) | HTTP 202 + fault `9001` en `ExternalIPAddress` |
| SPV unlock mínimo (`AddressingType=DHCP`) → SPV Static+VLAN+WiFi | **OK** — WAN Static aplicada |
| Tener DHCP+VLAN100 a mano / ya en el CPE (sin SPV unlock) | **No** sustituye al unlock |

**No es necesaria** la VLAN en el unlock si ya está en 100. El unlock mínimo validado:

```bash
curl -sS -X POST 'http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?timeout=30000&connection_request' \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.AddressingType", "DHCP", "xsd:string"]
  ]
}'
```

Luego (esperar ~5–10 s / fin de sesión CWMP) el SPV prod. Ejemplo validado (`192.168.30.215`, SSID `puppy` / `bdbdbxbd`):

```bash
curl -sS -X POST 'http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?timeout=30000&connection_request' \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.AddressingType", "Static", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress", "192.168.30.215", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.SubnetMask", "255.255.255.0", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DefaultGateway", "192.168.30.1", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DNSServers", "8.8.8.8,8.8.4.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DNSEnabled", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_CT-COM_VLANIDMark", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANID", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANEnable", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_CT-COM_WANGponLinkConfig.VLANIDMark", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID", "puppy", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase", "{PASS}", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", "bdbdbxbd", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", "{PASS}", "xsd:string"]
  ]
}'
```

**Nota GenieACS:** HTTP **202** solo significa “task encolada”; el rechazo real aparece en `/faults` (`cwmp.9003` / leaf `9001`). Purgar tasks/faults antes de reintentar.

**Backend:** hoy `buildStagingDhcpParameterValues` manda `DHCP` + VLAN (más de lo mínimo). El unlock crítico confirmado es solo `AddressingType=DHCP`.

### Backend (`Tr069ProvisioningService`) — cada alta

1. Esperar device en ACS (match serial).  
2. **`purgeDeviceQueue(deviceId)`** — borra tasks/faults pendientes vía NBI (`GET` + `DELETE` por id) para evitar bloqueo de sesión CWMP.  
3. **WCD.1 no se toca** (preconfig de fábrica / canal TR-069).  
4. Asegurar slot **WCD.2** (`AddObject` si falta) y SPV Static INTERNET del abonado.  
5. SPV WiFi en `LANDevice`.  
6. Poll faults (`findFaultBodyForTask`) + verificación IP WCD.2 y SSIDs en cache ACS.

Código: `GenieAcsClient.addObject`, `GenieAcsClient.purgeDeviceQueue`, `Tr069ProvisioningService.provision`.

### Body JSON referencia — prod VLAN 100 (índice WAN `.1`)

Sustituir `{IP}`, `{SSID24}`, `{SSID5}`, `{PASS}`. **Requiere unlock previo** si la ONU viene de staging DHCP `.255.x` (ver arriba).

```json
{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.AddressingType", "Static", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress", "{IP}", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.SubnetMask", "255.255.255.0", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DefaultGateway", "192.168.30.1", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DNSServers", "8.8.8.8,8.8.4.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DNSEnabled", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_CT-COM_VLANIDMark", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANID", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANEnable", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.X_CT-COM_WANGponLinkConfig.VLANIDMark", "100", "xsd:unsignedInt"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID", "{SSID24}", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase", "{PASS}", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", "{SSID5}", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", "{PASS}", "xsd:string"]
  ]
}
```

### GPV — verificar preconfig staging (fábrica)

Valores esperados **antes del alta**:

| Parámetro GPV | Esperado |
|---------------|----------|
| `AddressingType` | `DHCP` |
| `ExternalIPAddress` | `192.168.255.x` |
| `DefaultGateway` | `192.168.255.1` |
| `X_CT-COM_VLANIDMark` | `100` |

```json
{
  "name": "getParameterValues",
  "parameterNames": [
    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.AddressingType",
    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress",
    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.DefaultGateway",
    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_CT-COM_VLANIDMark",
    "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_ZTE-COM_VLANID",
    "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID",
    "InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID"
  ]
}
```

POST con `?connection_request` a `/devices/{deviceId}/tasks`.

### GPV — verificar post-alta prod

| Parámetro | Esperado |
|-----------|----------|
| `AddressingType` | `Static` |
| `ExternalIPAddress` | `192.168.30.x` (pool app) |
| `DefaultGateway` | `192.168.30.1` |
| VLAN | `100` |

### Cola GenieACS — evitar bloqueos

Si quedan **tasks SPV fallidos** (lab), cada Connection Request reintenta la cola antes del task nuevo → NBI cuelga ~20–90 s.

| Acción | Cuándo |
|--------|--------|
| Backend `purgeDeviceQueue` | Automático al inicio de cada `provision()` |
| Manual NBI | Antes de pruebas Postman repetidas en el mismo serial |
| Sintoma | POST `/tasks` timeout, HTTP 000, cache ACS stale |

Purgar vía NBI (no hay DELETE bulk; listar y borrar por `_id`):

```bash
# Listar: GET /tasks/?query={"device":"DEVICE_ID"}
# Borrar: DELETE /tasks/{_id}  y  DELETE /faults/{_id}
```

---

## Aprovisionamiento validado — lab (ago 2026)

Flujo **confirmado** en VSOL V2804AX15T con GenieACS NBI + Connection Request (VPN VPS ↔ MK2, pool `192.168.123.0/24`).

| Campo | Valor |
|-------|--------|
| **Método** | `POST` |
| **URL** | `http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?connection_request` |
| **Header** | `Content-Type: application/json` |
| **Respuesta** | **202** + JSON con `_id` de la tarea (sin error de credenciales CR) |
| **Efecto** | IP WAN, VLAN, DNS y WiFi aplicados en la ONU en la misma sesión CWMP |

### Valores aplicados en esta prueba

| Grupo | Parámetro | Valor |
|-------|-----------|-------|
| WAN | IP | `192.168.123.4` |
| WAN | Máscara / GW | `255.255.255.0` / `192.168.123.1` |
| WAN | DNS | `8.8.8.8,8.8.4.4` |
| WAN | VLAN | `1` (triple path CT + GPON + ZTE) |
| WiFi 2.4G | inst **5** | SSID `acs2g`, clave `11111111` |
| WiFi 5G | inst **1** | SSID `acs5g`, clave `11111111` |

### Body JSON (referencia — no re-ejecutar en lab salvo prueba intencional)

```json
{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.AddressingType", "Static", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.ExternalIPAddress", "192.168.123.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.SubnetMask", "255.255.255.0", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.DefaultGateway", "192.168.123.1", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.DNSServers", "8.8.8.8,8.8.4.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.DNSEnabled", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.X_CT-COM_VLANIDMark", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.X_ZTE-COM_VLANID", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.X_ZTE-COM_VLANEnable", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.X_CT-COM_WANGponLinkConfig.VLANIDMark", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID", "acs2g", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase", "11111111", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", "acs5g", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", "11111111", "xsd:string"]
  ]
}
```

### curl equivalente

```bash
curl -X POST \
  'http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?connection_request' \
  -H 'Content-Type: application/json' \
  -d @- <<'EOF'
{
  "name": "setParameterValues",
  "parameterValues": [
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.AddressingType", "Static", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.ExternalIPAddress", "192.168.123.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.SubnetMask", "255.255.255.0", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.DefaultGateway", "192.168.123.1", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.DNSServers", "8.8.8.8,8.8.4.4", "xsd:string"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.DNSEnabled", "true", "xsd:boolean"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.X_CT-COM_VLANIDMark", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.X_ZTE-COM_VLANID", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.X_ZTE-COM_VLANEnable", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.X_CT-COM_WANGponLinkConfig.VLANIDMark", "1", "xsd:unsignedInt"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.SSID", "acs2g", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.5.KeyPassphrase", "11111111", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", "acs5g", "xsd:string"],
    ["InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.KeyPassphrase", "11111111", "xsd:string"]
  ]
}
EOF
```

### Prerrequisitos (lab)

- Túnel SSH activo a `127.0.0.1:7557`
- VPN `wg-olt` con ruta `192.168.123.0/24`
- MK2: firewall TCP `:7547` desde `10.255.255.2`
- CPE: `Connect Request Username` / `Password` = `gigafiber-acs` / valor de `ACS_CPE_PASSWORD` (mismo que TR-069)
- WAN activo en `WANConnectionDevice.4` con `X_CT-COM_ServiceList` = `TR069,INTERNET`

### Notas

- Cambiar solo los **valores** (IP, VLAN, SSID…) para nuevas pruebas; mantener paths e instancias WiFi (**1** = 5G, **5** = 2.4G).
- Para VLAN distinta de `1` en lab, verificar que el uplink OLT/switch soporte ese VID.
- **Una tarea por POST**; no mezclar con `reboot` en la misma petición.

Ejemplo mínimo (solo IP):

```http
POST http://127.0.0.1:7557/devices/{deviceId}/tasks?connection_request
Content-Type: application/json

{"name":"setParameterValues","parameterValues":[
  ["InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4.WANIPConnection.1.ExternalIPAddress","192.168.30.210","xsd:string"]
]}
```

### Requisitos de esta HGU (lab)

- ACS URL en **HTTP** (`http://acs.gigafiberperu.cloud/`). gSOAP 2.7 no completa TLS 1.2/1.3 GCM ni sigue el 301 de Certbot.
- WAN service mode **TR069_INTERNET** (no solo INTERNET). Si falta `TR069` en `X_CT-COM_ServiceList`, el Inform no sale.
- **Auth HTTP CWMP (prod):** `cwmp.auth = true` en GenieACS — no se exige Digest en el Inform porque gSOAP falla el reintento 401 (`qop=auth,auth-int`). Compensación: allowlist nginx (solo IP MK2). Ver [genieacs-cwmp-auth-http.md](./genieacs-cwmp-auth-http.md).

## Prefijo WAN activo (lab)

```
InternetGatewayDevice.WANDevice.1.WANConnectionDevice.4
  WANIPConnection.1          ← IP routed, NAT, VLAN, DNS
  X_CT-COM_WANGponLinkConfig ← VLAN/prio GPON
```

Alias del servicio: `1_TR069_INTERNET_R_VID_{vlan}`.

---

## 1. Provisionamiento WAN (validado en lab)

SetParameterValues aplicado y persistido en la ONU. Para producción Gigafiber (MK2): IP del pool `192.168.30.0/24`, gw `192.168.30.1`, VLAN **100**.

| Parámetro | Path relativo a `…WANIPConnection.1.` | Tipo | Lab | Notas |
|-----------|----------------------------------------|------|-----|--------|
| IP WAN | `ExternalIPAddress` | string | `192.168.123.3` | **Writable.** Confirmado ACS + WAN Info VSOL |
| Máscara | `SubnetMask` | string | `255.255.255.0` | |
| Gateway | `DefaultGateway` | string | `192.168.123.1` | |
| Tipo | `AddressingType` | string | `Static` | `Static` / DHCP |
| DNS | `DNSServers` | string | `8.8.8.8,8.8.4.4` | Lista coma |
| DNS on | `DNSEnabled` | boolean | `True` | |
| Override DNS | `DNSOverrideAllowed` | boolean | `True` | |
| Enable WAN | `Enable` | boolean | `True` | |
| NAT | `NATEnabled` | boolean | `True` | Router, no bridge |
| MTU | `MaxMTUSize` | unsignedInt | `1500` | |
| Trigger | `ConnectionTrigger` | string | `AlwaysOn` | |
| Tipo conn | `ConnectionType` | string | `IP_Routed` | |
| Nombre | `Name` / `Alias` / `vsName` | string | `1_TR069_INTERNET_R_VID_1` | El VID del nombre puede desfasarse del VLAN real |
| Servicios | `X_CT-COM_ServiceList` | string | `TR069,INTERNET` | **Obligatorio TR069** para Inform |
| Servicios (alias) | `X_ZTE-COM_ServiceList` | string | `TR069,INTERNET` | Duplicado vendor |

### VLAN — setear los tres (validado)

Un solo `VLANIDMark` no basta: hay que alinear **CT-COM IP + GPON link + alias ZTE**.

| Parámetro | Path | Tipo | Lab |
|-----------|------|------|-----|
| VLAN IP (principal) | `…WANIPConnection.1.X_CT-COM_VLANIDMark` | unsignedInt | `100` |
| Modo VLAN | `…WANIPConnection.1.X_CT-COM_VLANMode` | unsignedInt | `1` (tagged) |
| PCP 802.1p | `…WANIPConnection.1.X_CT-COM_802-1pMark` | unsignedInt | `0` |
| VLAN GPON | `…WANConnectionDevice.4.X_CT-COM_WANGponLinkConfig.VLANIDMark` | unsignedInt | `100` |
| Enable GPON link | `…WANGponLinkConfig.Enable` | boolean | `True` |
| Mode GPON | `…WANGponLinkConfig.Mode` | unsignedInt | `2` |
| PCP GPON | `…WANGponLinkConfig.802-1pMark` | unsignedInt | `4294967295` (= no set / inherit) |
| VLAN ZTE | `…WANIPConnection.1.X_ZTE-COM_VLANID` | unsignedInt | `100` |
| VLAN ZTE on | `…WANIPConnection.1.X_ZTE-COM_VLANEnable` | unsignedInt | `1` |
| PCP ZTE | `…WANIPConnection.1.X_ZTE-COM_8021P` | unsignedInt | `1` |

En lab, VLAN **100** deja al CPE sin internet si el uplink es VLAN 1 (`192.168.123.0/24`). En producción MK2 el uplink OLT es VLAN 100.

`probe-vsol-wan.sh --apply` solo escribe el **primer** `VLANIDMark` que encuentra. Para un cambio real, incluir también `WANGponLinkConfig.VLANIDMark` y `X_ZTE-COM_VLANID`.

---

## 2. ACS / ManagementServer

Credenciales **fijas** en Gigafiber (provision `inform` + panel VSOL): mismo usuario/contraseña para Inform al ACS y para Connection Request.

| Parámetro | Path | Valor Gigafiber | Writable |
|-----------|------|-----------------|----------|
| ACS URL | `…ManagementServer.URL` | `http://acs.gigafiberperu.cloud/` | sí |
| ACS user | `…ManagementServer.Username` | `gigafiber-acs` | sí |
| ACS pass | `…ManagementServer.Password` | `ACS_CPE_PASSWORD` (.env) | sí |
| CR user | `…ManagementServer.ConnectionRequestUsername` | **igual que Username** | sí |
| CR pass | `…ManagementServer.ConnectionRequestPassword` | **igual que Password** | sí |
| Inform periódico | `…ManagementServer.PeriodicInformEnable` | `True` | sí |
| Intervalo (s) | `…ManagementServer.PeriodicInformInterval` | `3600` | sí |

**Importante:** el provision `inform` por defecto de GenieACS regeneraba CR username (= deviceId) y password aleatorio en cada Inform; eso rompía `?connection_request`. Corregido en `scripts/genieacs/configure-genieacs-pilot.sh` (ago 2026).

Writable **sin valor** hasta `refreshObject` de `ManagementServer`:

`URL`, `Username`, `Password`, `EnableCWMP`, `AutoCreateInstances`, `InstanceMode`, `UpgradesManaged`, `CWMPRetryMinimumWaitInterval`, `CWMPRetryIntervalMultiplier`, `DefaultActiveNotificationThrottle`, `ManageableDeviceNotificationLimit`, `UDPConnectionRequestAddressNotificationLimit`, `STUNEnable`, `STUNServerAddress`, `STUNServerPort`, `STUNUsername`, `STUNPassword`, `STUNMinimumKeepAlivePeriod`, `STUNMaximumKeepAlivePeriod`.

---

## 3. WiFi (`LANDevice.1.WLANConfiguration.{1-8}`)

Ocho SSIDs. En lab: **1** = 5 GHz `VSOL-5G-C0B6`, **5** = 2.4 GHz `VSOL-C0B6`. 2–4 guest (`AP-1`…), 6–8 `FTTH-*`.

Hojas ya cacheadas (writable, **validado en lab ago 2026** vía `setParameterValues` + `?connection_request`):

| Parámetro | Path | Tipo |
|-----------|------|------|
| SSID | `…WLANConfiguration.{n}.SSID` | string |
| Clave | `…WLANConfiguration.{n}.KeyPassphrase` | string |

Instancias lab: **1** = 5 GHz, **5** = 2.4 GHz. Ver [Aprovisionamiento validado](#aprovisionamiento-validado--lab-ago-2026).

Writable **sin valor** (mismo set en las 8 instancias). Hacer `refreshObject` de `InternetGatewayDevice.LANDevice` + Inform antes de setear:

| Parámetro | Uso esperado |
|-----------|----------------|
| `Enable` / `RadioEnabled` | Encender radio / BSS |
| `SSIDAdvertisementEnabled` / `X_CT-COM_SSIDHide` | Ocultar SSID |
| `Channel` / `AutoChannelEnable` | Canal |
| `ChannelWidth` / `OperatingChannelBandwidth` / `X_CT-COM_ChannelWidth` | 20/40/80 |
| `TransmitPower` / `X_CT-COM_Powerlevel` | Potencia |
| `BeaconType` / `WPAAuthenticationMode` / `WPAEncryptionModes` | Seguridad WPA |
| `IEEE11iAuthenticationMode` / `IEEE11iEncryptionModes` | WPA2 |
| `WMMEnable` | WMM |
| `MACAddressControlEnabled` | Filtro MAC |
| `SsidIsolationEnable` / `BlockRelay` | Aislamiento |
| `EnableBandSteering` | Band steering |
| `X_CT-COM_RFBand` / `X_CT-COM_Mode` / `X_CT-COM_VLAN` | Banda / modo / VLAN SSID (CTCOM) |
| `X_CT-COM_APModuleEnable` / `X_CT-COM_GuardInterval` / `X_CT-COM_RetryTimeout` / `X_CT-COM_WPSKeyWord` | Vendor CTCOM |
| `RadiusServerIPAddr` / `RadiusServerPort` / `RadiusSecret` / `Enable80211X` | 802.1X |
| `Standard` / `OperatingStandard` / `SupportedStandards` | 802.11n/ac/ax |
| `WEPKeyIndex` / `WEPEncryptionLevel` | WEP (no usar) |

---

## 4. WAN — extras (cacheados, no validados en lab)

Útiles si más adelante hay que endurecer WAN, IPv6 o QoS de cola. Prefijo `…WANIPConnection.1.` salvo que se indique.

### Acceso desde WAN (`Nucom_WanAccessCfg`)

| Parámetro | Lab | Notas |
|-----------|-----|--------|
| `HttpDisabled` | `True` | Panel web por WAN |
| `HttpsDisabled` | `True` | |
| `IcmpEchoReqDisabled` | `True` | Explica ping WAN fallido (mismo patrón que VSOLVA74) |
| `TelnetDisabled` | `True` | |
| `TftpDisabled` | `True` | |
| `ftpDisabled` | `True` | |

A nivel dispositivo: `WANCommonInterfaceConfig.EnabledWanPing` (`False`), `EnabledWanSideHttpServer` (`False`), `WebServerPort` (`80`), `EnabledForInternet` (`True`).

### QoS / reset

`EnableBandctl`, `upStreamBandctl`, `downStreamBandctl`, `Reset`, `RouteProtocolRx`.

### IPv4/IPv6 vendor

`X_CT-COM_IPMode` (`3`), `X_ZTE-COM_IPMode` (`Both`), DS-Lite (`X_CT-COM_Dslite_Enable`, `X_CT-COM_Aftr`, `X_CT-COM_AftrMode`), NPT (`X_CT-COM_NPTv6Enable`), prefix delegation, DNS/addr IPv6 `X_CT-COM_IPv6*` y `X_ZTE-COM_IPv6*`, `X_CT-COM_LanInterface`, `X_CT-COM_LanInterface-DHCPEnable`, `X_CT-COM_MulticastVlan` (`-1`), `X_CT-COM_IPForwardList`.

### DHCP option 60

Cuatro instancias `X_CT-COM_DHCPOPTION60.{1-4}` (`Enable`, `Type`, `Value`, `ValueMode`, `Account`, `Password`). Lab: `Enable=False`.

### Objetos writable (crear instancias)

`PortMapping` (port forward), `X_CT-COM_DDNSConfiguration`, `WANPPPConnection` (PPPoE, vacío en este WAN IP), `WANConnectionDevice` (crear otro WAN). `WANConnectionRemove` es string writable (peligroso).

---

## 5. Ramas writable casi vacías

No se hizo `refreshObject`. Existen en el modelo; hay que refrescar antes de usar:

| Path | Qué se espera |
|------|----------------|
| `InternetGatewayDevice.LANDevice.1.LANEthernetInterfaceConfig` | LAN ETH enable/speed |
| `…LANMACFilter` | Filtro MAC LAN |
| `…LANVirtualServer` | Virtual server / DNAT |
| `InternetGatewayDevice.Firewall` | Firewall |
| `InternetGatewayDevice.X_HW_Security` | Security Huawei-style |
| `InternetGatewayDevice.UserInterface` | Password UI / idioma |

DeviceInfo, Time, DHCP LAN, QoS global, diagnostics (`IPPing`, traceroute) **no** aparecieron siquiera como writable en este snapshot.

---

## 6. Receta mínima MK2 (producción)

Alinear VLAN OLT (service-port) **en el mismo cambio**. Ver [migracion-piloto-vsolva74-629.md](./migracion-piloto-vsolva74-629.md).

```
WAN = …WANDevice.1.WANConnectionDevice.{n}   # n con ServiceList TR069
IP  = WAN.WANIPConnection.1

set IP.AddressingType            = Static
set IP.ExternalIPAddress         = {ip del pool MK2}
set IP.SubnetMask                = 255.255.255.0
set IP.DefaultGateway            = 192.168.30.1
set IP.DNSServers                = 8.8.8.8,8.8.4.4
set IP.X_CT-COM_VLANIDMark       = 100
set IP.X_ZTE-COM_VLANID          = 100
set IP.X_ZTE-COM_VLANEnable      = 1
set WAN.X_CT-COM_WANGponLinkConfig.VLANIDMark = 100
```

No tocar: `WANConnectionRemove`, índices `WANConnectionDevice` a ciegas, WEP, passwords ACS en logs.

---

## 7. Cómo actualizar este catálogo

Cuando haga falta otro parámetro:

1. `refreshObject` de la rama (`WANDevice`, `LANDevice`, `ManagementServer`, …) + Inform.
2. GET NBI del device y filtrar `_writable: true`.
3. Probar SetParameterValues en lab y anotar aquí **Validado** vs **Solo data model**.
4. Si el path es nuevo, añadir fila; no duplicar.

Inventario rápido (en el VPS, NBI localhost):

```bash
./scripts/genieacs/probe-vsol-wan.sh          # VLAN/IP/máscara/gw
# UI: All parameters → buscar el leaf
```

Última revisión: 2026-08-22 (unlock CWMP: re-SPV `AddressingType=DHCP` antes de Static desde staging `.255.x`).
