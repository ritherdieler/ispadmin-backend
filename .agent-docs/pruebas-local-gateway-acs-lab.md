# Pruebas locales: Core + Gateway + ACS VPS + MK2 + ONU `lab`

**Obligatorio** cuando el usuario pida pruebas en local de alta FIBER, authorize/activate/delete OLT, o TR-069.

Regla Cursor: `gigafiber/.cursor/rules/olt-lab-acs-vps-local.mdc`. `AGENTS.md` (sección pruebas Gateway/ACS).

## Qué significa “pruebas en local”

| Pieza | Dónde corre | No hacer |
|-------|-------------|----------|
| **WAR único** | Mac `:8082` `/ispadmin` (`./scripts/run-local-prestaging.sh start` o `./gradlew :core:bootRun`) | No desplegar staging “para probar”. No arrancar Gateway/ACS en otros puertos. |
| **ACS** | In-process en el WAR local (HTTP loopback al mismo context-path) | **No** levantar un segundo `AcsApplication`. |
| **GenieACS NBI** | VPS por túnel `:7557` | No GenieACS local. |
| **OLT** | Real `10.11.104.2`. El SSH lo abre **un** Gateway (stopgap: el embebido de prod). Prestaging Mac tiene `olt.gateway.enabled=false` y habla por HTTP al túnel `:8092` | No abrir SSH desde la Mac. No segundo `OltCliBus`. |
| **MikroTik** | **MK2** `network_device.id=8`, VLAN **100** | No MK1 ni `mikrotik_test`. |
| **ONU** | Solo tag GenieACS **`lab`**. Canónica **`ZTEGDC47BFFD`**. VSOL lab **`VSOL0031C0B6`** | Prohibido writes a ONUs de clientes. |

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

## Stopgap 2026-09-21

Prestaging **no** abre SSH a `10.11.104.2`. `olt.gateway.enabled=false`. El alta local necesita un túnel HTTP al Gateway que ya tiene el VTY (prod embebido):

```bash
ssh -L 8092:127.0.0.1:8080 <vps>
# Core local: olt.gateway.internal-base-url=http://127.0.0.1:8092/ispadmin
# Header que manda el Core: X-Olt-Gateway-Key=$OLT_GATEWAY_STAGING_API_KEY y X-Gigafiber-Env: lpstg
```

`free-olt-ssh` en la Mac deja de ser prerrequisito del alta. Sigue siendo obligatorio en el host del Gateway antes de abrir un SSH manual a la OLT. Sin el túnel y sin `OLT_GATEWAY_STAGING_API_KEY` en el dueño, el POST local no autoriza.

## Runbook (seguir en este orden)

No saltar al POST hasta health + tag `lab` + autofind + perfiles ACS. No deploy. No ACS local. No SSH a la OLT desde esta Mac.

### 1. Túneles Mac → VPS

Un solo proceso. Siempre cierra túneles previos en `7557`/`8091` (listeners y `ssh -L` colgados) y abre uno nuevo. Credenciales: `ispadmin-backend/scripts/deploy.config.local` (`VPS_HOST`, `DEPLOY_SSH_PASSWORD`). No commitear el valor.

```bash
/Users/sergiocarrillo/gigafiber/start-genieacs-tunnel.sh
```

Forwards: `127.0.0.1:7557` → GenieACS NBI; `127.0.0.1:8091` → Tomcat staging `:8081` (ACS WAR). `start` y el default hacen lo mismo: cerrar y abrir. `stop` / `status` también.

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

Si `oltReachable=false` con ping OK: `./scripts/run-local-prestaging.sh free-olt-ssh`, esperar **~8 s** (VTY Huawei), relanzar. **Antes de cualquier SSH nuevo** a `10.11.104.2:22`, matar conexiones TCP previas desde esta Mac (`free-olt-ssh`). No abrir SSH extra “de diagnóstico” en paralelo al Gateway (lockout `Reenter times have reached the upper limit`).

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

### 5. Provisions GenieACS (no CSV)

TR-069 ya no importa perfiles CSV. El ACS encola scripts por HTTP NBI (`NamedCpeProvisioner`): `gf-pppoe-wan2-poc` (PPPoE + WiFi) o `gf-static-wan2-poc` (STATIC_IP, reemplaza leftover PPP). Layouts: `F6600R`, `V2804AX15T`, `VSOLVA74`.

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

Ejecutar el POST+poll con **salida visible** y mantener el turno abierto con `AwaitShell` hasta COMPLETE/timeout.

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

Desde el repo Android (consola visible; `AwaitShell` hasta el final):

```bash
./scripts/e2e_register_fiber_local_espresso.sh --cleanup-mode ask
```

`--cleanup-mode auto` (default) limpia al terminar. `ask` pregunta `¿Ejecutar hard cleanup ahora? [s/N]`. `skip` / `--no-cleanup` no limpia. Detalle: [e2e-cleanup-prompt.md](./e2e-cleanup-prompt.md).

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

### Prestaging: MK2 real, no mock

`./scripts/run-local-prestaging.sh` activa `dev,local-prestaging`. `application-dev.properties` deja `mikrotik.connection.mock.enabled=true`; sin override, `PppoeAccessService.ensureSecret` no abre sesión con el CCR (`core.mk.pppoe` ~4 ms) y MikroTik COMPLETE es falso.

En `application-local-prestaging.properties`:

```properties
olt.service.mock.enabled=false
mikrotik.connection.mock.enabled=false
```

No poner `mikrotik.connection.override.ip`: el alta usa `hostDeviceId` **8** (MK2 `38.224.231.4`). No `mikrotik_test` ni MK1.

---

## Criterio COMPLETE del ACS WAR

`POST /api/acs/v1/cpe/provision` (lo llama el Gateway, no el Core) → `CpeFacadeService` → `NamedCpeProvisioner.provision`.

El provisioner encola el script GenieACS por NBI HTTP y espera SSIDs / WAN según el layout.

Timeouts: `genieacs.wait-timeout-ms` 90 s + `poll-interval-ms` 5 s en find **y** otra vez en verify. `GET /cpe/{sn}/status` lee BD ACS, no reconsulta Genie en vivo.

---

## Anti-patrones

- Levantar **ACS WAR local** (`AcsApplication` `:8090`) “porque el usuario dijo WAR local”. WARs locales = **Core + Gateway**. ACS = VPS.
- Levantar monolito Core “para tener ACS embebido” (ACS **no** va en el ComponentScan del Core).
- Deploy staging para un cambio que ya se puede ejercer contra OLT/ACS desde la Mac.
- Password WiFi de **menos de 8** caracteres.
- Re-probar activate sin borrar `olt_activation_operation` (no-op).
- Usar ONUs sin tag `lab`, MK1 o `mikrotik_test`.
- Prestaging con `mikrotik.connection.mock.enabled=true` (el perfil `dev` lo enciende; hay que apagarlo en `application-local-prestaging.properties`).
- SSH directo a la OLT en paralelo al Gateway (llena VTY / lockout de `oltadmin`).

---

## Ambiente `local-prestaging`

Opt-in. **No** reemplaza el runbook canónico de arriba (ACS VPS `:8091`). Existe **solo** cuando el proceso arranca con el perfil Spring `local-prestaging`. No edita `application-dev|staging|prod|acs|oltgateway.properties`. Los `main()` de Gateway/ACS siguen igual en VPS (`prod,oltgateway` / `acs`); solo omiten esos perfiles extra si los args o `SPRING_PROFILES_ACTIVE` incluyen `local-prestaging` (si `prod` queda al final, JDBC pasa a `mysql:3306/ispadmin`).

| Pieza | Dónde | Perfiles | Schema JDBC |
|-------|--------|----------|-------------|
| Core | Mac `:8082` `/ispadmin` | `local-prestaging` | `ispadmin_prestaging` |
| Gateway | Mac `:8080` `/ispadmin` | `oltgateway,local-prestaging` (`main()` **no** añade `prod` si el arranque pide este perfil; el WAR VPS en `configure()` sigue `prod,oltgateway`) | `prestaging_oltgateway` |
| ACS | Mac `:8090` `/ispadmin-acs` | `acs,local-prestaging` (`main()` **no** fuerza `acs` extra si ya viene prestaging; el WAR VPS en `configure()` sigue `acs`) | `prestaging_acs` |
| GenieACS NBI | Túnel `127.0.0.1:7557` → VPS | — | — |
| OLT | SSH LAN `10.11.104.2` desde el Gateway **en la Mac** | — | — |
| ONU | Solo tag GenieACS `lab`. Canónica `ZTEGDC47BFFD` | — | — |

El único tráfico al VPS es NBI `:7557`. No usar `:8091`, `ispadmin-staging-acs`, Gateway en Tomcat VPS, ni `olt.provider.authorize=SMARTOLT`.

```text
Cliente / app
  │ HTTP JWT
  ▼
Core :8082  (local-prestaging)
  │ HTTP activate (localhost)
  ▼
Gateway :8080  (oltgateway + local-prestaging)
  │ SSH LAN 10.11.104.2
  ▼
OLT MA5608T

Gateway ──HTTP localhost──► ACS :8090 (acs + local-prestaging)
                              │ NBI túnel
                              ▼
                         GenieACS VPS :7557
                              ▲
ONU lab CWMP (VLAN 1000) ─────┘
```

Tag: `gigafiber.environment.tag=lpstg` (no `stg`, para no activar reglas de colas/VLAN de staging).

### Archivos

| Archivo | Git |
|---------|-----|
| `src/main/resources/application-local-prestaging.properties` | Versionado, sin secretos |
| `src/main/resources/application-local-prestaging.secrets.properties.example` | Versionado, claves vacías |
| `src/main/resources/application-local-prestaging.secrets.properties` | **gitignore**. Copiar el example o claves desde `application-local.properties` |
| `scripts/run-local-prestaging.sh` | Opt-in; no cambia defaults de VPS |

Este ambiente **no** lee `application-local.properties`.

### Arranque

```bash
# overlay de passwords (una vez)
cp src/main/resources/application-local-prestaging.secrets.properties.example \
   src/main/resources/application-local-prestaging.secrets.properties
# rellenar MySQL / OLT / ACS_API_KEY (no commitear)

# túnel NBI (el script canónico también abre :8091; este ambiente no lo usa)
/Users/sergiocarrillo/gigafiber/start-genieacs-tunnel.sh

# opcional: matar listeners de los tres WARs (no toca túneles :7557 ni :8091)
./scripts/run-local-prestaging.sh stop

# tres terminales (cada uno libera su puerto y arranca = restart de ese WAR)
./scripts/run-local-prestaging.sh check
./scripts/run-local-prestaging.sh core
./scripts/run-local-prestaging.sh gateway
./scripts/run-local-prestaging.sh acs
```

`stop` y el arranque de cada WAR matan **solo** listeners TCP (`lsof -iTCP:PORT -sTCP:LISTEN`) en **8080 / 8082 / 8090**. SIGTERM y, si sigue el listener, SIGKILL. No `pkill` de java/mvn. **No** mata `:7557` (GenieACS NBI) ni `:8091` (ACS VPS). `core|gateway|acs` y `restart core|gateway|acs` hacen kill+start de ese puerto.

Equivalente Maven:

```text
Core:    --spring.profiles.active=local-prestaging --server.port=8082
Gateway: --spring.profiles.active=oltgateway,local-prestaging --server.port=8080
ACS:     --spring.profiles.active=acs,local-prestaging --server.port=8090
```

Antes de un alta: `ping -c 1 10.11.104.2` y health del Gateway con `oltReachable=true`. El log debe mostrar SSH a `10.11.104.2`. Si `oltReachable=false` con ping OK: `./scripts/run-local-prestaging.sh free-olt-ssh` (solo con el WAR caído; si el Java está up, eso mata el proceso), esperar ~8 s VTY y relanzar **este** Gateway/prestaging local. ONU `lab` only. Antes de cualquier SSH nuevo a la OLT: matar TCP previos (`free-olt-ssh` / `start`).

Schemas locales: `ispadmin_prestaging`, `prestaging_oltgateway`, `prestaging_acs` (el JDBC lleva `createDatabaseIfNotExist=true`). El catálogo e2e de `scripts/local-e2e-ensure-catalog.sh` apunta a `ispadmin_dev`; este schema nace vacío hasta que se copie o se siembre.

Hallazgos boot **2026-09-13**: el plugin Maven usa `<mainClass>${start-class}</mainClass>`; el script debe pasar `-Dstart-class=…ApplicationKt` (sin eso ACS/Gateway levantan el Core). Schema ACS/Core vacío: Flyway de Core (`V2` WhatsApp / `V39` netdiag) y ACS (`V2` altera `cpe_record`) fallan; prestaging deja `spring.flyway.enabled=false` en Core/ACS y `classpath:db/oltgateway` en Gateway. Core necesita placeholders de `application-dev` (`custom.username`, Firebase): el script usa `dev,local-prestaging` (prestaging gana JDBC/OLT/ACS). Alta lab: NAP **42** (board 1 / port 6), no `NO-001` (port 8).

### E2E alta FIBER (`local-prestaging`)

Curl contra el **Core** `:8082` `/ispadmin`. No Gateway activate, no ACS VPS `:8091`. NAP **42**, MK2 `hostDeviceId` 8, VLAN 100, ONU `ZTEGDC47BFFD` (tag `lab`). Schema `ispadmin_prestaging`.

`/scripts/` está gitignored: al versionar, `git add -f scripts/e2e_register_fiber_local_prestaging.sh`.

WARs ya arriba (`./scripts/run-local-prestaging.sh core|gateway|acs`) y túnel NBI `:7557`. Overlay: `application-local-prestaging.secrets.properties` (no `application-local.properties`).

```bash
./scripts/e2e_register_fiber_local_prestaging.sh check

./scripts/e2e_register_fiber_local_prestaging.sh \
  --wifi-ssid 'mimiwifi' --wifi-pass 'MimiWifi24pass' \
  --cleanup-mode ask
```

`--cleanup-mode skip` es el default (no borra la suscripción). `auto` limpia solo la ONU lab. Poll `GET /subscription/{id}/registration-progress` hasta `tr069ProvisionStatus=COMPLETE`. Si COMPLETE, imprime SSID **y** password 2.4 y 5 GHz (`{ssid} - 5G`), marca `subscription_acs.lab=1` y dispara `POST /api/acs/v1/cpe/inform-notify` (series 360 vía Redis `lpstg`). Wrapper fino Android (opcional): `IpsAdmin-android app/scripts/e2e_register_fiber_local_prestaging.sh` (delega al backend; no cambia espresso local/staging).

### 360 Wi‑Fi con Redis (prestaging)

El overlay `local-prestaging` deja `gigafiber.redis.enabled=true` y namespace `lpstg`. Redis local: `./scripts/redis-local.sh` (`127.0.0.1:6379`, sin password). `run-local-prestaging.sh start` lo levanta si `:6379` está caído. El consumer 360 corre con Redis ON aunque `gigafiber.scheduling.enabled=false`.

Camino: `POST /api/acs/v1/cpe/inform-notify` (NBI túnel `:7557`) → Gateway XADD `cpe.inform` → `HealthSnapshotConsumer` → `acs_wifi_*`. El ext de GenieACS en el VPS **no** llega al ACS local; hay que notificar a `:8082`:

```bash
./scripts/run-local-prestaging.sh inform-notify ZTEGDC47BFFD
```

`GET /subscription/{id}/service-health` (JWT) lee series. Óptica SNMP sigue apagada. Tráfico **sí** corre in-process (`gigafiber.subsystems.traffic.enabled=true`); el scheduler de billing sigue off — el poll es `POST /api/traffic/v1/admin/poll`.

### Tráfico STATIC_IP (wireless, MK2)

Cola simple `target=IP/32`. No usa ONU ni OLT. Fixture:

```bash
./scripts/run-local-prestaging.sh start
./scripts/prestaging-static-ip-traffic-lab.sh all
```

El script siembra un plan `WIRELESS` en `ispadmin_prestaging`, da de alta `LAB` / `STATICIP` en MK2 (`hostDeviceId` 8), comprueba que el directorio emite **solo IP** y dispara un poll. No MK1. Detalle: [traffic-directorio-access-mode-2026-09-15.md](./traffic-directorio-access-mode-2026-09-15.md).

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
