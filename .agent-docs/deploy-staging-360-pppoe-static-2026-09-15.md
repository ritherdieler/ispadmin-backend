# Deploy staging 360 — PPPoE e IP estática (2026-09-15)

```bash
FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth
npm run build:staging && rsync dist/ → /var/www/gigafiber/backoffice-staging/
```

Tomcat-staging `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó.

La línea `Waiting for http://127.0.0.1:8081/ispadmin-staging/` es el health check post-restart: Tomcat explota el WAR (~136 MB, 4 módulos) y Spring Boot arranca. El script hace curl cada 5 s hasta 200/302 (tope ~7.5 min). En este run ~3 min y contestó 200.

## Validación API (mismo día)

| Caso | Id | Identidad 360 | Tráfico |
|------|----|---------------|---------|
| FIBER `PPPOE_DYNAMIC` | `#6` `gf6` | `PPPOE=gf6` `ROUTER=8` (sin `IP`) | `traffic/latest` 200, cola `*89C`, hoy 4 puntos, último bucket ~4.3 Mbps down |
| WIRELESS `STATIC_IP` | `#7` `192.168.250.16` | `IP=192.168.250.16` `ROUTER=8` (sin `PPPOE`) | `traffic/latest` 200, cola `*8A1`, poller escribe samples (idle 0 Mbps) |

DNS `backoffice-staging.gigafiberperu.cloud` no resuelve desde la Mac; el SPA quedó en el VPS. Vista 360: Vite `--mode staging` o el host que ya use el usuario, hard refresh.
