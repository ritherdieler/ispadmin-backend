# Deploy prod wifi-inform-core — 2026-09-18

## Qué

`develop` @ `7982e58` (`1.0.3+7982e58`) con carril `wifi-inform-core`. Preflight confirmado. `DEPLOY_CONFIRM_DISABLED_MODULES=yes ./scripts/deploy.sh --env prod` → HTTP 200.

## Post-deploy

| Check | Resultado |
|-------|-----------|
| Thread `gigafiber-redis-wifi-inform-core` | Sí (#50) |
| Lag grupos al arranque | ~MAXLEN → `XGROUP SETID … $` en `snapshot-core` y `wifi-inform-core` |
| `acs_wifi_count_sample` MAX(inform_at) | `2026-09-18 05:45:20` UTC |
| Filas últimos 5 min | 40 |
| Stations MAX | `2026-09-18 05:44:55` |

Store: `docs/deploy-prod-wifi-inform-lane-2026-09-18.md`.
