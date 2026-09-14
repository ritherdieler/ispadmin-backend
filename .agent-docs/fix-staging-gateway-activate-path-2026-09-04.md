# Fix staging: alta FIBER vía Gateway (writes + client-enabled)

Fecha: 2026-09-04.

## Evidencia en staging

1. **SmartOLT fallback en el alta 2363 (14:04).** Access log: cero `POST …/onu/activate` en esa ventana. `olt=COMPLETE` en ~10 s por `CancelledOnuReuseService` → SmartOLT cloud. Log esperado a partir de este fix: `FIBER OLT Gateway client unavailable; SmartOLT fallback` o `FIBER OLT via Gateway activate`.
2. **Único activate del día (13:01) → 403 CSRF** (antes de `OltGatewaySecurityConfig`).
3. **Probe live con API key (15:27):** `oltStatus=FAILED`, mensaje `OLT gateway writes are disabled (olt.gateway.writes.enabled=false)` pese a `writes.enabled=true` appendeado al final de `application-oltgateway.properties`. Causa: Boot Config Data conserva la **primera** clave del documento / el valor de `application-prod.properties` (`${OLT_GATEWAY_WRITES_ENABLED:false}`).
4. **ACS URL:** misma regla first-wins; el append a `/ispadmin-staging-acs` no pisaba `${…/ispadmin-acs}`. Access: `GET /ispadmin-acs/…` 404.
5. **ACS_API_KEY** sí está en el env del contenedor; falta no era la key sino writes + path Core→Gateway.

## Cambios

| Área | Cambio |
|------|--------|
| `oltgateway-staging-war` | `replaceregexp` de `writes.enabled=true` en **prod** y oltgateway; ACS URL se **reemplaza** (no append duplicado) |
| `subsystems.sh --props-dir` | Con `--with oltgateway`, fuerza `olt.gateway.client-enabled=true` en `application.properties` / `prod` / `staging` |
| `FiberInstallationStrategy` | INFO path Gateway; WARN si cae a SmartOLT |
| WAR packaging | Excluye `application-local.properties` (secretos locales no deben ir al VPS) |

## Redeploy

```bash
./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs
```

Verificar en Tomcat:

- Core: `grep client-enabled …/ispadmin-staging/…/application-staging.properties` → `true`
- Gateway: `grep writes.enabled …/application-prod.properties` → `true`
- Gateway: una sola línea ACS URL → `…/ispadmin-staging-acs`
- Alta: access log con `POST …/onu/activate` 200 y `oltStatus=COMPLETE` (no `writes are disabled`)
- App log: `FIBER OLT via Gateway activate` (no SmartOLT fallback)

SmartOLT sigue como fallback solo si el bean cliente no existe.
