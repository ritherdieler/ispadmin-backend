# Lote TR-069 VLAN 1000 — F6600R (2026-09-19)

Inventario NBI F6600R: **56**. Lab 1 (`ZTEGDC47BFFD`, ya `10.20.0.158`). Fuera de alcance: `ZTEGDC47DABE` CR `192.168.93.229` (vivo) y `ZTEGDC47DAA0` CR `192.168.88.140` (Inform 2026-08-26). Pendientes en `.252/22` al arrancar: **39**.

Piloto `ZTEGDC47BF9F` + lote 38 vía Core prod + ping internet. Duración lote ~13 min + reintento 3× ~1 min. **Ninguna ONU perdió internet.**

| Resultado | N |
|--|--|
| OK (CR en `10.20.0.0/22`) | 39 |
| FAIL persistente | 0 |
| Internet se perdió en el retag | 0 |
| Lab / CR no invertido | 3 (skip) |

FAIL primer paso (reintento OK): `ZTEGDC47DF15`, `ZTEGDC47DFB5`, `ZTEGDC47DFB7` — Core `ensure-mgmt` HTTP 502 `UPSTREAM_FAILURE` (Gateway). No se tocó NBI. Reintento 18:51Z: HTTP 200, internet intacto, CR `10.20.1.153`–`155`.

Log lote: `/tmp/zte-tr069-batch.log`. Lista: `/tmp/zte-tr069-vlan1000-rest.tsv`.

## Piloto

| Campo | Valor |
|-------|--------|
| SN | `ZTEGDC47BF9F` |
| Enqueue | HTTP 200 |
| Internet | `192.168.30.243` (se mantuvo) |
| OLT | `1/9/3` VLANs 100,1,1000 |
| CR | `10.20.1.117` |
| TR-069 VLAN | 100 → 1000 |

## Recuento vivo NBI 2026-09-19 18:52Z

Inventario F6600R: **56**. Lab 1 (ya en `10.20/22`).

| CR WAN | N (no-lab) |
|--------|------------|
| `10.20.0.0/22` (migradas) | **53** |
| `192.168.252.0/22` (pendientes) | **0** |
| Otra IP (no invertida) | **2** (`DABE` `192.168.93.229`, `DAA0` `192.168.88.140`) |

El provision `gf-tr069-vlan1000` en F6600R escribe solo WAN TR-069 (`X_ZTE-COM_VLANID`); no toca WAN de internet.

Siguiente (otras marcas, no F6600R): 8 VSOL leftovers con Inform >24 h; Huawei HG8145X6 y XC220 stale en `.252`; IGD/XC220 con CR de internet fuera de este retag.
