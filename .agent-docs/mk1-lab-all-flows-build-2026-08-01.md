# MK1 lab — ejecución completa de flujos (2026-08-01)

## Cambios

- `scripts/lab/mk1-e2e-seed.sql`: suscripción `900002` (`192.168.250.2`, fecha suscripción 2024-06-01 para reactivación sin validación de borne).
- `scripts/mk1-lab-all-flows.sh`: update-plan, POST/DELETE ip-pool (`192.168.250.250/32`), cancel/reactivate `900002`, PUT `/payment` fixture `900001`.

## Re-ejecución 2026-08-01 (15:35 UTC-5)

| Script | Resultado |
|--------|-----------|
| `mk1-lab-reset.sh` + `mk1-lab-all-flows.sh` | **exit 0** — 8/8 + remaining critical 7/7 |

Log local: `/tmp/mk1-lab-full-run3.log`.
