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
