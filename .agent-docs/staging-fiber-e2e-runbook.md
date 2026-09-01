# Runbook maestro — e2e FIBER / TR-069 en staging

Fuente de verdad para **repetir** el alta FIBER Android contra `ispadmin-staging`. Leer esto antes de tocar seed, red o el script. Detalle por tema en los docs enlazados; no inventar IPs, SN ni comandos.

Fecha de consolidación: 2026-09-01. Último e2e verde (ping + Firebase 204): suscripción **2349** (2026-09-01 16:05).

## Cómo correr (Mac)

Prerrequisitos en la máquina:

| Qué | Valor / dónde |
|-----|----------------|
| Workspace | `gigafiber/` (backend + `IpsAdmin-android app/`) |
| SSH VPS | `ispadmin-backend/scripts/deploy.config.local` (`VPS_HOST=212.85.13.47`, `DEPLOY_SSH_PASSWORD`) — **no commitear** |
| Firebase SA | `ispadmin-backend/src/main/resources/firebase_service_account_prod.json` (cleanup de foto de fachada) |
| `sshpass`, `adb`, Python 3 | PATH local |
| Emulador | AVD `medium_phone`, serial típico **`emulator-5554`** (`adb devices`) |
| ONU lab | física, cableada a OLT, **unconfigured** en SmartOLT |

```bash
cd "IpsAdmin-android app"
E2E_ONU_SN=ZTEGDC47BFFD E2E_WIFI_SSID=lab-zte-e2e-24 E2E_WIFI_PASS='LabZteWifi24!' \
  ./scripts/e2e_register_fiber_staging_espresso.sh
```

El wrapper: precleanup → login API → catálogo → espera ONU unconfigured → `emu geo fix` → `installStagingDebug` → `pm clear` → Espresso → ping MK2 → `tr069-e2e-hard-cleanup.sh --env staging`.

Éxito del script: `E2E_FIBER_STAGING_ESPRESSO_OK`. Si `tr069ProvisionStatus=COMPLETE`, entregar al usuario **SSID y clave** 2.4/5 **antes** del hard cleanup (regla `AGENTS.md` backend). El 5 GHz es `{ssid24} - 5G` con la misma clave.

Cleanup manual:

```bash
cd ispadmin-backend
./scripts/tr069-e2e-hard-cleanup.sh --env staging --sn ZTEGDC47BFFD --dni "$E2E_DNI"
```

## Ambientes

| | Staging | Prod |
|--|---------|------|
| API | `https://api.gigafiberperu.cloud/ispadmin-staging/` | `…/ispadmin/` |
| WAR | `ispadmin-staging.war` | `ispadmin.war` |
| MySQL | `ispadmin_staging` en `mysql8033` | `ispadmin` |
| Tomcat | mismo `tomcat9027` | mismo |
| Login e2e | `dscorp` / `nohacker` | no usar para este e2e |
| Flavor Android | `stagingDebug`, `applicationId` = prod (`com.dscorp.ispadmin`) | `prod` |

Detalle: `ambientes-local-staging-prod.md`.

## Seed (MySQL + perfiles TR-069)

```bash
cd ispadmin-backend
./scripts/sql/staging-e2e-seed-all.sh
```

Aplica `scripts/sql/staging-e2e-registration-catalog.sql` (idempotente) y hace `touch` del WAR staging para recargar `Tr069ModelProfileRegistry`. **No** crear `webapps/ispadmin-staging.xml` vacío (rompe el context). Sin reload, el SQL queda en MySQL pero el alta dice «No hay perfiles TR-069 importados».

`RELOAD_STAGING=0 ./scripts/sql/staging-e2e-seed-all.sh` — solo SQL.

| Dato | Origen | Uso |
|------|--------|-----|
| `place` (24, polígonos) | prod | `findByLocation`, selector Lugar |
| `mufa` / `nap_box` (162, `NO-001`) | prod | NAP |
| `plan` (FIBER activo, p. ej. `lab-basico`) | prod | paso plan |
| `network_device` id **8** (MK2) | prod | host + ping |
| `ip_pool` **`192.168.250.1/24`** → host 8 | seed (no solapa prod `.30`) | IP VLAN 100 + tag `stg` |
| `tr069_model_profile` | prod: `F6600R`, `HG8145X6`, `V2804AX15T` | ACS; alias `F6600RV9.0.21` → **F6600R** |
| Staff `dscorp` | seed de app | login |

Fixtures tabulares: `staging-e2e-fixtures.md`. SQL: `staging-e2e-registration-catalog-2026-09-01.md`.

Geo validado (cae en polígono `9 de octubre`): lat **`-11.2156`** lon **`-77.4107`**. No usar el centro del envelope.

## Fixture lab (ONU + UI)

| Campo | Valor |
|-------|--------|
| ONU | `ZTEGDC47BFFD` / hex `5A544547DC47BFFD` / SmartOLT `(ZTEG-DC47BFFD)` |
| Tipo | `F6600RV9.0.21` → perfil **F6600R** |
| Puerto GPON | 6 |
| Place | `9 de octubre` (id 1) |
| NAP | `NO-001` (id 104); el wizard puede elegir otra por `/napbox/near` |
| WiFi 2.4 | `lab-zte-e2e-24` / `LabZteWifi24!` |
| WiFi 5 | `lab-zte-e2e-24 - 5G` / `LabZteWifi24!` |
| Cliente UI | nombre `EeeFiber`, apellido `Prueba` (cleanup acepta `Prueba*` o DNI `9xxxxxxx`) |
| DNI | `9` + 7 dígitos (el script lo genera) |
| Foto fachada | broadcast debug `DEBUG_SET_FACADE_PHOTO` (no cámara) |

Android: `IpsAdmin-android app/.agent-docs/e2e-register-fiber-staging-lab-onu-2026-09-01.md`. Test: `FiberRegisterFirstOnuE2ETest`. **No** volver a pulsar tipo FIBER en el paso 2 (resetea NAP/ONU/WiFi).

## Backend / deploy

```bash
cd ispadmin-backend
./scripts/deploy.sh --env staging --with oltgateway,servicehealth,netdiag,traffic
```

`--with` mínimo: esos cuatro. Solo `oltgateway` o `oltgateway,servicehealth` rompe el arranque (faltan ports).

### SmartOLT (igual que prod)

`OnuService` **no** usa `OltManagerFacade`. Authorize / unconfigured / delete van a `OltService` → API SmartOLT. El env Tomcat `OLT_GATEWAY_ENABLED=true` puede crear el facade SSH, pero no intercepta `/onu/*`. Si se usa el facade con writes off → `OltWritesDisabledException` → HTTP 500 en `POST /subscription/with-facade-photo`.

`application-staging.properties`: `olt.gateway.enabled=false`, `olt.gateway.writes.enabled=false`. `scripts/subsystems.sh` hornea lo mismo.

Doc: `staging-smartolt-alignment-2026-09-01.md`. Deploy histórico: `staging-e2e-deploy-2026-09-01.md`.

### VLAN 100 + pool staging

Con `gigafiber.environment.tag=stg`, `SubscriptionVlanRules` acepta VLAN **100** sobre `192.168.250.x` además de prod `192.168.30.x`. Sin tag `stg`, `.250` + VLAN 100 → 500.

Doc: `staging-vlan100-pool-2026-09-01.md`.

## Red L3 (por qué el ping fallaba y cómo quedó)

El e2e pingeá **desde MK2** (`POST /rest/ping`), no desde el VPS.

| Capa | Estado 2026-09-01 | Nota |
|------|-------------------|------|
| MySQL pool | `192.168.250.1/24` host 8 | solo BD |
| MK2 `sfp-sfpplus2` | **`192.168.250.1/24`** + NAT masquerade + drop a `192.168.22.0/24` | aplicado; backup `staging-e2e-250-pre` |
| Misma iface | `192.168.30.1/24` prod + `192.168.255.1/24` ACS DHCP | no tocar |
| VPS `wg-olt` | **sin** `192.168.250.0/24` | `ip route get 192.168.250.20` sale por `eth0`; no afecta `/rest/ping` |
| ACS gestión | `192.168.255.0/24` | Inform TR-069; sí está en `wg-olt` |

Script MK2 idempotente: `scripts/genieacs/mk2-staging-pool-250.rsc`. Doc: `staging-mk2-pool-250-2026-09-01.md`. Catálogo RouterOS: `mikrotik-mk2-comandos-catalogo.md`.

TR-069 escribe WAN cliente `.250.x` / GW `.250.1`. Sin gateway en MK2, GPV puede ser `COMPLETE` y el ping 100% loss.

Para CR/GenieACS **desde el VPS** a `.250.x` faltaría añadir el CIDR a `wg-olt` (`wg-olt-customer-routes.sh`, `apply-genieacs-wg-customer-routes.sh`). No hace falta para el e2e actual.

## Cleanup (§4)

Orden: MikroTik cola → SmartOLT → Firebase Storage → MySQL → GenieACS tasks/faults.

- Schema: `--env staging` → `ispadmin_staging`.
- Firebase: `scripts/tr069_e2e_firebase_delete.py` (CA de `certifi` / `/etc/ssl/cert.pem`). Python.org 3.13 en Mac no tiene `…/etc/openssl/cert.pem`; urllib sin CA fallaba y **abortaba antes del DELETE MySQL**. El helper usa CA bundle; si Firebase falla, MySQL/ACS **siguen**. Verificado 2026-09-01: delete 204 de las fotos residuales `facades/1788295485102_…` y `facades/1788294259720_…`.
- `DELETE onu` solo si ninguna otra `subscription.fiber_onu_sn` apunta a ese SN.
- Safety: solo filas lab (`Eee*`/`Prueba*` o DNI `9xxxxxxx`) salvo `--force`.
- Colas MK2: prefijo `[stg] `, comment `env=stg`.

## Checklist si algo falla

| Síntoma | Qué mirar |
|---------|-----------|
| HTTP 500 al POST suscripción | VLAN/pool (`stg` + `.250`) o SmartOLT vs facade SSH |
| «No hay perfiles TR-069» | seed + **reload WAR**; `GET /admin/tr069-profiles` ≥3 y `F6600R` |
| Wizard sin lugar/NAP/plan | `staging-e2e-seed-all.sh`; `findByLocation` con el geo de arriba |
| ONU no en unconfigured | SmartOLT; cleanup previo; esperar ~1 min |
| TR-069 `MANUAL_REQUIRED` | perfil / alias tipo ONU |
| Ping 100% loss | MK2 tiene `192.168.250.1`? ONU aún autorizada? ping **antes** del cleanup |
| Script exit 1 post-PASS | Firebase o ping; MySQL no debe quedar con la sub |
| `ispadmin-staging.xml` vacío | no crearlo; rompe Tomcat |

Verificar API (token `dscorp`/`nohacker`):

```text
POST /users/login
GET /place/findByLocation?latitude=-11.2156&longitude=-77.4107
GET /plan   GET /napbox   GET /networkDevice/coreTypes
GET /admin/tr069-profiles
GET /onu/unconfigured_onus
```

## Infra fija (no commitear secretos)

| Recurso | Valor |
|---------|--------|
| VPS | `212.85.13.47` (`srv1043610`), stack `/opt/gigafiber/` |
| MySQL | contenedor `mysql8033` |
| Tomcat | `tomcat9027` |
| MK2 | `network_device` id 8, `38.224.231.4` |
| SmartOLT | `https://gigafiberperu.smartolt.com` (key en WAR prod `olt.service.api-key`) |
| ACS | GenieACS en VPS `:7557`; device e2e típico `5872C9-F6600R-ZTEGDC47BFFD` |
| Firebase bucket | `ispadmin-687ca.appspot.com` |
| Túnel OLT | `wg-olt` (pools `.30`, `.255`, no `.250`) |

Nombres de secretos: `vps-secrets-management.md`.

## Resultados (2026-09-01)

| Sub | IP | TR-069 | Ping MK2 | Nota |
|-----|-----|--------|----------|------|
| 2343 | `.250.16` | — | FAIL | SmartOLT URL con paréntesis |
| 2346 | `.250.19` | — | FAIL | sin gateway `.250` |
| 2347 | `.250.20` | COMPLETE | FAIL | perfiles OK; sin L3 `.250` |
| **2348** | **`.250.21`** | **COMPLETE** | **OK 4/4 ~4 ms** | gateway MK2 aplicado |
| **2349** | **`.250.19`** | SUCCESS UI (no `MANUAL_REQUIRED`) | **OK 4/4 ~4 ms** | `E2E_FIBER_STAGING_ESPRESSO_OK`; Firebase **204**; MySQL 0 residual |

## Relacionado

- `staging-e2e-fixtures.md` — tablas seed
- `staging-e2e-deploy-2026-09-01.md` — WAR y `--with`
- `staging-smartolt-alignment-2026-09-01.md`
- `staging-vlan100-pool-2026-09-01.md`
- `staging-mk2-pool-250-2026-09-01.md`
- `tr069-e2e-validacion-modelo.md`
- `IpsAdmin-android app/.agent-docs/e2e-register-fiber-staging-lab-onu-2026-09-01.md`
