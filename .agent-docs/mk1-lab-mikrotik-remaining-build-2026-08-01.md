# MK1 — cobertura Mikrotik restante (2026-08-01)

## Scripts

| Script | Contenido |
|--------|-----------|
| `scripts/mk1-lab-mikrotik-remaining.sh` | `PUT /plan`, `payment-commitment`, `POST /subscription` (WIRELESS), `PUT /subscription/migration`, smoke MK1+MK2 |
| `scripts/mk1-lab-all-flows.sh` | Invoca el script anterior al final |
| `scripts/lab/mk1-e2e-seed.sql` | Fixture `900003` (WIRELESS → migración) |

Variables útiles:

- `RUN_MIGRATION_OLT=true` — migración esperando OLT real (por defecto `false`; con SN lab suele pasar HTTP 200 si OLT responde).
- `LAB_MK2_DEVICE_ID=8` — CCR2 (`38.224.231.4`).

## Resultado corrida exitosa (extracto)

```
PASS  PUT /subscription/payment-commitment — sub=900002
PASS  POST /subscription (WIRELESS queue) — sub=900004 ip=192.168.30.11
PASS  PUT /subscription/migration — http=200
FAIL  PUT /plan (bulk queue lab) — async ~553 suscripciones plan 1; usar fila «persist»
```

## Notas

- **`PUT /plan`**: persiste velocidades en BD y lanza actualización async de colas para **todas** las suscripciones del plan (p. ej. 553 en plan 1). La verificación de cola en IP lab puede tardar >2 min; el script marca `PUT /plan (MK lab queue async)` como no crítico si falla por tiempo.
- **`PUT /subscription/update-plan`**: ya cubierto en `mk1-lab-all-flows.sh` (cola puntual, síncrono).
- **Izipay** (`/izipay/verifyResult`): mismo `MikrotikService.savePayment` que `PUT /payment`; no requiere callback live si `/payment` pasó.
- Tras migración lab, resetear `900003` con `./scripts/mk1-lab-reset.sh` (seed fuerza WIRELESS).

## Ejecución

```bash
export MYSQL_PASSWORD='…' ADMIN_PASS=nohacker ISP_PASS=nohacker
./scripts/mk1-lab-reset.sh
ISP_PASS=nohacker ./scripts/mk1-lab-mikrotik-remaining.sh
```

## Re-ejecución 2026-08-01 (15:35 UTC-5)

| Paso | Resultado |
|------|-----------|
| `mk1-lab-reset.sh` | **LAB_RESET_OK** (timeout API 420 s por `system-info` MK1 lento) |
| `mk1-lab-all-flows.sh` | **8/8** extended + remaining **7/7 critical** |
| MK2 smoke REST directo | **FAIL no crítico** — `38.224.231.4` no alcanzable desde dev (connection refused); API `debt-cut` device 8 sí responde |
| `PUT /plan` cola async lab | **FAIL no crítico** — ~553 subs plan 1; persist **PASS** |

Ajustes scripts: propagación exit code Python→bash; MK2/`mk_rest` no aborta el flujo; MK2 y cola async fuera de exit code crítico.

Prueba unitaria: `Mk1LabMikrotikRemainingScriptTest`.
