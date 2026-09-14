# Service-health — membresía lab solo BD (2026-09-07)

## Decisión

Se mantiene el mecanismo **lab** (staging/OLT/GenieACS compartidos con clientes). La membresía es **100% BD**; no hay lista en env.

## Fuente canónica

```text
GenieACS device tag "lab"
  → SubscriptionAcsSyncService
  → subscription_acs.lab = true
  → AcsSubscriptionPort.labSubscriptionIds() / isLab(id)
  → ServiceHealthScope
```

| Entorno | Recolección |
|---------|-------------|
| Staging / dev (`gigafiber.environment.tag` no vacío) | Solo `subscription_acs.lab=1` |
| Prod (tag vacío) | Piloto o todas, **excepto** labs BD |

## Eliminado

- `SERVICE_HEALTH_LAB_SUBSCRIPTION_IDS`
- `service.health.lab-subscription-ids`
- `ServiceHealthProperties.labSubscriptionIds`

Añadir un lab: tag GenieACS `lab` + sync ACS (siguiente poll). **Sin** editar `.env` ni recreate por membresía.

## Staging as-built (2026-09-07)

GenieACS tag `lab` (VPS, 2 devices) → BD:

| Env | subscription_id | SN | genieacs_device_id | lab |
|-----|-----------------|----|--------------------|-----|
| staging | 2387 | ZTEGDC47BFFD | `5872C9-F6600R-ZTEGDC47BFFD` | 1 |
| staging | 2389 | VSOL0031C0B6 | `B46415-V2804AX15T-12345B4641531C0B6` | 1 |
| prod | 2339 | ZTEGDC47BFFD | `5872C9-F6600R-ZTEGDC47BFFD` | 1 |
| prod | 2345 | (ACS huérfana / mismo device ZTE) | `5872C9-F6600R-ZTEGDC47BFFD` | 1 |

VSOL lab en GenieACS lleva tag `sub-2349`; en prod no hay suscripción `VSOL0031C0B6` / `#2349` — solo staging `#2389`.

Tras deploy del Core con este cambio: quitar `SERVICE_HEALTH_LAB_SUBSCRIPTION_IDS` de `/opt/gigafiber/.env` y recreate `tomcat-staging` si el contenedor aún inyecta la var.

## Código

- `ServiceHealthScope` → `AcsSubscriptionPort`
- `HealthEvidenceReader` → `identity.lab` vía `scope.lab(id)`
- Tests: `ServiceHealthScopeTest`, `ServiceHealthPropertiesTest`

## Verificación

```bash
./mvnw -Dtest=ServiceHealthScopeTest,ServiceHealthPropertiesTest,SubscriptionHealthAdaptersTest,RemoteActionWifiRefreshTest,OltHistoryServiceTest,DiagnosisEngineTest test
```
