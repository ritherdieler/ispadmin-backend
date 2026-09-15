# Sync `subscription_acs.lab` en el alta — 2026-09-15

## Problema

Staging 360 solo recolecta filas `subscription_acs.lab=1`. El alta FIBER de la ONU lab `VSOL0031C0B6` (`#6`) dejó `tr069_device_id` en `subscription` y TR-069 `COMPLETE`, pero **no creó** `subscription_acs`. GenieACS ya tenía tag `lab` (y tags viejos `sub-2349` / `sub-3`, no `sub-6`). El 360 vio `identity.lab=false` y descartó óptica.

Causas:

1. `upsertFromProvision` salía si el outcome era `NA`, aunque hubiera `deviceId` (CPE ya conocido en GenieACS / ACS aún no provisionado).
2. `refreshTr069FromGateway` no reintentaba el sync si status y deviceId ya estaban listos.

## Corrección

| Sitio | Cambio |
|-------|--------|
| `SubscriptionAcsSyncService.upsertFromProvision` | Persiste si hay `deviceId`. `NA` sin deviceId sigue sin fila. Copia `lab` desde `_tags`. |
| `SubscriptionProvisionService.refreshTr069FromGateway` | Si ya está `COMPLETE` + deviceId, igual llama `persistAcsLink` (upsert + tagger `sub-{id}`). No pega al Gateway. |

El tag `lab` **sigue siendo** del NBI (banco). El alta no lo inventa; solo lo proyecta a BD.

## Cableado 360 por ambiente

| Ambiente | `environment.tag` | Quién entra al 360 | Overlay |
|----------|-------------------|--------------------|---------|
| Prod | **vacío** (prohibido setear tag) | Todas las no-lab. Labs BD **fuera**. Flags `SERVICE_HEALTH_*` / Redis en `/opt/gigafiber/.env`. |
| Staging | `stg` | Solo `subscription_acs.lab=1` | `application-staging.properties` hornea health/óptica/ACS on |
| Prestaging local | `lpstg` | Solo `subscription_acs.lab=1` | `application-local-prestaging.properties` |

Un `gigafiber.environment.tag` en prod volvería el 360 lab-only y apagaría a los abonados. Contrato: `ServiceHealthEnvironmentWiringTest`.

## Verificación

```bash
./gradlew :core:test --tests "com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsSyncServiceTest" --tests "com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionServiceTest" --tests "com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069PostInstallProvisionerAcsSyncTest" --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthPropertiesTest" --tests "com.dscorp.wispadmin.servicehealth.config.ServiceHealthScopeTest" --tests "com.dscorp.wispadmin.wispadmin.config.ServiceHealthEnvironmentWiringTest" --tests "com.dscorp.wispadmin.servicehealth.OpticalBatchPersistServiceTest"
```

Tras deploy: un alta lab (ONU con tag `lab` en NBI) debe dejar `subscription_acs.lab=1` y `sub-{id}`. `registration-progress` backfilla altas que ya tengan deviceId.
