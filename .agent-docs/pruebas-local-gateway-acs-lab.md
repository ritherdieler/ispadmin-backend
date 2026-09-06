# Pruebas locales: Core + Gateway + ACS VPS + MK2 + ONU `lab`

**Obligatorio** cuando el usuario pida pruebas en local de alta FIBER, authorize/activate/delete OLT, o TR-069.

Regla Cursor: `gigafiber/.cursor/rules/olt-lab-acs-vps-local.mdc`. `AGENTS.md` (sección pruebas Gateway/ACS).

## Qué significa “pruebas en local”

| Pieza | Dónde corre | No hacer |
|-------|-------------|----------|
| **Core WAR** | Mac `:8082` `/ispadmin` | No desplegar staging “para probar”. No embeber OLT/ACS en el Core. |
| **Gateway WAR** | Mac `:8080` `/ispadmin` | No llamar SmartOLT cloud si `olt.provider.authorize=GATEWAY`. |
| **ACS WAR** | **VPS staging** por túnel `:8091` `/ispadmin-staging-acs` | **No** levantar `AcsApplication` local. |
| **GenieACS NBI** | VPS por túnel `:7557` | No GenieACS local. |
| **OLT** | Real `10.11.104.2` (SSH desde Gateway) | No saturar VTY con SSH extra (health del Gateway basta). |
| **MikroTik** | **MK2** `network_device.id=8`, VLAN **100** | No MK1 ni `mikrotik_test`. |
| **ONU** | Solo tag GenieACS **`lab`**. Canónica **`ZTEGDC47BFFD`** | Prohibido writes a ONUs de clientes. |

Cliente (curl, app, script) habla **solo con el Core**. El Core orquesta Gateway (OLT + ACS) y MK2.

```text
Cliente (curl / app)
  │ HTTP JWT
  ▼
Core WAR :8082 /ispadmin ──── HTTP MK ──────────────► MK2 id 8 VLAN 100
  │ HTTP activate (X-Olt-Gateway-Key)
  ▼
Gateway WAR :8080 /ispadmin ──── SSH CLI ───────────► OLT 10.11.104.2
  │ HTTP provision (X-Acs-Key)
  ▼
ACS VPS :8091 /ispadmin-staging-acs ──── NBI ───────► GenieACS :7557
```

Continua = HTTP/JWT/SSH. El Core **no** habla con ACS ni GenieACS.

Pasada canónica **2026-09-06** (SSID `coreprueba123`): suscripción **2333**, IP `192.168.30.19`, OLT+MK+TR-069 **COMPLETE** en ~19 s, una sola `POST /subscription`.

---

## Runbook (seguir en este orden)

No saltar al POST hasta health + tag `lab` + autofind + perfiles ACS. No deploy. No ACS local.

### 1. Túneles Mac → VPS

```bash
# GenieACS NBI
ssh -f -N -L 7557:127.0.0.1:7557 root@<VPS>
# Tomcat staging (ACS WAR)
ssh -f -N -L 8091:127.0.0.1:8081 root@<VPS>
```

Comprobar:

```bash
nc -z 127.0.0.1 7557 && nc -z 127.0.0.1 8091
curl -s -H "X-Acs-Key: $ACS_API_KEY" \
  http://127.0.0.1:8091/ispadmin-staging-acs/api/acs/v1/health
# esperado: {"status":"UP"}
```

`ACS_API_KEY` vive en el catálogo `.agent-docs/vps-secrets-management.md` (no commitear el valor).

### 2. Tag `lab` en GenieACS

```bash
curl -s 'http://127.0.0.1:7557/devices/?query=%7B%22_id%22%3A%7B%22%24regex%22%3A%22ZTEGDC47BFFD%22%7D%7D&projection=_id%2C_tags'
```

Esperado: `_id` `5872C9-F6600R-ZTEGDC47BFFD`, `_tags` contiene `lab`. Si no hay tag **`lab`**, parar.

### 3. Arrancar Gateway WAR local → ACS VPS

Main: `OltGatewayApplicationKt`. Puerto **8080**. Writes on. `olt.provider.authorize=GATEWAY`. JDBC schema **`dev_oltgateway`**.

Flags mínimas (secretos por env / `application-local.properties`, nunca en git):

```text
--server.port=8080
--olt.gateway.host=10.11.104.2
--olt.gateway.writes.enabled=true
--olt.gateway.acs.enabled=true
--olt.gateway.acs.internal-base-url=http://127.0.0.1:8091/ispadmin-staging-acs
--olt.gateway.acs.api-key=$ACS_API_KEY
--olt.provider.authorize=GATEWAY
```

Verificar en el log `Tomcat started ... context path`. En la pasada canónica fue **`/ispadmin`**. Alinear la URL del Core a ese context-path.

```bash
curl -s -H "X-Olt-Gateway-Key: $GKEY" \
  http://127.0.0.1:8080/ispadmin/api/olt-gateway/health
# esperado: "status":"UP","oltReachable":true
```

Si `oltReachable=false` con ping OK: parar Gateway, esperar **~8 s** (VTY Huawei), relanzar. No abrir SSH extra “de diagnóstico” (lockout `Reenter times have reached the upper limit`).

### 4. Arrancar Core WAR local → Gateway local

Main: `WispAdminApplicationKt`. Puerto **8082**. El Core es **cliente** del Gateway, no corre OLT SSH ni ACS.

```text
--server.port=8082
--gigafiber.subsystems.oltgateway.enabled=false
--olt.gateway.enabled=false
--olt.gateway.client-enabled=true
--olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin
--olt.gateway.api-key=$GKEY
--olt.service.mock.enabled=false
```

`application-dev.properties` deja `olt.service.mock.enabled=true` (ONUs `ALCL*`). Sin el override, el e2e Android abre el dropdown mock y no ve `ZTEGDC47BFFD`. En `application-local.properties` (perfil `local`) debe ir `olt.service.mock.enabled=false`.

Timeout Core→Gateway: `OltGatewayClientConfig.READ_TIMEOUT_MS = 180000` (cubre authorize SSH + ACS).

### 5. Perfiles TR-069 en ACS VPS (dos imports)

`GET /api/acs/v1/profiles` debe listar `F6600R` (alias `F6600RV9.0.21`).

Si falta, o si activate falla con *“No hay perfiles TR-069 importados…”* pese a que `GET /profiles` ya lista filas: importar el CSV **dos veces**.

Causa: `Tr069ModelProfileImportService.importCsv` hace `registry.reload()` **dentro** de `@Transactional`; el primer import puede dejar el registry en memoria vacío.

Fixture: `src/test/resources/genieacs-exports/zte-f6600r.csv`.

```bash
# body: { "csv": "<contenido>", "importedBy": "lab", "aliases": ["F6600RV9.0.21"] }
curl -s -X POST -H "X-Acs-Key: $ACS_API_KEY" -H "Content-Type: application/json" \
  --data-binary @/tmp/acs-import-f6600r.json \
  http://127.0.0.1:8091/ispadmin-staging-acs/api/acs/v1/profiles/import
# repetir el mismo POST una segunda vez
```

### 6. Limpiar la ONU lab (una pasada exige journal vacío + autofind)

SN `ZTEGDC47BFFD`.

1. `GET …/onu/get_onus_details_by_sn/{sn}` → si existe, `POST …/onu/delete/{unique_external_id}` (p. ej. `gigafiber-ma5608t_1_6_115`).
2. Borrar journal Gateway: `DELETE FROM dev_oltgateway.olt_activation_operation WHERE sn='ZTEGDC47BFFD'`. Un `DONE` con el mismo fingerprint hace **no-op** el activate.
3. Desligar Core: `UPDATE ispadmin_dev.subscription SET fiber_onu_sn=NULL WHERE fiber_onu_sn='ZTEGDC47BFFD'`.
4. Unique `olt_mgr_onu` **no ignora soft-delete**. Si el delete API deja conflicto de índice (`1-1-6-*`), hard-delete de la fila y FKs (`status_current`, `service_port`, `extra_vlan`; nullificar `task` / `audit_log`).

### 7. Esperar autofind

```bash
curl -s -H "X-Olt-Gateway-Key: $GKEY" \
  http://127.0.0.1:8080/ispadmin/api/olt-gateway/onu/unconfigured_onus
```

Debe aparecer `ZTEG-DC47BFFD` (o `5A544547DC47BFFD`) en board **1** / port **6**. Puede tardar unos segundos. No hacer POST hasta verla.

### 8. Alta por el Core (`POST /subscription`)

NAP lab **42** (board 1 / port 6), place **2**, plan **1**, technician existente, `hostDeviceId` **8**, `vlan` **`"100"`**, `installationType` **`FIBER`**.

- `clientRequestId`: UUID nuevo.
- `dni`: 8 dígitos que no existan.
- `onu.olt_id`: `gigafiber-ma5608t` (no el `olt_id` numérico de autofind).
- `onu.onu_type_name`: `F6600RV9.0.21`.
- **WiFi 2.4 y 5:** mismo SSID y password, **≥ 8 caracteres**. WPA en la ONU rechaza passphrase corta (`KeyPassphrase 9007`).

Login e2e Android: `dscorp` / `nohacker` (mismos que prod). La app envía **SHA-384** de la clave; el Core guarda `pbkdf2` de ese hex. El usuario debe ser **ADMIN** y **verified=1** (si no, la app se queda en cuenta no verificada y Espresso no ve el drawer).

Antes del login, el script Android corre `scripts/local-e2e-ensure-catalog.sh` (Core repo): upsert de `dscorp` y comprueba place `9 de octubre`, NAP `NO-001`, plan FIBER activo, MK2 `network_device.id=8` y pool eligible. No usar `dscorp`/`123456` del mockdata.

El POST bloquea en Gateway activate (OLT + ACS async). En la pasada canónica volvió en ~4 s con OLT+MK **COMPLETE** y TR-069 **PENDING**.

### 9. Poll hasta COMPLETE

```bash
GET http://127.0.0.1:8082/ispadmin/subscription/{id}/registration-progress
```

Éxito: `oltProvisionStatus=COMPLETE`, `mikrotikProvisionStatus=COMPLETE`, `tr069ProvisionStatus=COMPLETE`, mensaje `ONU configurada automáticamente por TR-069.`

COMPLETE ACS exige en WAN cliente (índice 2 / path F6600R): IP del alta, `ConnectionStatus=Connected`, SSIDs iguales a los pedidos. Timeout verify 90 s → `PENDING` (no FAILED).

Lanzar el POST+poll en **background** si puede pasar de ~1–2 min; cerrar el turno y reportar al aviso de fin de job.

### 10. Entregar WiFi al usuario

Si `tr069ProvisionStatus=COMPLETE`, en el mismo mensaje de cierre (antes de cualquier limpieza dura):

| Banda | Campos | Qué mostrar |
|-------|--------|-------------|
| 2.4 GHz | `wifiSsid24` / `wifiPassword24` | SSID **y** contraseña reales del alta |
| 5 GHz | `wifiSsid5` / `wifiPassword5` | SSID **y** contraseña reales del alta |

No dar la prueba por cerrada si solo se mencionan los SSID.

### 11. E2E con emulador Android (mismo stack)

Flavor **devDebug**. `BASE_URL=http://127.0.0.1:8080/ispadmin/`. El Core está en **8082**; el emulador llega con:

```bash
adb reverse tcp:8080 tcp:8082
```

No usar NAP `NO-001` / lugar `9 de octubre` (puerto GPON distinto). Lab local:

| Campo | Valor |
|-------|--------|
| Login / WiFi / lugar | Los mismos que `e2e_register_fiber_espresso.sh` (prod): `dscorp` / `nohacker`, `mimiwifi` / `MimiWifi24pass`, `9 de octubre`, geo `-11.2156,-77.4107`, NAP `NO-001` |
| ONU | `ZTEGDC47BFFD` (única con tag `lab`; el script de prod toma la primera ONU) |
| Paquete | `com.dscorp.ispadmin.dev` (BASE_URL local; no prodDebug) |

Desde el repo Android (background; no `AwaitShell`):

```bash
./scripts/e2e_register_fiber_local_espresso.sh
```

No usar `e2e_register_fiber_espresso.sh` (prodDebug) ni el script staging contra este Core.

---

## MK2 en BD local

```sql
SELECT id, name, ip_address, vlan_id, disabled
FROM ispadmin_dev.network_device
WHERE id = 8 OR name LIKE '%CCR 2%';

SELECT id, ip_segment, host_device_id, is_eligible
FROM ispadmin_dev.ip_pool
WHERE host_device_id = 8 AND is_eligible = 1;
```

Esperado: dispositivo **8** activo; pool `192.168.30.1/24` (`is_eligible=1`).

---

## Criterio COMPLETE del ACS WAR

`POST /api/acs/v1/cpe/provision` (lo llama el Gateway, no el Core) → `CpeFacadeService` → `Tr069ProvisioningService.provision`.

Tras SPV (WAN cliente + WiFi) hace GPV y poll de caché GenieACS.

Timeouts: `genieacs.wait-timeout-ms` 90 s + `poll-interval-ms` 5 s en find **y** otra vez en verify. `GET /cpe/{sn}/status` lee BD ACS, no reconsulta Genie en vivo.

---

## Anti-patrones

- Levantar **ACS WAR local** (`AcsApplication` `:8090`) “porque el usuario dijo WAR local”. WARs locales = **Core + Gateway**. ACS = VPS.
- Levantar monolito Core “para tener ACS embebido” (ACS **no** va en el ComponentScan del Core).
- Deploy staging para un cambio que ya se puede ejercer contra OLT/ACS desde la Mac.
- Password WiFi de **menos de 8** caracteres.
- Re-probar activate sin borrar `olt_activation_operation` (no-op).
- Usar ONUs sin tag `lab`, MK1 o `mikrotik_test`.
- SSH directo a la OLT en paralelo al Gateway (llena VTY / lockout de `oltadmin`).

---

## Relación con camino más corto

Sigue aplicando [pruebas-camino-mas-corto.md](./pruebas-camino-mas-corto.md): unit → este runbook local → staging deploy **solo** si el fallo es bake/overlay/Tomcat en VPS.

---

## Apéndice — resultados lab 2026-09-06

### Gateway activate directo (`gateway123`)

Delete `POST /api/olt-gateway/onu/delete/gigafiber-ma5608t_1_6_115`. Activate `Generic_1` / VLAN 100 / Routing / MK2 `192.168.30.240`.

| Fase | Tiempo | Estado |
|------|--------|--------|
| T_auth | 1.754 s | `gigafiber-ma5608t_1_6_115` |
| T_acs | 15.642 s | `cpeStatus=COMPLETE` |
| T_total | 17.396 s | — |

WiFi 2.4/5: `gateway123` / `gateway123`.

### Core `POST /subscription` (`core123`, 7 chars) — no usar

Suscripción **2332**, IP `192.168.30.249`. OLT+MK COMPLETE. TR-069 **FAILED** `KeyPassphrase 9007`. Re-provision con passphrase ≥8 → COMPLETE. No repetir.

### Core una pasada (`coreprueba123`) — patrón a copiar

Suscripción **2333**, IP `192.168.30.19`. NAP 42, MK2 VLAN 100.

| Fase | Tiempo | Estado |
|------|--------|--------|
| `POST /subscription` | 3.9 s | OLT + MikroTik COMPLETE; TR-069 PENDING |
| Poll `registration-progress` | ~19 s total | TR-069 COMPLETE |

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `coreprueba123` | `coreprueba123` |
| 5 GHz | `coreprueba123` | `coreprueba123` |
