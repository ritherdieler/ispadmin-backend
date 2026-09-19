# Deploy staging — Flyway V42/V52 (2026-09-18)

Staging `tomcat-staging` no arrancaba: Flyway V42 fallaba con `Field 'lab' doesn't have a default value` al hacer INSERT en `subscription_acs` (columna Hibernate `lab` NOT NULL sin default). La tabla `onu` no existe en `ispadmin_staging` (inventario en `olt_mgr_onu`); el procedimiento de FK a `onu` también se salta.

V52 (`ALTER TABLE subscription ADD COLUMN access_mode…`) se guardó para no romper el boot: esas columnas y un UNIQUE de Hibernate sobre `pppoe_username` ya existen.

`repair()` + migrate reintentó V42 (fila `success=0`). Flyway V38–V55 `success=1`. Cadena FIBER/TR-069 del preflight: completa. No se tocó prod.

Verificado 2026-09-18 13:38 UTC:

| Check | Resultado |
|-------|-----------|
| `GET https://api.gigafiberperu.cloud/ispadmin-staging/` | HTTP 200 |
| `POST /onu/{sn}/service-port/ensure-mgmt` sin JWT | HTTP 401 (ruta existe; ya no 404) |
| Flyway `ispadmin_staging` | 38–55 OK |

`olt.gateway.writes.enabled=false` en `application-staging.properties`: el endpoint está; un ensure-mgmt real contra la OLT puede quedar en no-op hasta habilitar writes.

```bash
./gradlew :core:test --tests SchemaGovernancePropertiesFileTest :core:war :core:tomcatLibs -Pdjl.linux
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth
```
