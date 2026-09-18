# develop local — wifi-inform (2026-09-18)

Sin deploy. Sin push a `origin/develop`.

## Qué ya estaba

- `7982e58` merge `cursor/wifi-inform-consumer-lane-7e12` (carril Core).
- `6cf2b14` nota deploy prod `1.0.3+7982e58`.

## Qué entró ahora

- Merge `cursor/wifi-inform-notify-lab-dual-7e12` → `77f9f78`.
- Ext lab fan-out prod+staging, tests, docs de gap `snapshot-core`.
- Link roto a nota prod-wifi-telemetry no presente: quitado.

## Fuera

- Stash `wip-unrelated-before-wifi-inform-lane` (pppoe/docs/wifi-refresh, no de este merge).
- Diagnóstico #2373 solo en Agent Store (`internal/wifi-2373-gap.md`).
- Backoffice: sin ramas de este hilo; dirty live-readings en `cursor/backoffice-prod-deploy-dc9c`.
