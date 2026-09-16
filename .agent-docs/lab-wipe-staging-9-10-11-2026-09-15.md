# Wipe staging #9 #10 #11 — 2026-09-15

Hard cleanup de las tres suscripciones lab que quedaban en staging. No se tocó prod.

## Antes

| Id | ONU | Alta |
|----|-----|------|
| 9 | `ZTEGDC47BFFD` | FIBER `PPPOE_DYNAMIC` `gf9` |
| 10 | — | WIRELESS `STATIC_IP` `192.168.250.10` |
| 11 | `VSOL0031C0B6` | FIBER `STATIC_IP` `192.168.250.11` |

## Qué se borró

`scripts/tr069-e2e-hard-cleanup.sh --env staging`:

- MK2: secret/sesión `gf9`; colas `[stg]` `*8AC` (#11) y `*8AB` (#10)
- OLT Gateway: delete `gigafiber-ma5608t_1_6_0` (ZTE) y `gigafiber-ma5608t_1_6_16` (VSOL)
- Core `ispadmin_staging`: filas #9 #10 #11 + hijos / health
- Traffic `stg_traffic`: samples de esos ids / IP / `pppoe:gf9`
- Gateway `stg_oltgateway`: journal + `olt_mgr_onu` de ambos SN

Firebase no corrió (falta SA local). MySQL/OLT/MK sí.

## Después

| Check | Resultado |
|-------|-----------|
| `GET /subscription/all` | `0` |
| `GET /onu/unconfigured_onus` | `ZTEGDC47BFFD` y `VSOL0031C0B6` libres |

GenieACS devices + tag `lab` se conservan.
