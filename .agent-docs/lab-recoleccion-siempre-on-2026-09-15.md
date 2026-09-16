# Recolección lab siempre on — 2026-09-15

## Qué es “recolección”

En este stack no hay zonas de cobranza SmartMap. Recolección = telemetría 360 + poll de tráfico:

| Capa | Flag / fuente | Quién entra en lab |
|------|---------------|--------------------|
| Service-health 360 (ACS Inform, óptica, diagnosis) | `service.health.enabled` + `ServiceHealthProperties.collects()` | En tag no vacío (`stg`, `lpstg`, `dev`): **todas** las suscripciones (e2e). Prod (tag vacío) sigue excluyendo lab. |
| Membresía lab | GenieACS `_tags` contiene `lab` → `subscription_acs.lab` | `AcsSubscriptionPort.isLab()` |
| Tráfico MikroTik | `traffic.poll.enabled` (WAR Traffic) | Directorio Core; no filtra por lab |
| Overlay staging | `application-staging.properties` | `service.health.enabled=true`, `gigafiber.environment.tag=stg`, `gigafiber.subsystems.servicehealth.enabled=true` |
| Master env compartido | `/opt/gigafiber/.env` `SERVICE_HEALTH_ENABLED` | Hoy `true` en `tomcat-staging` |

`GET /subscription/{id}/service-health` expone el gate como `identity.lab` y `pilot_enabled`.

## Zonas lab (staging 2026-09-15)

No hay `place` ni `olt_mgr_zone` llamados lab. Place de las tres altas: **9 de octubre**. OLT: **Zone 1**.

| Id | Cliente | SN / modo | GenieACS tag `lab` | Antes 360 | Después 360 | Tráfico 2 h |
|----|---------|-----------|--------------------|-----------|-------------|-------------|
| 5 | EEEFIBER PRUEBAZTE | `ZTEGDC47BFFD` STATIC_IP `10.64.0.11` | sí (`5872C9-F6600R-ZTEGDC47BFFD`) | sin fila `subscription_acs` → `lab=false` `pilot_enabled=false` | fila `lab=1` → `lab=true` `pilot_enabled=true` | 59 samples |
| 6 | EEEFIBER PRUEBAVSOL | `VSOL0031C0B6` PPPOE_DYNAMIC | sí | `lab=1` `pilot_enabled=true` | igual | 59 samples |
| 7 | LAB STATICIP | WIRELESS `192.168.250.16` | no hay CPE | N/A ACS | N/A ACS | 59 samples |

GenieACS NBI: 2 devices con tag `lab` (ZTE + VSOL). Tags `sub-5` y `sub-6` puestos en NBI.

Prestaging local (`lpstg`) usa el mismo gate tagged = todas las suscripciones. Política e2e: [staging-e2e-recoleccion-siempre-on-2026-09-15.md](./staging-e2e-recoleccion-siempre-on-2026-09-15.md).

## Guard de código

`SERVICE_HEALTH_ENABLED=false` (env compartido prod/staging) ya no apaga recolección en ambientes con tag:

- `collects()` / `collectionSubscriptionIds()`: tag no vacío → **todos** los ids; `enabled=false` solo apaga prod (tag vacío).
- `HealthEvaluationService.evaluate()` ya no sale por `!properties.enabled` si hay ids en scope.

Prod sigue excluyendo labs BD. Overlays `application-staging.properties` y `application-local-prestaging.properties` hornean `service.health.enabled=true`.

## Agujero que dejó #5 off

HEAD de `refreshTr069FromGateway` retorna si TR-069 ya es `COMPLETE` + `tr069_device_id` y **no** upserta `subscription_acs`. `GET …/registration-progress` en el WAR staging no creó la fila. Se insertó a mano en `ispadmin_staging.subscription_acs` (`lab=1`, device ZTE). No se mezcló el working tree local de `SubscriptionProvisionService`.

## Verificación

```bash
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :core:test \
  --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthPropertiesTest" \
  --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthScopeTest" \
  --tests "com.dscorp.wispadmin.servicehealth.DiagnosisEngineTest"
```

```bash
# JWT dscorp. Esperado #5 y #6: identity.lab=true, pilot_enabled=true
curl -sS -H "Authorization: Bearer $TOKEN" \
  https://api.gigafiberperu.cloud/ispadmin-staging/subscription/5/service-health
```
