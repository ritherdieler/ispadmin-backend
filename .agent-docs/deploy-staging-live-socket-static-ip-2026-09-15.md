# Deploy staging — live socket STATIC_IP / accessMode XOR (2026-09-15)

## Qué se desplegó

| Pieza | Ref | Destino |
|-------|-----|---------|
| Core WAR | `1.0.3+3fe25fc` branch `cursor/static-ip-live-socket-d0ce` | `tomcat-staging` `:8081` `/ispadmin-staging/` |
| Módulos | `oltgateway,traffic,acs,servicehealth` | mismo WAR |
| Backoffice SPA | `d6f0181` mismo branch | `/var/www/gigafiber/backoffice-staging/` |

## Comandos

```bash
FORCE_WAR_REBUILD=1 DEPLOY_CONFIRM_DISABLED_MODULES=yes \
  ./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth

cd ../ispadmin-backoffice
npm run build:staging
# rsync --delete dist/ → VPS /var/www/gigafiber/backoffice-staging/
# credenciales: scripts/deploy.config.local
```

## Health

| Check | Resultado |
|-------|-----------|
| `GET https://api.gigafiberperu.cloud/ispadmin-staging/` | HTTP 200 |
| Login staging | OK |
| `#5` `accessMode` | `STATIC_IP`, `ip=192.168.250.20`, sin PPPoE |
| SPA `https://gigafiberperu.cloud/backoffice-staging/` | HTTP 200 |
| Asset `SubscriptionTrafficPanel-BFlHmi5g.js` | HTTP 200 |

DNS `backoffice-staging.gigafiberperu.cloud` no resuelve desde la Mac; nginx en el VPS sí lo sirve. Alternativa pública: `https://gigafiberperu.cloud/backoffice-staging/`.

## Contenido relevante

- Directorio Traffic: un solo modo por suscripción (`IP` xor `PPPOE`) según `accessMode`.
- Core `CoreTrafficStreamRelay` / start STOMP: identidad solo desde directorio; el browser manda `{subscriptionId}`.
- Identidad service-health PPPoE + panel 360 host-only (sin IP cuando no aplica).

Prod no se tocó.
