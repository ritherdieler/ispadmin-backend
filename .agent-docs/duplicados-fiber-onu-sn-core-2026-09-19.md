# Duplicados Core ACTIVE restantes (2026-09-19)

SQL: `scripts/sql/release-duplicate-fiber-onu-sn-core-2026-09-19.sql`

Criterio: un SN ocupado (ACTIVE/CUT_OFF/SUSPENDED). El keeper es el nombre de `prod_oltgateway.olt_mgr_onu`, salvo `TPLG31B241F0` (serial fantasma: SmartOLT/Gateway/GenieACS vacíos; MK2 tenía dos CPE vivos distintos).

No se tocó OLT, colas ni IP.

| SN | Keeper | Liberado | Motivo |
|----|--------|----------|--------|
| `TPLGE6EC8818` | `#1198` Basilio | `#1` Tomasa | OLT = Basilio |
| `TPLG2137FA68` | `#1305` Agrícola M y C | `#1679` Roberto | OLT = Agrícola |
| `TPLG21380C20` | `#2165` Liseth | `#1801` Yanet | OLT = Liseth |
| `VSOL0086BDD9` | `#629` Eugenia | `#1695` Camila (`HWTC0086BDD9`) | OLT = VSOL Eugenia |
| `VSOL00871AC9` | `#1816` Zenón | `#1815` Fundo Pamajosa | OLT = Zenón |
| `HWTC15F5F5A6` | `#1846` Edith | `#1837` Delia | OLT = Edith |
| `TPLG31B241F0` | ninguno | `#1458` Javier y `#1459` Benjamin | serial no existe en inventario |

También se soltó `fiber_onu_sn` de CANCELLED que compartían sufijo con una ocupada (`CANCELLED_LEFT=0`).

Conflictos `service_identity_conflict` 1, 2, 4–7, 12–17, 19, 21, 22, 24, 25, 27–30, 32 → `RESOLVED`. Quedan OPEN 18/20 (solo CANCELLED), 31 (clave obsoleta) y 33–35 (ZTEG lab).
