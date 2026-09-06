# Pruebas locales: Gateway + ACS VPS + MK2 + ONUs `lab`

Regla de agentes: Cursor `.cursor/rules/olt-lab-acs-vps-local.mdc` y `AGENTS.md` (sección pruebas Gateway/ACS).

## Objetivo

Probar escrituras OLT y aprovisionamiento CPE desde la Mac **sin** desplegar Core ni un ACS local improvisado: OLT real, ACS/GenieACS del VPS, y **MK2** como router de lab (VLAN 100).

## Reglas

| Qué | Regla |
|-----|--------|
| **ACS** | Siempre el **ACS del VPS** (WAR `ispadmin-staging-acs` en Tomcat staging). No arrancar `AcsApplication` local salvo spike explícito del usuario. |
| **GenieACS** | NBI del VPS (mismo host que GenieACS prod/staging compartido). |
| **OLT** | Real `10.11.104.2` (SSH legacy desde Gateway). |
| **MikroTik** | **MK2** — `network_device.id = 8`, nombre `Mikrotik CCR 2`, IP `38.224.231.4`, `vlan_id = 100`. Se asume ya cargado en BD local. |
| **ONU** | Solo series con tag GenieACS **`lab`**. |
| **ONU canónica** | `ZTEGDC47BFFD` → `_id` `5872C9-F6600R-ZTEGDC47BFFD`, `_tags: ["lab"]`. |

## MK2 en BD local

Verificación rápida:

```sql
SELECT id, name, ip_address, vlan_id, disabled
FROM ispadmin_dev.network_device
WHERE id = 8 OR name LIKE '%CCR 2%';

SELECT id, ip_segment, host_device_id, is_eligible
FROM ispadmin_dev.ip_pool
WHERE host_device_id = 8 AND is_eligible = 1;
```

Esperado: dispositivo **8** activo; pool lab típico `192.168.30.1/24` (`is_eligible=1`) ligado a MK2.

No usar `mikrotik_test` (id 7) ni MK1 (id 1, VLAN 1) para el flujo FIBER lab Gateway→ACS.

## Cómo verificar el tag `lab`

```bash
curl -s "http://127.0.0.1:7557/devices/?query=$(python3 -c 'import urllib.parse,json; print(urllib.parse.quote(json.dumps({"_tags":"lab"})))')"
```

O por SN:

```bash
curl -s 'http://127.0.0.1:7557/devices/?query=%7B%22_id%22%3A%7B%22%24regex%22%3A%22ZTEGDC47BFFD%22%7D%7D&projection=_id%2C_tags'
```

Si el dispositivo **no** tiene `lab` en `_tags`, **no** ejecutar writes.

## Túneles Mac → VPS (ejemplo)

```bash
# GenieACS NBI (+ UI 3000 si aplica)
ssh -f -N -L 7557:127.0.0.1:7557 -L 3000:127.0.0.1:3000 root@<VPS>

# Tomcat staging (ACS WAR)
ssh -f -N -L 8091:127.0.0.1:8081 root@<VPS>
```

Health ACS:

```bash
curl -s -H "X-Acs-Key: $ACS_API_KEY" \
  http://127.0.0.1:8091/ispadmin-staging-acs/api/acs/v1/health
```

## Gateway local apuntando a ACS VPS

```properties
olt.gateway.acs.enabled=true
olt.gateway.acs.internal-base-url=http://127.0.0.1:8091/ispadmin-staging-acs
olt.gateway.acs.api-key=${ACS_API_KEY}
```

Arranque Gateway (perfil Maven `oltgateway-war`, main `OltGatewayApplicationKt`), OLT real, writes on. Context path: `/ispadmin-oltgateway`.

En `application-local.properties` ya está documentado: MikroTik real MK2, sin mock.

## Perfiles TR-069 en ACS VPS (preflight)

`GET /api/acs/v1/profiles` debe devolver al menos `F6600R` (alias `F6600RV9.0.21`) antes de `POST .../onu/activate`.

Si está vacío, importar el CSV de fixture:

```bash
# body JSON: { "csv": "<contenido>", "importedBy": "lab", "aliases": ["F6600RV9.0.21"] }
curl -s -X POST -H "X-Acs-Key: $ACS_API_KEY" -H "Content-Type: application/json" \
  --data-binary @zte-f6600r.import.json \
  http://127.0.0.1:8091/ispadmin-staging-acs/api/acs/v1/profiles/import
```

Fixture: `src/test/resources/genieacs-exports/zte-f6600r.csv`.

**Bug conocido (staging):** `Tr069ModelProfileImportService.importCsv` llama `registry.reload()` **dentro** de `@Transactional`; el primer import puede dejar el registry en memoria vacío hasta un segundo import (o restart del WAR). Sintoma en activate: `cpeStatus=FAILED` con *“No hay perfiles TR-069 importados…”* aunque `GET /profiles` ya liste filas. Mitigación operativa: re-importar el mismo CSV y reintentar provision/`activate`.

## Criterio COMPLETE del ACS WAR

`POST /api/acs/v1/cpe/provision` → `CpeFacadeService` → [`Tr069ProvisioningService.provision`](../src/main/kotlin/com/dscorp/wispadmin/acs/genieacs/Tr069ProvisioningService.kt).

Tras SPV (WAN cliente + WiFi) hace GPV y poll de caché GenieACS. **COMPLETE** solo si en la WAN cliente (`genieacs.client-wan-index`, default 2 / path del perfil F6600R) se cumple todo:

- `ExternalIPAddress` igual a la IP del request
- `ConnectionStatus == Connected` (ignoreCase)
- SSIDs 2.4/5 iguales a los pedidos (si no van vacíos)

Timeouts: `genieacs.wait-timeout-ms` 90 s + `poll-interval-ms` 5 s en find **y** otra vez en verify. Timeout de verify → `PENDING` (no FAILED). `GET /cpe/{sn}/status` lee BD ACS, no reconsulta Genie en vivo.

Mensaje de éxito: `ONU configurada automáticamente por TR-069.`

## Resultado lab limpio 2026-09-06 (`ZTEGDC47BFFD`, WiFi `gateway123`)

Delete vía `POST /api/olt-gateway/onu/delete/gigafiber-ma5608t_1_6_115` + hard-clean `olt_mgr_onu` / `olt_activation_operation`. Autofind OK. Activate `Generic_1` / VLAN 100 / Routing / MK2 `192.168.30.240`.

| Fase | Tiempo | Estado |
|------|--------|--------|
| **T_auth** (`POST …/onu/activate` hasta `oltStatus=COMPLETE`) | **1.754 s** | `gigafiber-ma5608t_1_6_115` |
| **T_acs** (respuesta activate → `cpeStatus=COMPLETE`) | **15.642 s** | mensaje TR-069 OK |
| **T_total** | **17.396 s** | — |

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `gateway123` | `gateway123` |
| 5 GHz | `gateway123` | `gateway123` |

GenieACS `_lastInform=2026-09-06T15:48:11.146Z`; SSIDs confirmados. ONU OLT índice **115** (puede figurar `offline` justo tras reauth).

## Registro vía Core local (`POST /subscription`)

Core en `http://127.0.0.1:8082/ispadmin` (cliente Gateway, **sin** embebido OLT SSH):

```text
--server.port=8082
--gigafiber.subsystems.oltgateway.enabled=false
--olt.gateway.enabled=false
--olt.gateway.client-enabled=true
--olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin
```

ACS sigue en VPS (`8091`). MK2 id 8, NAP `42` (board 1 / port 6), plan `1`, place `2`.

Lab 2026-09-06 (SSID `core123`, 7 chars): suscripción **2332**, IP `192.168.30.249`. OLT y MikroTik **COMPLETE** en ~5 s. Primer TR-069 **FAILED** `KeyPassphrase 9007` (WPA exige passphrase ≥8). Re-provision ACS con `Core1234` → **COMPLETE**.

Lab 2026-09-06 (SSID `coreprueba123`, una pasada): Core local `:8082` + Gateway local `:8080` + **ACS VPS** `:8091`. ONU `ZTEGDC47BFFD` (tag `lab`), MK2 VLAN 100, NAP `42`. Suscripción **2333**, IP `192.168.30.19`.

| Fase | Tiempo | Estado |
|------|--------|--------|
| `POST /subscription` | **3.9 s** | OLT + MikroTik **COMPLETE**; TR-069 `PENDING` |
| Poll `registration-progress` | **~19 s** total | TR-069 **COMPLETE** (mensaje TR-069 OK) |

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `coreprueba123` | `coreprueba123` |
| 5 GHz | `coreprueba123` | `coreprueba123` |

La app debe enviar `wifiPassword24`/`wifiPassword5` de **al menos 8 caracteres**. No levantar ACS WAR local: el ACS es el de staging en VPS.

## Anti-patrones

- Levantar monolito Core “para tener ACS embebido” (ACS **no** va en el ComponentScan del Core).
- Levantar ACS local contra GenieACS VPS “por comodidad” cuando el ACS VPS ya está alcanzable por túnel.
- Usar ONUs de producción / sin tag `lab` en smokes destructivos.
- Asignar IP/colas contra MK1 o `mikrotik_test` en pruebas lab FIBER/VLAN 100.

## Relación con camino más corto

Sigue aplicando [pruebas-camino-mas-corto.md](./pruebas-camino-mas-corto.md): unit → live local OLT → staging deploy solo si hace falta el WAR en Tomcat.
