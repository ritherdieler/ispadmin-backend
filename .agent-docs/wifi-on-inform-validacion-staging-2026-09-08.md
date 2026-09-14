# WiFi-on-Inform — validación staging VSOL (2026-09-08)

**Canónico (cómo funciona):** [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md). Esta nota es evidencia E2E staging.

## Resultado: PASS (con matices)

| Señal | Resultado |
|-------|-----------|
| Secretos VPS (nombres) | SET: `GENIEACS_TO_ACS_API_KEY`, `GENIEACS_TO_ACS_NOTIFY_URL`, `ACS_TO_GATEWAY_API_KEY`, `SERVICE_HEALTH_ACS_POLL_ENABLED=false`. `ACS_GATEWAY_BASE_URL` horneada en WAR ACS staging (no `.env`). |
| GenieACS ext | `wifi-inform-notify.js` en volumen `GENIEACS_EXT_DIR`; compose GenieACS pasa `GENIEACS_TO_ACS_*`. |
| Provision piloto | Solo `B46415-V2804AX15T-12345B4641531C0B6` (VSOL lab). |
| Deploy staging | ACS + Gateway + Core. Core con `--with servicehealth,netdiag,observability`. |
| `POST …/inform-notify` | HTTP **200** `COMPLETE` |
| ACS `cpe_record` last-state | `sn=12345B4641531C0B6`, `wifi_associated_total=3`, `quality=FRESH`, snapshot JSON presente |
| Gateway → Redis `stg:gigafiber.events` | Eventos `type=cpe.inform` con payload WiFi completo |
| Core `acs_wifi_count_sample` | Fila `id=3422`, `subscription_id=2389`, device VSOL, 2g=2 / 5g=1 / total=3, `FRESH` |

## Fixes aplicados en el camino

1. **Disco lleno** en Mac al empaquetar ACS (`No space left on device`) → limpieza de `target/` y reintento.
2. **Bake Redis Gateway**: Ant `echo` concatenaba `redis` + `gigafiber.redis.namespace=stg` → host inválido `redisgigafiber…`. Corregido en `pom.xml` (oltgateway/traffic staging) con `${line.separator}`.
3. **Core sin servicehealth** fallaba (`AcsSubscriptionPort` / adapters NetDiag/Observability) → empaquetar Core con `servicehealth,netdiag,observability`.
4. **Persist Core**: `persistFromEventJson` sin `@Transactional` (self-invoke) → `TransactionRequiredException`. Anotación añadida; test `CpeInformPersistServiceTest`.

## Matices / follow-up

- ~~`acs_wifi_station_sample` vacío y `acs_wifi_status_current` (sub 2389) no se actualizó~~ → **PASS en revalidación** (count **3424**, 2 stations, `status_current` refrescado). Detalle: [wifi-on-inform-fix-stations-status-2026-09-08.md](./wifi-on-inform-fix-stations-status-2026-09-08.md).
- `GENIEACS_TO_ACS_NOTIFY_URL=http://tomcat-staging:8080/ispadmin-staging-acs`.
- `GENIEACS_TO_ACS_API_KEY`: valores rotados en `.env` (nombres solo); contenedores pueden seguir con el valor previo en memoria hasta recreate GenieACS (`/opt/gigafiber/genieacs`) + `tomcat-staging`. No hay valor en git.
- Auto-`ext` tras CR: en la revalidación no apareció en logs; se cerró el camino con `POST inform-notify` (mismo contrato). Revisar provision/ext si se quiere notify 100% automático en cada CR.
- `MYSQL_ROOT_PASSWORD` en `/opt/gigafiber/.env` está vacío; MySQL root vive en env del contenedor `mysql8033`.

## Evidencia operativa

- Suscripción lab: `subscription_id=2389`, `genieacs_device_id=B46415-V2804AX15T-12345B4641531C0B6`.
- Health: Core `/ispadmin-staging/` 200; ACS/Gateway `actuator/health` 200.
- Revalidación stations/status: count **3424** / stations **2** / `status_current.count_sample_id=3424` (2026-09-08 ~21:56Z).
