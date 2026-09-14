# Service Health — refrescos manuales sin cooldown (2026-09-03)

## Decisión

`WIFI_REFRESH` y `OPTICAL_REFRESH` (botones «Actualizar Wi‑Fi» / «Actualizar óptica» en el 360) **no** aplican el cooldown de `service.health.action-cooldown-seconds` (default 600 s) ni el bloqueo de muestra Wi‑Fi &lt; 15 min. Se pueden solicitar al instante, una tras otra.

## Qué se mantiene

| Restricción | Aplica a |
|-------------|----------|
| Cooldown `action-cooldown-seconds` | `CONFIG`, `REBOOT_*` (y cualquier acción no-refresh) |
| Límite `cr-concurrency` ACS | Solo cuando la acción usa Connection Request (`WIFI_REFRESH`, `CONFIG`, `REBOOT_ACS`) |
| `ACS_STALE` (Inform reciente) | Antes de enviar CR en Wi‑Fi / config / reboot ACS |
| Pilot / `actions-enabled` / identidad | Todas las acciones remotas |
| Idempotency-Key | Replay de la misma clave |

## Código

- `RemoteActionService.reserve`: salta cooldown si `action` es `WIFI_REFRESH` u `OPTICAL_REFRESH`.
- `RemoteActionService.refresh`: ya no rechaza por muestra Wi‑Fi reciente (&lt; 15 min).
- `ServiceHealthController.actionPolicy`: `cooldown_until` siempre `null` para wifi/óptica (solo `last_action_at` informativo).
- Backoffice `ServiceHealthPage`: textos sin «10 minutos» / frescura de muestra.

## Tests

`RemoteActionPersistenceTest` (reserva inmediata wifi→wifi y óptica→óptica) y `RemoteActionWifiRefreshTest` (muestra &lt; 15 min aceptada).

```bash
./mvnw test "-Dtest=RemoteActionPersistenceTest,RemoteActionWifiRefreshTest"
```

## Nota operativa

El poller ACS automático sigue con su cadencia/GPV cooldown propios (`acs-wifi-sample-target-seconds`, `acs-gpv-cooldown-seconds`). Eso no limita el botón manual.
