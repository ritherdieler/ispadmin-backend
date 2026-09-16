# Traffic WAR — desacople IP + subscription_id (2026-09-03)

El collector de tráfico deja de leer `Subscription`/`NetworkDevice` por JDBC. Recolección en `/queue/simple`; atribución `subscription_id` vía directorio REST del core. El Core elige la identidad según `accessMode` (PPPoE dinámico = username; estático/fijo = IP). Matriz y verificación: [traffic-directorio-access-mode-2026-09-15.md](./traffic-directorio-access-mode-2026-09-15.md).

## Identidad

| Rol | Campo | Origen |
|-----|--------|--------|
| Recolección | `client_ip` | IP `/32` o `pppoe:{username}` (cola `<pppoe-user>`) |
| Atribución | `subscription_id` nullable | `GET /internal/traffic/targets` |

UK de muestra: `(client_ip, bucket_start)`. Si el directorio falla, el poll sigue y deja `subscription_id` nulo.

## HTTP

Auth de frontera: header `X-Traffic-Key` (`TRAFFIC_API_KEY`). URLs internas **horneadas** en el WAR (`traffic.internal-base-url`, `traffic.core-base-url`); no van en `/opt/gigafiber/.env`.

| Dirección | Ruta |
|-----------|------|
| Core → traffic | `GET /api/traffic/v1/by-subscription/{id}/latest\|series\|summary\|today\|day` |
| Core → traffic | `GET /api/traffic/v1/by-ip/{ip}/latest\|series` |
| Core → traffic | `GET /api/traffic/v1/network\|overview\|series\|sources\|anomalies` |
| Traffic → core | `GET /internal/traffic/targets` |
| Público (BFF core o in-process) | `/subscription/{id}/traffic*` y `/traffic/bandwidth/v1/*` |

Health 360 usa `HealthTrafficHttpClient` (ya no hay adapter JDBC).

## WAR

| Artefacto | Perfil Maven | Context | Schema |
|-----------|--------------|---------|--------|
| `ispadmin-staging-traffic.war` | `traffic-staging-war` | `/ispadmin-staging-traffic` | `stg_traffic` |
| `ispadmin-traffic.war` | `traffic-war` | `/ispadmin-traffic` | `prod_traffic` |

`TrafficApplication` escanea `traffic` + `routeros`. El core staging **excluye** clases `traffic`; `--with traffic` solo enciende el cliente HTTP. `oltgateway` sigue empaquetado en el WAR core cuando va en `--with`. `PlatformAuthFilter` deja pasar `/internal/traffic/**` (auth por `X-Traffic-Key`).

Deploy staging: `./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic` sube core **y** traffic WAR.

## Schema

Scripts (no ejecutar sin OK explícito):

- `scripts/sql/migrate-traffic-to-stg_traffic.sql`
- `scripts/sql/migrate-traffic-to-prod_traffic.sql`

Hasta el migrate, el WAR traffic puede crear `stg_traffic` vacío (`ddl-auto=update`) y sembrar `traffic_router` desde `ispadmin_staging.network_device`.

## E2E lab VSOL

Corrido el 2026-09-03: `npm run e2e:traffic-vsol-staging` en `ispadmin-backoffice` (Vite `--mode staging` en `http://127.0.0.1:3010`).

Fixture: suscripción `#2360` (EEEFIBER PRUEBA, SN `VSOL0031C0B6`, IP pool `192.168.250.20`, host CCR2 id 8, cola RouterOS `*7AB`).

| Paso | Resultado |
|------|-----------|
| `GET /subscription/2360/traffic/latest` | 200, `ip=192.168.250.20`, `sampleStatus=OK` |
| `GET /subscription/2360/traffic?granularity=sample` | 200, puntos de serie presentes |
| UI `/subscriptions/2360/service-health` | panel `subscription-traffic-panel` visible; tab Hoy + resumen |

Cola lab creada en CCR2 (`38.224.231.4`) porque no existía `target=192.168.250.20`. Schema `stg_traffic` vacío (sin migrate histórico; no ejecutar `migrate-traffic-to-stg_traffic.sql` sin OK). Poll escribe muestras; `subscription_id=2360` vía directorio REST.

`GET .../traffic/summary` sin rollup mensual/diario debe devolver DTO con ceros (`rxGbTotal=0`), no `{}`. Hotfix desplegado en `ispadmin-staging-traffic.war` el 2026-09-03. El panel 360 no debe romper si el BFF aún manda un objeto vacío.

## Hotfix WS traffic

El socket live de tráfico quedó confirmado como responsabilidad del WAR desacoplado (`/ispadmin-staging-traffic/ws`); el core solo lo consume desde front/BFF.

Cambios aplicados el 2026-09-03:

- `TrafficWebSocketConfig` ahora protege `/ws` con `TrafficWebSocketHandshakeInterceptor`, validando el mismo `observability.session.secret` y query param `token` que usa el backoffice.
- `ispadmin-staging-traffic.war` recompilado con perfil `traffic-staging-war` y redeployado manualmente al contenedor Tomcat de staging.
- Backoffice: `subscriptionTrafficWebsocketService` conserva suscripciones pendientes mientras conecta, reintenta al cerrarse y re-suscribe al reconectar.
- Backoffice: `useSubscriptionTrafficLive` registra listeners antes del `connect()` para evitar una carrera donde la UI quedaba en `socket sin conexión` aunque el STOMP ya hubiera llegado a `CONNECTED`.

Verificaciones:

- `./mvnw test -Dtest=TrafficWebSocketHandshakeInterceptorTest -q`
- `npm test -- --run src/services/subscriptionTrafficWebsocketService.test.ts src/components/subscriptions/SubscriptionTrafficPanel.test.tsx src/lib/stagingApiBase.test.ts`
- Logs staging posteriores al deploy: `WebSocketMessageBrokerStats` del WAR traffic reporta `processed CONNECT/CONNECTED` y sesiones WS activas.

No se desplegó prod en este ciclo.
