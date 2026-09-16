# Staging e2e: recolección siempre on — 2026-09-15

## Invariante

En ambientes con `gigafiber.environment.tag` no vacío (`stg`, `lpstg`, `dev`) **toda** suscripción entra a recolección 360. El e2e de staging no puede quedar con “Recolección desactivada” por falta de `subscription_acs.lab`.

Prod (tag vacío) no cambia: excluye lab y respeta `pilotSubscriptionIds`.

## Qué se enciende

| Capa | Gate | Staging e2e |
|------|------|-------------|
| Diagnosis / banner 360 (`pilot_enabled`) | `ServiceHealthProperties.collects()` | `true` para cualquier id |
| Wi‑Fi counts + estaciones (Inform ACS) | `CpeInformPersistService` → `scope.collects()` | Persiste si hay SN mapeado |
| Óptica | `scope.collects()` + `optical-enabled` | Overlay `true` |
| Tráfico MikroTik | `traffic.poll.enabled` + directorio XOR | Overlay `true`; no filtra lab |
| Overlay | `application-staging.properties` | health, ACS, traffic, poll on |

Wi‑Fi y estaciones siguen necesitando Inform ACS (ONU con TR-069). STATIC_IP WIRELESS sin CPE no tiene estaciones; el tráfico va por IP.

## Por qué fallaba el e2e

El gate tagged era `lab=true`. El alta Android / STATIC_IP a menudo no deja `subscription_acs.lab=1` (sin fila ACS, o sync que no proyecta `_tags`). Entonces `pilot_enabled=false` y el Inform se descarta.

## Cómo asegurarlo en cada e2e

1. **Código** (este cambio): tagged env recolecta todos los ids. Tests: `ServiceHealthPropertiesTest`, `ServiceHealthScopeTest`, `DiagnosisEngineTest`.
2. **Overlay**: `service.health.enabled/acs-enabled/optical-enabled=true`, `gigafiber.subsystems.traffic/servicehealth.enabled=true`, `traffic.poll.enabled=true`. Contrato: `ServiceHealthEnvironmentWiringTest`, `StagingEnvironmentPropertiesTest`.
3. **Tras el alta**, `GET /subscription/{id}/service-health` debe traer `pilot_enabled=true`. El GET pisa snapshots viejos con `scope.collects()`. El script Espresso falla si no. Si el banner sigue off, el WAR no tiene este decorate.
4. **No** apagar `SERVICE_HEALTH_ENABLED` / ACS / traffic en el overlay de staging para “probar”. Prod se apaga con tag vacío + flags, no tocando staging.

## Verificación

```bash
./gradlew :core:test \
  --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthPropertiesTest" \
  --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthScopeTest" \
  --tests "com.dscorp.wispadmin.servicehealth.DiagnosisEngineTest" \
  --tests "com.dscorp.wispadmin.wispadmin.config.ServiceHealthEnvironmentWiringTest"
```
