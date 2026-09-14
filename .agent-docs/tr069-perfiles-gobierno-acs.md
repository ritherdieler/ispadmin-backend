# Perfiles TR-069: gobierno del ACS WAR

Fecha: 2026-09-11.

Invariantes: sin JDBC Core↔ACS; clientes externos solo Core; ACS no importa `wispadmin.*`. Test: `CrossSchemaJdbcForbiddenTest`.

## VirtualParameters GenieACS (staging)

El contrato HTTP Core→ACS **no cambia** (`POST /api/acs/v1/cpe/provision` sigue siendo `CpeProvisionCommand`). Con `genieacs.vparams.enabled=true` el ACS WAR deja de construir SPV desde `tr069_model_profile` y habla GenieACS con vparams `Gf*` (JSON string atómico).

| Intent | Vparam | Quién |
|--------|--------|--------|
| Internet PPPoE | `VirtualParameters.GfApplyInternetPppoe` | Alta PPPoE y migración `applyCpe` |
| Internet estático | `VirtualParameters.GfApplyInternetStatic` | Rollback / alta STATIC_IP |
| WiFi | `VirtualParameters.GfSetWifi` | Alta con SSIDs |
| Reboot | `VirtualParameters.GfReboot` | `POST /cpe/{sn}/reboot` |
| Estado | `GfInternetStatus`, `GfWifiStatus` | GPV de verificación; `access-layout` y `wifi-refresh` |

JS dueño del mapeo: `scripts/genieacs/virtual-parameters/` (V2804AX15T WCD.2 / WLAN.5+.1; F6600R WCD.1 PPP.2). `GfApplyInternetPppoe` hace **AddObject** de `WANPPPConnection` en el WCD de internet (`{path: Date.now()}` + `{path: N}`): F6600R `...WCD.1.WANPPPConnection.` (N=2), VSOL `...WCD.2.WANPPPConnection.` (N=1, nunca WCD.1). **Add-then-set:** si la instancia PPP aún no está en el modelo (Username/Enable ausentes), el primer ciclo solo AddObject y devuelve `{ok:true,pending:"addObject"}`; Username/Password/Enable y el disable de `WANIPConnection` de internet van en el ciclo siguiente (mismo SET GenieACS si re-evalúa, o segundo SPV). F6600R nunca toca `WANIPConnection.1` (gestión ACS). Otro product class → error, sin SPV. Nunca escribe la WAN de gestión (CR `10.20.x` o `192.168.252-255`).

`VparamProvisioner` (ACS WAR): PPPoE **no** da `COMPLETE` si el SPV de `GfApplyInternetPppoe` no es HTTP **200**, ni si `GfInternetStatus.ip` no está en el pool `10.64.*`. IP residual de WANIP (p. ej. `192.168.250.22`) deja `PENDING`; SPV 202 → `FAILED`.

Flag: `genieacs.vparams.enabled` default **false** (`GENIEACS_VPARAMS_ENABLED`). El perfil Maven `acs-staging-war` lo hornea `true`. Prod (`acs-war`) sigue el provisioner de paths. El import CSV y la tabla **no se dropean**; staging no los lee con el flag on.

Push NBI (obligatorio tras deploy ACS staging). El NBI vivo acepta **PUT** en `/virtual_parameters/{name}` (snake_case); `/virtualParameters/{name}` responde 404.

```bash
GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-virtual-parameters-via-nbi.sh
```

El sandbox de un virtual parameter **no** define `_deviceId` (eso es de provisions). El JS resuelve el product class con `DeviceID.ProductClass` vía `declare` si `_deviceId` no existe.

`wifi-refresh` con flag on = GPV `GfWifiStatus` (no reescribe SSIDs). `access-layout` con flag on: `hasPppPath` por product class (V2804AX15T / F6600R), `wanIpPath`/`wanPppPath` = null, `connectionRequestUrl` sigue saliendo del device.

## Smoke lab 2026-09-11 (vparams)

ACS staging WAR 16:38: `VparamProvisioner` + `genieacs.vparams.enabled=true` horneado. `GENIEACS_VPARAMS_ENABLED` **unset** en Tomcat (el env no pisa el bake).

Lab: sub **2398**, SN **VSOL0031C0B6** / `12345B4641531C0B6`, tag `lab`. Re-provisión ACS `POST /cpe/provision` **sin SSIDs**, `wanVlanId=100`. No se tocó VLAN 1000 / WCD.1.

| Check | Resultado |
|--------|-----------|
| `access-layout` | `hasPppPath=true`, `wanIpPath`/`wanPppPath` = null (vparams, no paths) |
| Primer provision | `PENDING` — JS `script.ReferenceError: _deviceId is not defined` |
| Tras fix + re-PUT | `COMPLETE` en ~8 s |
| `GfInternetStatus` | `connected=true`, `ip=10.64.0.2`, `productClass=V2804AX15T` |
| Sesión MK | `gf2398` `10.64.0.2` `SESSION_UP` |
| CR / gestión | `http://10.20.0.2:7547/tr069` |
| WiFi | Intactos: `puppy` / `puppy - 5G` (no se mandaron SSIDs) |

## Smoke lab F6600R 2026-09-11 (vparams) — FAIL apply PPPoE

ACS staging ya desplegado (`FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --env staging --only acs`, exit 0, 16:38–16:39). WAR con `VparamProvisioner` y `genieacs.vparams.enabled=true`. PUT NBI de los 6 `Gf*` → HTTP 200. No deploy extra.

Lab: SN **ZTEGDC47BFFD**, deviceId `5872C9-F6600R-ZTEGDC47BFFD`, tag **`lab`** (verificado antes de cualquier write). No se tocó WANIPConnection.1 de gestión. Username de prueba **`gflabzte`** (secret MK2 perfil `GF-STG-200-200`, pool `PPPOE-STG`); no hay suscripción PPPoE ligada a este SN.

| Check | Resultado |
|--------|-----------|
| `access-layout` | `hasPppPath=true`, `wanIpPath`/`wanPppPath` = null (vparams, no paths) |
| GPV `GfInternetStatus` / `GfWifiStatus` | HTTP **200**. JS mapea F6600R: `productClass=F6600R`, `connected=true`, `ip=192.168.250.22` (WANIP.2 estática), SSIDs `lab-zte-e2e-24` / `lab-zte-e2e-24 - 5G` |
| `POST /cpe/provision` PPPoE sin WiFi | HTTP 200 `COMPLETE` en ~2.4 s — **falso**: cache de status ya tenía IP; el SPV quedó 202 |
| SPV `GfApplyInternetPppoe` | HTTP **202** + fault; sin sesión `/ppp/active` |
| WAN real | Solo `WANIPConnection.1` (gestión `192.168.255.236`) y `WANIPConnection.2` (internet `192.168.250.22`, name `2_INTERNET_R_VID_100`). **No existe `WANPPPConnection.*`** en WCD.1. El JS escribe `...WANPPPConnection.2` |
| CR / gestión | Intacta: `http://192.168.255.236:58000/...` |
| WiFi | Intactos (no se mandaron SSIDs) |
| Cola | Tasks/faults de este device **purgados** |

Fix TDD 2026-09-11 (esta copia, rama `cursor/cloud-agent-1788755422204-t73q3`, sin commits, sin prod):

- JS: AddObject `WANPPPConnection` en el WCD de internet antes del SPV. Node `gf-vparams.test.js` 12/12.
- Kotlin: `VparamProvisioner` exige SPV HTTP 200 y pool `10.64.*`. `VparamProvisionerTest` + `GenieAcsVirtualParametersTest` verdes.
- PUT NBI de los 6 `Gf*` → HTTP 200 (script vivo tiene `pppParentPath` / `{path: Date.now()}`).
- ACS staging health 200 tras `--war-only`. Re-smoke lab **FAIL** (SPV 202, CPE sin Inform). No `tomcat9027`.

## Re-smoke F6600R 2026-09-11 21:38 — **FAIL**

ACS staging health **200** (no redeploy). Secret `gflabzte` / `GF-STG-200-200` leído de MK2 vía MySQL VPS `ispadmin_staging.network_device` id 8 (REST `GET /ppp/secret?name=`). Tag `lab` OK.

| Check | Resultado |
|--------|-----------|
| `POST /cpe/provision` PPPoE sin WiFi | HTTP 200 **`FAILED`** en 2.8 s — ya **no** hay COMPLETE falso. Causa: SPV `GfApplyInternetPppoe` HTTP **202** |
| AddObject JS / NBI | No creó instancia. `WANPPPConnection` solo metadatos (`_object`). AddObject NBI quedó queued |
| `GfInternetStatus` | `connected=true`, `ip=192.168.250.22` (WANIP.2 residual, no pool) |
| Sesión MK | **ninguna** `/ppp/active` `gflabzte` |
| WANIP.1 / CR | Intactos `192.168.255.236` |
| WiFi | Intactos `lab-zte-e2e-24` / `lab-zte-e2e-24 - 5G` |
| Inform | `_lastInform` **01:51Z** (~47 min stale). CR no cerró sesión ACS |
| Cola | Tasks/faults de este device **purgados**. Lab usable |

Causa: el CPE no informó; el SPV/AddObject se quedan en 202. El guard de `VparamProvisioner` (200 + `10.64.*`) evitó el COMPLETE falso.

## Re-authorize lab ZTE 2026-09-12 02:44Z — Inform/CR OK, smoke PPPoE incompleto

ONU **ZTEGDC47BFFD** estaba en autofind Core staging (`board=1`, `port=6`, `onu_type_name=F6600RV9.0.21`). No re-authorize a ciegas: `getBySn` no la tenía. `POST /onu/authorize` (fachada Core, VLAN **100**, `Generic_1`, Routing, `F6600RV9.0.21`) → `gigafiber-ma5608t_1_6_120`. No se usó lineprofile 12 (eso es VSOL `0031C0B6`). Tag GenieACS `lab` verificado antes.

| Check | Resultado |
|--------|-----------|
| Autofind | **Sí** — única en `GET /onu/unconfigured_onus` |
| Authorize | **OK** HTTP 200; luego `administrative_status=online` |
| Inform | `_lastInform` **02:44:47Z** (fresco; antes 01:51Z stale) |
| CR | **Alcanzable** — NBI `connection_request` GPV `GfInternetStatus` HTTP **200**. URL `http://192.168.255.236:58000/…` |
| WANIP.1 / WiFi (snapshot pre-smoke) | Intactos `192.168.255.236` / `lab-zte-e2e-24` |
| WANPPP | Aún vacío en ese snapshot |
| Smoke `GfApplyInternetPppoe` | **No cerrado** — túneles `:7557`/`:8091` cayeron; `ssh root@VPS` Permission denied. MK2 REST/API desde Mac RST (solo VPS alcanza 443). Sin deploy prod. |

Lab usable: ONU autorizada y online. Reabrir túneles ACS/GenieACS para el re-smoke PPPoE.

## Add-then-set F6600R 2026-09-12 (JS)

TDD Node: `gf-vparams.test.js` **22/22**. Primer ciclo sin instancia = solo AddObject + `{pending:"addObject"}`; segundo ciclo con `Username`/`Enable` = SPV credenciales y `WANIPConnection.2.Enable=false`. Nunca `WANIPConnection.1`. V2804AX15T sigue en WCD.2.

PUT NBI `apply-virtual-parameters-via-nbi.sh` → 6× HTTP 200 (`http://127.0.0.1:7557`).

### Smoke F6600R 2026-09-12 **PASS** `gflabzte` / `10.64.60.2`

El primer script murió: túneles cayeron (NBI timeout). Reabiertos `:7557`/`:8091`. ACS health UP. Tag `lab` OK.

`GfApplyInternetPppoe` gordo (Name/Trigger/NAT/CT-COM + AddObject en ciclo ready) → SPV HTTP **202** + fault **9002** sobre el objeto `WANPPPConnection.2.`. Username seguía vacío. WANIP.1 y WiFi intactos.

SPV flaco NBI (solo lab): `ConnectionType` + `Username` + `Password` → 200, user `gflabzte`. Luego VLAN/Enable + disable WANIP.2 → 200. `ConnectionStatus=Connected` `ip=10.64.60.2`. MK2 `/ppp/active` `gflabzte` `10.64.60.2`. `GfInternetStatus` connected. WANIP.1 `192.168.255.236`. WiFi `lab-zte-e2e-24` / `lab-zte-e2e-24 - 5G`. Cola vacía.

JS + ACS 2026-09-12 (producto): F6600R `slimPpp` en dos SET (creds → VLAN/Enable). `VparamProvisioner` reintenta SPV si `pending`. Node 25/25. `VparamProvisionerTest` 6/6. Re-PUT NBI de `Gf*`.

### Smoke ACS `POST /cpe/provision` 2026-09-12 — **FAIL 202 / too_many_commits**

Reset lab OK. Dos `POST /api/acs/v1/cpe/provision` (WAR staging sin retry Kotlin): HTTP ACS **200** body `FAILED` **`HTTP 202`** (SET `VirtualParameters.GfApplyInternetPppoe` encolado). Sin 9002. Fault GenieACS `too_many_commits` en el 2.º task. El JS igual aplicó en cola: `WANPPP.2` user `gflabzte` Enable true; GPV live `Connected` `10.64.60.2`; MK2 `/ppp/active` `gflabzte` `10.64.60.2`; WANIP.1 `192.168.255.236`; WiFi `lab-zte-e2e-24` / `lab-zte-e2e-24 - 5G`. **No es PASS de producto** (éxito = provision ACS 200/COMPLETE, no SPV NBI a `WANPPP.*`).

Diagnóstico: lote JS F6600R declaraba TR-181 `Device.ManagementServer.ConnectionRequestURL` cuando el CR IGD aún no estaba en el ciclo, más GPV de `ExternalIPAddress` en WANPPP.2/WANIP.2. Slim extra: sin `Device.*` en F6600R, sin esos GPV, skip VLAN/WANIP.2/`Enable` si ya aplicados (un `spv(Enable,true)` en cada re-run no asienta). Node 26/26. PUT NBI 6×200.

Reintento ACS tras slim (reset lab, idle 6 s, sin GPV warm): POST1 y POST2 otra vez HTTP ACS **200** `FAILED` **`HTTP 202`**, fault `too_many_commits`, sin 9002. GPV live `gflabzte` / `10.64.60.2` Connected; MK2 `gflabzte` `10.64.60.2`; WANIP.1 y WiFi intactos. **FAIL producto.**

NBI `SET VirtualParameters.GfApplyInternetPppoe` con `timeout=60000` (CPE ya settled, 0 writes) también **202 en ~1.2 s** + `too_many_commits`. No es solo el WAR sin timeout. Presets GenieACS `default` / `inform` / `bootstrap` tienen precondition vacía (corren en el parque). No se tocó el parque ni se hizo deploy.

## Cadena

```text
Backoffice
    │ HTTP JWT ADMIN
    ▼
Core  /admin/tr069-profiles
    │ HTTP + X-Olt-Gateway-Key
    ▼
Gateway  /api/olt-gateway/acs/profiles
    │ HTTP + X-Acs-Key
    ▼
ACS WAR  /api/acs/v1/profiles
    │ JDBC propio
    ▼
MySQL stg_acs / prod_acs . tr069_model_profile
```

Contrato backoffice (multipart CSV en Core): `GET/DELETE` JSON; `POST .../preview` y `POST .../import` con `file` + `aliases`. Core traduce a JSON `{ csv, aliases, importedBy }` hacia Gateway/ACS.

Core `/admin/tr069-profiles` exige `olt.gateway.client-enabled=true` (mismo overlay que el resto de fachadas ONU).

## Store ACS

- Flyway: `src/main/resources/db/acs/V1__tr069_model_profile.sql`
- `application-acs.properties`: `spring.flyway.enabled=true`, `locations=classpath:db/acs`, `baseline-on-migrate=true`, `baseline-version=0`
- Bake WAR `acs-war` / `acs-staging-war`: `spring.flyway.locations=classpath:db/acs` en `application-prod.properties` (no aplicar `db/migration` de Core en el schema ACS)

## Datos (VPS, one-shot)

Orden:

1. Desplegar ACS WAR (Flyway V1 crea la tabla).
2. `scripts/sql/copy-tr069-profiles-to-acs.sql` copia `ispadmin.tr069_model_profile` → `prod_acs` y `stg_acs`.
3. Desplegar Core (Flyway `V50__drop_tr069_model_profile.sql`).
4. Seed e2e: `staging-e2e-registration-catalog.sql` copia `prod_acs` → `stg_acs` y recarga `ispadmin-staging-acs.war`.

No re-ejecutar el copy después de V50: la tabla Core ya no existe.

## Verificación corta

- Unit: preview/import CSV F6600R (`WANIPConnection.2` / WLAN).
- Arquitectura: ACS no lee schema `ispadmin`; Core no tiene `@Table(name = "tr069_model_profile")`.
- Smoke: `GET /admin/tr069-profiles` con JWT ADMIN; import no escribe en `ispadmin_staging.tr069_model_profile`.
- Postman NBI lab (SET `GfApplyInternetPppoe`, un JSON): [postman-genieacs-nbi-pppoe-lab.md](./postman-genieacs-nbi-pppoe-lab.md).
