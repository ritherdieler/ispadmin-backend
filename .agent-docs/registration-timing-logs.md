# Logs de timing del alta FIBER (`REG_TIMING`)

Fecha: 2026-09-13.

Mide qué tramo del cableado de registro tarda más. **No corre en producción.**

## Dónde se ve

Logger `REG_TIMING` (consola + `wispadmin.log`). Grep:

```text
REG_TIMING
```

Ejemplo:

```text
REG_TIMING war=core kind=http.in method=POST path=/subscription status=200 ms=1842 trace=a1b2c3
REG_TIMING war=core kind=span name=core.gateway.activate ms=1710 sn=ZTEGDC47BFFD
REG_TIMING war=gateway kind=http.in method=POST path=/api/olt-gateway/onu/activate ms=1688
REG_TIMING war=gateway kind=span name=gateway.olt.authorize ms=420 sn=ZTEGDC47BFFD
REG_TIMING war=gateway kind=http.out method=POST path=/api/acs/v1/cpe/provision ms=1250
REG_TIMING war=acs kind=span name=acs.genieacs.wait-complete ms=980 sn=ZTEGDC47BFFD
```

El campo `ms` más grande es el cuello de botella. `kind=http.in` es el endpoint de ese WAR; `kind=http.out` es la llamada HTTP al siguiente; `kind=span` es trabajo interno (SSH OLT, PPPoE MK, poll GenieACS).

## Ambientes

| Ambiente | Activo |
|----------|--------|
| local / `dev` | sí |
| staging (Core, Gateway, ACS) | sí |
| prod | **no** |

No hace falta flag extra: staging ACS/Gateway se detectan por context-path `*staging*` o JDBC `stg_*` / `localhost`. Prod (`/ispadmin`, `prod_acs`, schema `ispadmin`) queda apagado.

Propiedad: `gigafiber.registration.timing.enabled` (`application-dev`/`staging` = true, `prod` = false). No es secreto.

## Spans

| name | WAR |
|------|-----|
| `core.gateway.activate` | Core → Gateway `POST /onu/activate` |
| `core.mk.pppoe` | Core → MK2 secret PPPoE |
| `gateway.olt.authorize` | Gateway SSH authorize |
| `gateway.acs.provision` | Gateway → ACS `POST /cpe/provision` |
| `acs.provision` | ACS facade |
| `acs.genieacs.find-device` | NBI list devices |
| `acs.genieacs.enqueue-pppoe` | enqueue `gf-pppoe-wan2-poc` |
| `acs.genieacs.wait-complete` | poll GPV IP/SSID |

Header de correlación: `X-Gf-Reg-Trace` (se propaga Core → Gateway → ACS).

## Pruebas

- `RegistrationTimingTest`
- `StagingEnvironmentPropertiesTest.staging_disables_collectors_whatsapp_and_udp_binds_by_default`
