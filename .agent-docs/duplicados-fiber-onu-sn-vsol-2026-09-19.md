# Duplicados `fiber_onu_sn` VSOL lote VLAN 1000 (2026-09-19)

Criterio: un SN, un dueño. El keeper es el nombre de `prod_oltgateway.olt_mgr_onu` (o la ACTIVE si la otra está CANCELLED). No se tocó OLT, colas ni IP.

SQL: `scripts/sql/release-duplicate-fiber-onu-sn-vsol-2026-09-19.sql`

| SN | Keeper | Liberado | Motivo |
|----|--------|----------|--------|
| `VSOL0086F109` | `#1982` Kety | `#1783` CANCELLED | OLT = Kety |
| `HWTC15F5C4F6` | `#2011` Melquiades | `#1852` CANCELLED | OLT = Melquiades |
| `HWTC15F5D5F6` | `#2219` Alejandrina | `#1883` CANCELLED | OLT = Alejandrina; ping keeper UP |
| `HWTC15F5F986` | `#1912` Basilio ACTIVE | `#1977` CANCELLED | Cancelada no retiene SN. OLT sigue nombrada Yoseph |
| `VSOL0086D819` | `#2070` Martha | `#1808` ACTIVE Herlinda | OLT = Martha. Ambas IP hacían ping |
| `HWTC15F61FA6` | `#2137` Deysi | `#1870` ACTIVE Alisson | OLT = Deysi. Ambas IP hacían ping |
| `HWTC15F62436` | `#650` Juan Bartolo | `#1847` ACTIVE Nahin | OLT = Juan |

Conflictos `service_identity_conflict` 3, 8, 9, 10, 11, 23, 26 → `RESOLVED`.

`#1808`, `#1870` y `#1847` siguen ACTIVE con internet y **sin** SN: hay que asignarles la ONU real cuando se identifique.
