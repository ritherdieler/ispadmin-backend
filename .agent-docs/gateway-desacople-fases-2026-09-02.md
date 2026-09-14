# Gateway 100 % desacoplado (fases 1–4) — 2026-09-02

Invariante: el paquete `oltgateway` solo conoce OLT/ONU. El **core**, Health y NetDiag hablan con él por HTTP JSON (no por JDBC ni beans del WAR gateway). Política general: [subsistemas-desacople-transporte.md](./subsistemas-desacople-transporte.md).

## Fase 1 — Aislamiento

- Borrados el puente `oltgateway.service.OnuService` (`@Primary`) y `oltgateway.controller.OnuController`.
- Inventario público `/onu/configured*` vive en `wispadmin.oltclient.OnuFacadeController` (HTTP al WAR gateway).
- Writes SmartOLT `/onu` (authorize/reboot/delete/unconfigured) viven en `wispadmin.controller.OnuSmartOltController` + `LegacyOnuOperations`.
- `OltGatewayController` usa `OltGatewayOpenApi.SECURITY_SCHEME` (no `OpenApiConfig` del core).
- Adapters DIP `oltgateway/adapter/*` borrados. Health/NetDiag implementan los puertos con clientes HTTP en su propio paquete.
- `SubsystemDependencyRulesTest`: `oltgateway` no importa `wispadmin` ni hermanos.

## Fase 2 — Writes canónicos

Fuera de este ciclo (alta FIBER sigue en SmartOLT). Próxima fase: `POST /api/olt-gateway/onus/reboot|move` y fachada core.

## Fase 3 — Schema staging

- Datasource del WAR gateway: `stg_oltgateway` (`createDatabaseIfNotExist=true`).
- Script one-shot: `scripts/sql/migrate-olt-mgr-to-stg_oltgateway.sql` (**no ejecutar en prod**; hace falta confirmación explícita).
- `olt_mgr_onu_optical_sample` permanece en el schema del core; Health hace pull HTTP de `/onus/configured`.

## Fase 4 — WAR propio (staging primero)

| Artefacto | Perfil Maven | Context | Schema | `deploy.sh --env` |
|-----------|--------------|---------|--------|-------------------|
| `ispadmin.war` | `prod-war` | `/ispadmin` | `ispadmin` | prod (sin WAR gateway en este ciclo) |
| `ispadmin-oltgateway.war` | `oltgateway-war` | `/ispadmin-oltgateway` | `prod_oltgateway` | Maven listo; **no** se sube en prod |
| `ispadmin-staging.war` | `staging-war` | `/ispadmin-staging` | `ispadmin_staging` | staging |
| `ispadmin-staging-oltgateway.war` | `oltgateway-staging-war` | `/ispadmin-staging-oltgateway` | `stg_oltgateway` | staging siempre |

- `OltGatewayApplication` escanea solo `com.dscorp.wispadmin.oltgateway`.
- `WispAdminApplication` excluye `OltGatewayApplication` del scan; en **local/dev** el paquete `oltgateway` sigue en el monolito (como traffic). En staging las clases **nunca** van en el WAR core.
- Core HTTP: `olt.gateway.internal-base-url` horneada. **No** en `/opt/gigafiber/.env`.
- `scripts/subsystems.sh` siempre excluye clases `oltgateway` del WAR core. `--with oltgateway` solo enciende `olt.gateway.client-enabled=true`.

Detalle de build: [oltgateway-war-staging-2026-09-03.md](./oltgateway-war-staging-2026-09-03.md).
