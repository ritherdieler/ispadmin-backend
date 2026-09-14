# Migración IP estática → PPPoE (Core)

Runbook del orquestador en el Core. Clientes externos (Android, backoffice) hablan **solo con el Core**. Core ↔ ACS y Core ↔ Gateway por HTTP. Sin JDBC cruzado.

## Estado

| Fase | Alcance | Estado |
|------|---------|--------|
| 0 | Cobranza PPPoE (`usesPppoe()` en cut/restore/cancel) | Código |
| 1 | Perfiles `GF-{dl}-{ul}`, comment, crear si falta | Código |
| 2 | Flyway `V53__subscription_access_migration.sql` | Código |
| 3 | Elegibilidad + abort dual-WAN | Código |
| 4 | Cliente HTTP Core → ACS (`wispadmin/acsclient`) | Código |
| 5 | `AccessMigrationService` | Código |
| 6 | Endpoints Core + `SubscriptionDto` | Código |
| 7 | Job cuarentena 7 días | Código |
| 8 | Telemetría live sin IP (`pppoe-{username}`) | Código |
| 9 | App Android | Otro agente |
| 10 | Deploy staging + validación live OLT/GenieACS | **PASS** 2026-09-11 (lab 2398 → `QUARANTINE`) |
| 10b | ACS staging vparams (`Gf*`) + smoke re-provisión 2398 | **PASS** 2026-09-11 (`COMPLETE`, sin paths) |
| 10c | Smoke vparams F6600R lab `ZTEGDC47BFFD` | **FAIL** 21:38: ACS `FAILED` (SPV 202, sin COMPLETE falso). CPE sin Inform desde 01:51Z; sin `WANPPP` ni sesión `10.64`. WANIP.1 + WiFi intactos. Lab usable |
| 10d | Re-authorize lab `ZTEGDC47BFFD` (autofind → Core) | **Authorize OK** 2026-09-12 02:44Z. Estaba en autofind `0/1/6`. Core staging `POST /onu/authorize` Generic_1 VLAN 100 → `gigafiber-ma5608t_1_6_120` (no lineprofile 12). Inform fresco + CR `192.168.255.236:58000` HTTP 200. Smoke PPPoE **no cerrado**: túneles NBI/ACS cayeron y SSH VPS rechazó password. ONU **online**. Lab usable |
| 11 | Esta documentación | Código |

## Destino

Tras migrar: `accessMode=PPPOE_DYNAMIC`, secret en MK2, perfil `GF-{dl}-{ul}`, sesión en pool `10.64.*`, VLAN de servicio 100, gestión en provisioning-255 (`192.168.252-255.x`). La IP estática se conserva en cuarentena y se libera al día 7.

## Elegibilidad

FIBER + `STATIC_IP` + ACTIVE + SN + IP válida + plan con velocidades (`upload>0`; INTERNET 60 → `GF-25-25`) + modelo con ruta WANPPP (V2804AX15T / F6600R) + ConnectionRequestURL en provisioning-255 (`192.168.252-255.x`) **o** gestión lab ACS `10.20.0.0/22` + gestión **distinta** de la IP de servicio.

Excluidos: WIRELESS, TV, IP inválida, CPE gestionado por IP de servicio, plan `upload=0`.

Si `replacedClientWanIpPath` sería `null` (WANIP y WANPPP no comparten WCD) el orquestador **aborta** para no dejar dos WAN activas. Excepción: si ACS staging ya usa VirtualParameters (`genieacs.vparams.enabled=true`), `access-layout` devuelve paths nulos y `hasPppPath=true` para V2804AX15T / F6600R; el guard **no aborta** y el JS GenieACS evita el dual-WAN. La migración sigue llamando el mismo `POST /api/acs/v1/cpe/provision` (contrato HTTP igual).

## Endpoints Core

| Método | Ruta | Qué hace |
|--------|--------|----------|
| `POST /subscription/{id}/access-migration` | Lanza (o retoma) la máquina de estados | 400 si no es elegible o dual-WAN |
| `GET /subscription/{id}/access-migration` | Progreso de la fila más reciente | 404 si no hay fila |
| `GET /subscription/access-migration/eligible` | `{ items: [...] }` con `name`, `onuSn`, `productClass`, `planName`, `reason`, `eligible` | |
| `GET /subscription/{id}` | `accessMode`, `pppoeUsername`, `accessMigrationStage` y objeto anidado `accessMigration` | |
| `POST /admin/access-migration/quarantine/finish-due` | Cierra cuarentenas vencidas (ADMIN). El scheduler global de staging sigue off | |

`AccessMigrationProgressDto`: `subscriptionId`, `stage`, `attempt`, `pppoeUsername`, `failureReason`, `quarantineUntil`, `done`, `message`. `done=true` en `QUARANTINE` / `DONE` / `FAILED_*` (la app deja de hacer poll; el job de 7 días sigue siendo `isActive()` en el Core).

## Máquina de estados

`ELIGIBLE` → `OLT_READY` (service-port VLAN 100) → `SECRET_READY` (secret MK + perfil) → `CPE_APPLIED` (ACS provision **sin WiFi**, VLAN 100) → `VERIFIED` (`/ppp/active` en `10.64.*` + Inform posterior) → `QUEUE_CLEARED` (borra cola por IP) → `QUARANTINE` (`accessMode=PPPOE_DYNAMIC`, no libera IP) → `DONE` (job: `undo` VLAN 1, limpia listas/cola, `ip=null`).

Fallo en `CPE_APPLIED`/`VERIFIED` con Inform < 15 min → reprovisión estática → `FAILED_REVERTED`. Si el CPE no informa → `FAILED_STRANDED`.

## Propiedades (nombres, no valores)

| Nombre | Enlaza | Dónde |
|--------|--------|--------|
| `ACS_API_KEY` | `acs.api-key` | `/opt/gigafiber/.env`; header `X-Acs-Key` |
| `ACS_CLIENT_ENABLED` | `acs.client-enabled` | Prod env; staging Core lo hornea `true` |
| `ACS_INTERNAL_BASE_URL` | `acs.internal-base-url` | **No** en `.env` compartido. Staging: `http://127.0.0.1:8080/ispadmin-staging-acs` |
| `PPPOE_MIGRATION_QUARANTINE_DAYS` | `pppoe.migration.quarantine-days` | Default 7; no es secreto |
| `pppoe.profile.remote-pool` / `local-address` | `PPPOE-DINAMICO` / `10.64.0.1` | No secretos |

Staging Core tiene `pppoe.new-subscriptions.enabled=true` y `acs.client-enabled=true`. Satélites **no** heredan `application-staging.properties`. `gigafiber.scheduling.enabled=false` en staging: el job de cuarentena **no** corre solo; invocarlo con `POST /admin/access-migration/quarantine/finish-due`. No encender el scheduler global solo por este job.

`--with oltgateway` hornea `olt.gateway.client-enabled=true` (cliente HTTP, no embebe el WAR). `--with acs` hornea `acs.client-enabled=true`. El default de `application-staging.properties` deja `olt.gateway.client-enabled=false` a propósito.

## Fase 10 — piloto staging 2026-09-11 **PASS**

Lab: sub **2398**, SN **VSOL0031C0B6** (V2804AX15T, tag `lab`), MK2 id 8, VLAN servicio 100. No se tocó VLAN 1000 / WCD.1. No commits. No `finish-due` (el lab necesita service-port VLAN 1000+100).

### Deploy

- Overlay Core vivo: `acs.client-enabled=true`, `olt.gateway.client-enabled=true`, `gigafiber.subsystems.servicehealth.enabled=true`.
- Health: VPS `http://127.0.0.1:8081/ispadmin-staging/` **200**, HTTPS público **200**, ACS **200**, Gateway **200**.
- `--with` **debe** incluir `servicehealth`. Sin eso el Core no arranca (`FileNotFoundException` `AcsSubscriptionPort`).
- Flyway ACS no aplicó `V4` (baseline 38). Paths PPP one-shot: V2804AX15T `...WCD.2.WANPPPConnection.1`, F6600R `...WCD.1.WANPPPConnection.2`.
- Elegibilidad lab: CR `10.20.0.2` cuenta como gestión dedicada (`10.20.0.0/22`), no solo provisioning-255.

### Migración 2398

| Paso | Resultado |
|------|-----------|
| Revert SQL | `STATIC_IP` + `192.168.250.40` + pool 250 |
| ACS provision estática | `COMPLETE` (sin WiFi ni PPP en el body) |
| `POST /subscription/2398/access-migration` | 200, arranca `ELIGIBLE` attempt 1 |
| Poll | `OLT_READY` → `SECRET_READY` → `QUARANTINE` en ~40 s |
| Sesión | `pppoe_provision_status=SESSION_UP`, `pppoe_last_ip=10.64.0.2`, user `gf2398` |
| Fila | `QUARANTINE` hasta `2026-09-18 14:24:39`, `previous_ip=192.168.250.40`, sin `failure_reason` |
| CR / VLAN 1000 | Sigue `http://10.20.0.2:7547/tr069` |
| WiFi | No se escribió en ACS. Core sigue `lab-vsol-e2e-24` / `lab-vsol-e2e-24 - 5G`. No se rotó passphrase. |

`GET /subscription/access-migration/eligible` devolvió 0 ítems en el momento del POST; el POST al id concreto sí fue elegible.

### Re-provisión vparams 2026-09-11 **PASS**

Misma lab 2398, ya `PPPOE_DYNAMIC`. ACS staging con `genieacs.vparams.enabled=true`. `POST /api/acs/v1/cpe/provision` sin SSIDs, VLAN 100. `access-layout` sin paths. Tras corregir `_deviceId` en el JS de vparams: `COMPLETE` en ~8 s. `GfInternetStatus` = connected / `10.64.0.2`. Sesión `gf2398`. CR `10.20.0.2`. WiFi intacto (`puppy` / `puppy - 5G`; no se mandaron SSIDs). VLAN 1000 / WCD.1 no tocados. Cola NBI de la lab vacía al cerrar.

### Smoke vparams F6600R 2026-09-11 **FAIL** apply PPPoE

Lab `ZTEGDC47BFFD` (`5872C9-F6600R-ZTEGDC47BFFD`, tag `lab`). GPV `GfInternetStatus`/`GfWifiStatus` HTTP 200: el JS mapea F6600R. Internet actual = WANIP.2 `192.168.250.22`; gestión WANIP.1 `192.168.255.236` intacta. WiFi `lab-zte-e2e-24` / `lab-zte-e2e-24 - 5G` intactos. No hay `WANPPPConnection` en WCD.1; SPV `GfApplyInternetPppoe` con secret de prueba `gflabzte` (pool `PPPOE-STG`) quedó 202 + fault. ACS `POST /cpe/provision` devolvió `COMPLETE` por cache (IP estática residual). Tasks/faults de este device purgados. Detalle: `.agent-docs/tr069-perfiles-gobierno-acs.md`.

Fix TDD posterior (sin commits): AddObject WANPPP + `VparamProvisioner` sin COMPLETE si IP ≠ `10.64.*` o SPV ≠ 200. Re-smoke 21:38 **FAIL**: ACS `FAILED` por SPV 202 (CPE sin Inform 01:51Z). WANIP.1 + WiFi intactos. Lab usable.

Add-then-set 2026-09-12: F6600R `slimPpp` en dos SET (creds → VLAN/Enable). `VparamProvisioner` reintenta `pending`. Node 22/22, `VparamProvisionerTest` 6/6. Reset lab + reprovision ACS en marcha. Detalle: `.agent-docs/tr069-perfiles-gobierno-acs.md`.

### Rollback (documentado, no experimentado contra el lab vivo)

- Fallo en `CPE_APPLIED`/`VERIFIED` con Inform &lt; 15 min → reprovisión estática → `FAILED_REVERTED`.
- Si el CPE no informa → `FAILED_STRANDED`.
- **No** lanzar `POST /admin/access-migration/quarantine/finish-due` sobre 2398: quitaría el service-port de gestión y dejaría el lab inutilizable.
- Tras `SUCCESS`/`QUARANTINE` no forzar rollback live.

## Cobranza

`PppoeAccessService.cut` / `restore` aplican a `usesPppoe()` (FIXED y DYNAMIC). `MikrotikPaymentReactivationHandler` restaura PPPoE o quita address-lists según `accessMode`. Cancelación sin IP corta por perfil `GF-CORTE`.
