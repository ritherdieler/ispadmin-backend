# OLT Gateway compartido — fase 0 y stopgap VTY

**Fecha:** 2026-09-21. Diseño: agent store `docs/olt-gateway-compartido.md`. Sin deploy y sin cutover DNS.

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

El Core manda `X-Gigafiber-Env` (`prod` por defecto, `stg` en staging, `lpstg` en prestaging).

## Antes de un deploy

1. En `/opt/gigafiber/.env` del dueño: `OLT_GATEWAY_STAGING_API_KEY` distinta de `OLT_GATEWAY_API_KEY`, y `OLT_GATEWAY_ACS_BASE_URL_STAGING` (desde `tomcat9027`: `http://tomcat-staging:8080/ispadmin-staging`).
2. Desplegar **prod primero** (acepta la key y el fanout). Después staging (apaga su SSH).
3. No publicar `/ispadmin-oltgateway`. No segundo SSH a la OLT. No prestaging al VPS.

La prueba de un solo TCP a `10.11.104.2:22` queda para después de ese deploy. Este cambio no se desplegó.

## Fuera de este corte

Fases 2–6 del diseño: WAR sibling, cutover del contenedor, Traefik, túnel documentado como único camino de la Mac. Cutover DNS `.cloud` no entra aquí.
