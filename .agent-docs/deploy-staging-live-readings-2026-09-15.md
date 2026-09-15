# Deploy staging — lecturas en vivo (2026-09-15)

```bash
FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth
```

Desde `~/gigafiber/ispadmin-backend` rama `cursor/vista-360-live-readings-b5b0` @ `abd4499` (`APP_RELEASE=1.0.3+b09932f` en el WAR; docs commit posterior). Tomcat `tomcat-staging` `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó.

El primer intento (`5236043`) dejó el context en 404: constructor secundario de `Clock` → Spring no instanciaba `SubscriptionLiveReadingService`. Fix en `b09932f` (un solo constructor).

## Validación #6 `pppoe:gf6`

`GET https://api.gigafiberperu.cloud/ispadmin-staging/subscription/6/live-readings` con JWT.

Sin token: 401. Con ADMIN: 200, `available: true`, `source: QUEUE`, `pppoe: null`, `rate`/`bytes` de `/queue/simple`.

Backoffice: `VITE_API_BASE_URL=https://api.gigafiberperu.cloud/ispadmin-staging` ([PR #2](https://github.com/ritherdieler/ispadmin-backoffice/pull/2) poll 2s). Vista 360 de #6 debe dejar de mostrar “aún no hay lecturas en vivo”.
