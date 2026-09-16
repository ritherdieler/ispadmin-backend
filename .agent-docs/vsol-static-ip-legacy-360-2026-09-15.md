# VSOL e2e = STATIC_IP (clientes IP legacy) — 2026-09-15

## Emparejamiento lab

| ONU | Alta | Tráfico 360 |
|-----|------|-------------|
| ZTE `ZTEGDC47BFFD` | FIBER `PPPOE_DYNAMIC` | XOR usuario `pppoe:` |
| VSOL `VSOL0031C0B6` | FIBER `STATIC_IP` | XOR IP `/32` + cola simple |

VSOL es el cliente legado con ONU real (óptica + Inform Wi‑Fi/estaciones + cola por IP). #10 WIRELESS no sirve para ACS.

## Cómo se pide

`POST /subscription` puede mandar `accessMode=STATIC_IP`. `PppoeAltaPolicy` lo honra en FIBER: asigna IP del pool, no crea secret PPPoE. `FiberInstallationStrategy` crea cola simple y manda IP al Gateway, no usuario PPPoE.

Sin ese campo, FIBER sigue naciendo PPPoE.

Android e2e: `E2E_ONU_SN=VSOL0031C0B6` pone `e2e.accessMode=STATIC_IP` (`E2eAccessModeResolver`). ZTE no manda override.

## Verificación

```bash
./gradlew :core:test \
  --tests "com.dscorp.wispadmin.wispadmin.service.subscription.PppoeAltaPolicyTest" \
  --tests "com.dscorp.wispadmin.wispadmin.service.subscription.strategies.FiberInstallationStrategyTest"
```

Tras deploy staging:

```bash
E2E_ONU_SN=VSOL0031C0B6 ./scripts/e2e_register_fiber_staging_espresso.sh --cleanup-mode skip
```

`GET /subscription/{id}` → `accessMode=STATIC_IP` + IP. Directorio tráfico: `ip` sí, `pppoeUsername` no. 360: `pilot_enabled=true` (gate tagged = todos los ids).

Staging vivo **2026-09-15**: VSOL es `#11` `192.168.250.11`. Detalle: [staging-vsol-11-static-ip-2026-09-15.md](./staging-vsol-11-static-ip-2026-09-15.md).
