# Service-health — rollout completo (sin piloto) — 2026-09-01

## Cambio

Antes, en prod solo recolectaban telemetría las suscripciones listadas en `SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS` (as-built: `2310,2328`). Lista vacía = cero recolección.

Ahora, con `SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS` vacío (o ausente), **todas** las suscripciones no-lab en prod entran en collectors ACS, óptica, evaluación y acciones. Staging sigue limitado a filas `subscription_acs.lab=1`.

La lista piloto sigue existiendo como restricción opcional si se rellena (rollback gradual).

## Build

`bash mvnw test -Dtest=ServiceHealthPropertiesTest,ServiceHealthScopeTest,BlastRadiusServiceTest,RemoteActionPersistenceTest` — OK.

## Prod

Tras deploy, dejar en `/opt/gigafiber/.env`:

```bash
SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS=
```

Reiniciar Tomcat (`docker compose restart tomcat` en `/opt/gigafiber`).

## Caso #2327

No era solo piloto: el CPE está en GenieACS (`5872C9-F6600R-ZTEGDC47DFB5`, Inform reciente) pero la suscripción tiene `tr069_device_id=NULL`, `tr069_provision_status=MANUAL_REQUIRED` y sin fila en `subscription_acs`. Hasta completar o reintentar TR-069, service-health no puede resolver identidad ACS.

Acción: `POST /subscription/2327/acs/retry-tr069` (o flujo de alta desde app) tras el deploy.
