# Deploy prod — Wi-Fi ACS por `observed_at` (V36) — 2026-08-31

## Backend

| Campo | Valor |
|-------|--------|
| Commit | `7f7f9af` — `fix(service-health): persistir Wi-Fi ACS solo cuando el Inform refresca parámetros` |
| Release | `1.0.3+7f7f9af` |
| Deploy | `./scripts/deploy.sh --deploy` OK; deploy event registrado |
| Smoke | `GET https://api.gigafiberperu.cloud/ispadmin/` → HTTP 200 |
| `APP_RELEASE` | `1.0.3+7f7f9af` en `/opt/gigafiber/.env` |

No se tocó backoffice ni GenieACS (el preset `gigafiber-wifi-telemetry` ya estaba en los dos F6600R del piloto).

## MySQL (antes del WAR)

Flyway no está activo. Orden: backup → V36 → WAR.

| Paso | Resultado |
|------|-----------|
| Backup | `/opt/gigafiber/backups/wifi-v36-pre-20260831114521.sql` (27K; tablas `acs_wifi_count_sample`, `acs_wifi_station_sample`, `acs_wifi_status_current`) |
| UNIQUE previo | `uk_sh_wifi_reading` = `(device_id, subscription_id, inform_at)` |
| UNIQUE posterior | `uk_sh_wifi_reading` = `(device_id, subscription_id, observed_at)` |
| Filas | 103 → 8 FRESH (94 `MISSING`/`observed_at` null borradas; 1 duplicado colapsado). Cursor piloto 2310/2328 quedó `FRESH`. |

Hibernate `ddl-auto=update` **no** recreó el UNIQUE viejo tras el arranque.

## Ventana del WAR viejo

V36 se aplicó con Tomcat aún en `8e46ea4`. El watcher legado persistió 2 `MISSING` a las 16:46 UTC y volvió a marcar el cursor como `MISSING`. Tras el WAR nuevo se borraron esas filas y se restauró el cursor desde la última muestra `FRESH`.

## Verificación del watcher nuevo

Corridas ACS posteriores al WAR (`telemetry_source_run`): `read_count=2`, `written_count=0`, `missing_count=2` (Informs sin GPV Wi-Fi). `acs_wifi_count_sample` sigue en 8 filas / 0 `MISSING`. Cursor 2310 y 2328 permanece `FRESH`.

La siguiente muestra `FRESH` llegará con el próximo `2 PERIODIC` que refresque `TotalAssociations` (~1 h).
