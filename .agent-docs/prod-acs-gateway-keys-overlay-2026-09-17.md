# Prod: bindings ACS/Gateway en overlay `prod` — 2026-09-17

## Síntoma

Alta FIBER prod `#2363` (ONU VSOL `12345B46415F5BB66` / OLT `HWTC15F5BB66`): OLT y MikroTik `COMPLETE`, TR-069 `PENDING`, sin `cpe_record`. El Gateway llamaba ACS sin `X-Acs-Key` válido (401).

## Causa

`tomcat9027` usa solo perfil `prod`. No carga `application-oltgateway.properties` ni `application-acs.properties`. El overlay prod tenía URL ACS pero **no** `olt.gateway.acs.api-key`.

## Overlay regularizado

En `application-prod.properties` (defaults vacíos; valores en `/opt/gigafiber/.env`):

| Propiedad | Env |
|-----------|-----|
| `olt.gateway.acs.api-key` | `ACS_API_KEY` |
| `acs.genieacs-to-acs-api-key` | `GENIEACS_TO_ACS_API_KEY` |
| `acs.gateway.internal-base-url` | `ACS_GATEWAY_BASE_URL` (default `http://127.0.0.1:8080/ispadmin`) |
| `acs.gateway.api-key` | `ACS_TO_GATEWAY_API_KEY` |
| `olt.gateway.acs-to-gateway-api-key` | `ACS_TO_GATEWAY_API_KEY` |
| `olt.gateway.operation-secret` | `OLT_GATEWAY_OPERATION_SECRET` (fallback `olt.gateway.api-key`) |

Preflight de deploy exige esas bindings. Nombres en `scripts/deploy.config.example`.

## Verificación

`./gradlew :core:test --tests "com.dscorp.wispadmin.wispadmin.config.ProdEnvironmentPropertiesTest" --tests "com.dscorp.wispadmin.wispadmin.config.DeployDisabledModulesPreflightScriptTest"`

## Pendiente operativo

El WAR prod en VPS **aún no** tiene este overlay. Tras `deploy.sh --env prod`, reintentar TR-069 del `#2363`.
