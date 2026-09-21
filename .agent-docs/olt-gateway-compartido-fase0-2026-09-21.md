# OLT Gateway compartido — fase 0 y stopgap VTY

**Fecha:** 2026-09-21. Diseño: agent store `docs/olt-gateway-compartido.md`. Desplegado en prod y staging (`1.0.3+743f306`). Sin cutover DNS.

Un solo `OltCliBus` habla con la MA5608T `10.11.104.2`. Staging y prestaging dejan de abrir SSH. Siguen siendo clientes HTTP del Gateway que ya corre dentro del Core prod.

## Contrato (fase 0)

| Regla | Dónde |
|---|---|
| `OLT_GATEWAY_API_KEY` escribe flota. `OLT_GATEWAY_STAGING_API_KEY` escribe solo SN lab (`0031C0B6`, `12345B4641531C0B6`, `ZTEGDC47BFFD`). Si no, 403 | `OltGatewayApiKeyFilter` |
| `X-Gigafiber-Env` que no coincide con la key → 400 | mismo filtro |
| Day-2 ACS: `stg` / `lpstg` → `olt.gateway.acs.base-url-staging`. `prod` → base prod. Sin env y `require-caller=true` → 400 | `AcsCallerRouter` |
| `onu.optical-batch` con `gigafiber.redis.optical-fanout-namespaces=prod,stg` hace XADD a los dos streams | `redisStreamKeys` |
| `cpe.provisioning` y `cpe.inform` salen al namespace del llamante (`prod` o `stg`) | `EventRouteContext` |
| `olt.gateway.enabled=false` no crea `OltCliBus` | `OltGatewayConfig` |

## Stopgap (fase 1, sin extraer proceso)

| Proceso | `olt.gateway.enabled` | URL cliente |
|---|---|---|
| Core prod | `${OLT_GATEWAY_ENABLED:false}` (el VPS lo deja en true: es el dueño) | loopback `/ispadmin` |
| Core staging | `false`. Signal y SNMP off | `http://tomcat9027:8080/ispadmin` |
| Prestaging Mac | `false` | `${OLT_GATEWAY_INTERNAL_BASE_URL:http://127.0.0.1:8092/ispadmin}` |

`gigafiber.subsystems.oltgateway.enabled` sigue `true` en staging para no romper el preflight de la cadena FIBER ni el datasource satélite. El SSH no arranca: el bean está detrás de `olt.gateway.enabled`.

El `.env` compartido lleva `OLT_GATEWAY_ENABLED=true` (el dueño es prod). Esa variable de entorno pisa `olt.gateway.enabled=false` del WAR de staging. `scripts/ensure-tomcat-staging-compose.py` fija `OLT_GATEWAY_ENABLED: "false"` solo en el servicio `tomcat-staging`.

El Core manda `X-Gigafiber-Env` (`prod` por defecto, `stg` en staging, `lpstg` en prestaging).

## Antes de un deploy

1. En `/opt/gigafiber/.env` del dueño: `OLT_GATEWAY_STAGING_API_KEY` distinta de `OLT_GATEWAY_API_KEY`, y `OLT_GATEWAY_ACS_BASE_URL_STAGING` (desde `tomcat9027`: `http://tomcat-staging:8080/ispadmin-staging`).
2. Desplegar **prod primero** (acepta la key y el fanout). Después staging (apaga su SSH).
3. No publicar `/ispadmin-oltgateway`. No segundo SSH a la OLT. No prestaging al VPS.

## Deploy 2026-09-21

Orden: prod (`tomcat9027` recreado, HTTP 200, `oltReachable=true`) y después staging. En `/opt/gigafiber/.env` quedaron `OLT_GATEWAY_STAGING_API_KEY` (distinta de la de prod) y `OLT_GATEWAY_ACS_BASE_URL_STAGING=http://tomcat-staging:8080/ispadmin-staging`.

El primer arranque de staging heredó `OLT_GATEWAY_ENABLED=true` y abrió su propio `OltCliBus` (dos sesiones). Se paró el contenedor, se fijó el pin en el compose y se restauró `ispadmin-staging.war`. Después: `OLT_GATEWAY_ENABLED=false` dentro de `tomcat-staging`, HTTP 200, sin `SSH session established` en el log, y `GET /api/olt-gateway/onu/unconfigured_onus` en staging responde 404. La misma lectura contra prod con la key de staging y `X-Gigafiber-Env: stg` responde 200. Un delete de SN que no es de laboratorio responde 403 `lab_sn_required`.

## Fuera de este corte

Fases 2–6 del diseño: WAR sibling, cutover del contenedor, Traefik, túnel documentado como único camino de la Mac. Cutover DNS `.cloud` no entra aquí.
