# Ext Wi‑Fi notify: lab → prod+staging, flota → prod — 2026-09-17

## Qué

`scripts/genieacs/ext/wifi-inform-notify.js` POST `inform-notify` a:

| ONU | Destino |
|-----|---------|
| Lab `ZTEGDC47BFFD`, `12345B4641531C0B6` | `GENIEACS_TO_ACS_NOTIFY_URL` (prod) **y** `GENIEACS_TO_ACS_STAGING_NOTIFY_URL` |
| Resto | solo prod |

POSTs en paralelo (timeout 2500 ms). Si staging está caído, lab igual entrega prod.

## Env (nombres)

`/opt/gigafiber/genieacs/.env` + compose:

- `GENIEACS_TO_ACS_NOTIFY_URL` = `http://tomcat9027:8080/ispadmin`
- `GENIEACS_TO_ACS_STAGING_NOTIFY_URL` = `http://tomcat-staging:8080/ispadmin-staging`
- `GENIEACS_TO_ACS_API_KEY` (misma pareja Tomcat)

Tras cambiar `.env` o el JS: copiar ext al volumen `GENIEACS_EXT_DIR` y recreate `gigafiber-genieacs`.

## Aplicado VPS (2026-09-17)

`gigafiber-genieacs` recreate. Runtime:

- `GENIEACS_TO_ACS_NOTIFY_URL=http://tomcat9027:8080/ispadmin` (health 200)
- `GENIEACS_TO_ACS_STAGING_NOTIFY_URL=http://tomcat-staging:8080/ispadmin-staging` (`tomcat-staging` sigue down; lab no bloquea prod)
- Ext con `LAB_SERIALS` en `/opt/genieacs/ext/wifi-inform-notify.js`
- Backups: `.env.bak.notify-dual-*`, `docker-compose.yml.bak.notify-dual-*`

## Tests

`node --test scripts/tests/wifi-inform-notify-ext.test.cjs`
