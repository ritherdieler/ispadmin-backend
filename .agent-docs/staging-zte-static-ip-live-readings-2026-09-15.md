# Staging — ZTE lab #5 en STATIC_IP (2026-09-15)

Cliente ya existente: `#5` EEEFIBER PRUEBAZTE, ONU `ZTEGDC47BFFD` (GenieACS `5872C9-F6600R-ZTEGDC47BFFD`, tag `lab`). No es `#6` VSOL ni `#7` WIRELESS.

## Qué se cambió (staging BD + MK2, sin WAR)

| Campo | Antes | Después |
|-------|-------|---------|
| `access_mode` | `PPPOE_DYNAMIC` | `STATIC_IP` |
| `ip` | NULL | `10.64.0.11` (sesión PPPoE `gf5` en MK2) |
| `pppoe_last_ip` | NULL | `10.64.0.11` |
| `pppoe_username` | `gf5` | NULL (el secret `gf5` sigue en MK2; la sesión no se cortó) |
| `fiber_onu_sn` | `ZTEGDC47BFFD` | igual |

Cola nueva (no reutilizada):

| | |
|---|---|
| `.id` | `*8A2` |
| `name` | `[stg] id:5, usuario:EEEFIBER PRUEBAZTE, lugar:9 de octubre, nap:NO-001, plan:basico, tipo:FIBER` |
| `target` | `10.64.0.11/32` |
| `max-limit` | `200M/200M` |

No se usó `*89A` `<pppoe-gf5>` (dinámica), ni `*8A1` `#7` `192.168.250.16`, ni `id:6` de prod.

WAN ACS en GenieACS: `192.168.255.236` (mgmt, no plano de cliente).

## Poll `GET /subscription/5/live-readings` (~19:56Z)

`source=QUEUE`, `pppoe=null` en 6/6. bps 0/0, `rxBytes`/`txBytes` 0. Eso es la cola `*8A2` vacía, no residual de otra persona (`*89A` tenía ~100 KB; Cintia ~106 GB).

La sesión ZTE está idle (interfaz `<pppoe-gf5>` rx=0, tx~211 KB). No hay YouTube en este CPE. `#6` `gf6` sí tiene caudal PPPoE, otro cliente.

Cierre real (sin PPPoE, cola con tráfico): [staging-zte-5-static-queue-live-readings-2026-09-15.md](./staging-zte-5-static-queue-live-readings-2026-09-15.md).
