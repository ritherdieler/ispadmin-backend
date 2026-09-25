# Lote TR-069 VLAN 1000 — VSOL invertidas (2026-09-19)

Inventario NBI V2804AX15T: **254**. Lab 1. CR ya en `10.20.0.0/22`: 141. Pendientes en `.252/22`: **108**.

Lote `108` vía Core prod + ping internet. Duración ~42 min. **Ninguna ONU perdió internet que ya tuviera ping.**

| Resultado | N |
|--|--|
| OK | 96 |
| FAIL | 12 |
| Ping internet OK antes y después | 56 |
| Internet ya caído antes (no se finge éxito) | 40 |
| Internet se perdió en el retag | 0 |

FAIL (reintento aparte): `VSOL00F5C7B6`, `VSOL00870569`, `VSOL00870309`, `VSOL00F61D46`, `VSOL00F5E7B6`, `VSOL00F5F0D6`, `VSOL00F5E206`, `VSOL00F5F6B6`, `VSOL00F62566`, `VSOL00F5DFA6`, `VSOL0086F109`, `VSOL0031FAE6`.

Log: `/tmp/vsol-tr069-batch.log`.

## Recuento vivo NBI 2026-09-19 17:50Z

Inventario V2804AX15T: **256**. Lab 1 (ya en `10.20/22`).

| CR WAN | N (no-lab) |
|--------|------------|
| `10.20.0.0/22` (migradas) | **246** |
| `192.168.252.0/22` (pendientes) | **8** |
| Otra IP (internet `192.168.30.170`) | **1** (`VSOL003217E6`, Inform 2026-09-09, sin WAN TR-069) |

Las 8 pendientes tienen Inform **>24 h** (26 h–11 d). El runner 17:51Z hizo `ensure-mgmt` + task NBI `202` (CPE no contestó CR). El CR NBI **sigue** en `.252/22` hasta el próximo Inform.

| SN | OLT ensure-mgmt | Notas |
|----|-----------------|-------|
| `VSOL0031D846` | VSOL `1/15/31` VLANs 100,1000 | Task en cola. Inform 2026-09-09. `sub-2351` |
| `VSOL0031EF96` | VSOL `1/8/59` VLANs 100,1000 | Task en cola. Inform 2026-09-08 |
| `VSOL0031FAE6` | 404 `UPSTREAM_NOT_FOUND` | No está en inventario OLT. Ya estaba en el FAIL del lote 108 |
| `VSOL0086CB69` | VSOL `1/12/8` VLANs 100,1,1000 | Task en cola. Inform 2026-09-14. `sub-1786` |
| `VSOL00F5F736` | resolvió **HWTC15F5F736** `0/2/11` (ya 1,100,1000) | Sufijo hex 6 choca con Huawei; VSOL no está en OLT. `sub-1839` |
| `VSOL00F5FB36` | **HWTC15F5FB36** `0/4/19` (ya 1,100,1000) | Igual. `sub-1879` |
| `VSOL00F60E26` | **HWTC15F60E26** `1/8/52` (ya 1,100,1000) | Igual. `sub-2153` |
| `VSOL00F628E6` | **HWTC15F628E6** `0/4/25` (ya 1,100,1000) | Igual. `sub-1889` |

`ensure-mgmt` busca por sufijo hex 6 (`findBySn`). En los 4 `HWTC*` el Huawei ya tenía VLAN 1000: no-op, internet no se tocó. El provision `gf-tr069-vlan1000` no escribe WAN de internet ni IPs fuera de `.252/22`; `VSOL003217E6` no aplica.

Siguiente: esperar Inform de las 3 VSOL con SP 1000 real; las 4 de sufijo Huawei y `VSOL0031FAE6` no tienen ONU VSOL en OLT (CPE muerto o SN distinto).

## Por qué las 8 no informan después del retag (2026-09-19 18:13Z)

No es el retag. Ya estaban mudas **antes**. El provision **no corrió** en el CPE.

| Señal | Hallazgo |
|-------|----------|
| `lastInform` vs retag 17:51Z | 0/8 informaron después. Ya llevaban 26 h–11 d de silencio |
| Control parque `10.20` | 239 VSOL migradas **sí** informaron después de 17:51Z. ACS/NBI sanos |
| Tasks NBI | 7/8 tienen **2** `gf-tr069-vlan1000` pendientes (08:00Z y 17:51Z), sin `expiry`. `VSOL0031FAE6` ninguna (ensure-mgmt 404) |
| Faults NBI | 0 |
| HTTP POST `connection_request` | 202 en ~2 s (no esperó los 45 s): CR inalcanzable |
| CWMP access | Cero `Inform` / `CONNECTION REQUEST` / `Script: gf-tr069-vlan1000` de estas 8 el 19 sep |
| Ping VPS (`wg-olt`) | CR `192.168.255.*` e internet de las 6 con cliente: **100% loss**. Control migrada `10.20.1.12` + `192.168.220.107`: **0% loss** |
| Periodic | 180 s. Si el CPE viviera, habría Inform cada 3 min |

`VSOL00F628E6` (Bautista): último tramo 18 sep PERIODIC cada 3 min hasta 13:57Z, `1 BOOT` 15:27Z, dos PERIODIC, silencio en 15:33Z. El retag fue 26 h después.
