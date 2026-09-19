# Fase 1 — VLAN 1000 en la OLT (parque ZTE / VSOL)

2026-09-18. Separado de la migración PPPoE. Esta fase **solo** abre el camino L2 de gestión. No toca la WAN de internet ni GenieACS.

## Contrato

1. OLT: gem mapping VLAN 1000 + `service-port vlan 1000` por ONU.
2. La WAN estática / PPPoE de internet no se modifica.
3. GenieACS (retag de la WAN TR-069 a VLAN 1000) es **fase 2**.

## Inventario OLT (inspect vivo)

`display service-port vlan 1000` → **152** SP (148 up / 4 down). 149 usan **gem 1**; 3 usan gem 2 (profile 12 lab).

ZTE+VSOL en `olt_mgr_onu` (516): **48** ya tienen SP 1000, **468** no.

| Acción | N | Qué hay que hacer |
|---|---|---|
| Ya tiene SP 1000 | 48 | Nada |
| Solo `service-port` | 28 | Profile ya transporta 1000 o SmartOLT por prioridad (`SmartOLT_G` / flexible / profile 12) |
| Mapping + SP | 438 | Añadir VLAN 1000 al lineprofile y después el SP |
| Manual | 2 | `SmartOLT_G_H0F71B8AE`, una VSOL sin profile |

### Commits de profile (OMCI a todos los bindings)

| Profile | ID | Bindings | Comando | Gem del SP |
|---|---|---|---|---|
| `Generic_1_HF291F96D` | 5 | 273 | `gem mapping 1 3 vlan 1000` | 1 |
| `Generic_1_V1` | 3 | 117 | `gem mapping 1 2 vlan 1000` | 1 |
| `line-profile_10` | 10 | 121 | `gem mapping 1 1 vlan 1000` | 1 |
| `Generic_1_V100` | 6 | 60 | `gem mapping 2 1 vlan 1000` | 2 |
| `SmartOLT_G_V100` | 7 | 19 | `gem mapping 1 2 vlan 1000` | 7 |

No se commitea un profile para “cambiar internet”: solo se **añade** el mapeo 1000. El gem de VLAN 100 / 1 no se toca.

No se revincula el parque al profile 12 (rompería perfiles VLAN 1 / SmartOLT).

## Código

- Planificador: `scripts/olt_vlan1000_onu_plan.py`
- Tests: `scripts/olt-vlan1000-onu-plan-test.py` (8/8)

```bash
cd scripts && python3 olt-vlan1000-onu-plan-test.py
```

## Aplicación (no corrida en masa en este turno)

Orden por ONU:

1. Si el profile no mapea 1000: un `commit` por profile (una vez).
2. `service-port vlan 1000 gpon 0/{slot}/{port} ont {ont} gemport {n} multi-service user-vlan 1000 tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9`
3. `display service-port port 0/{slot}/{port} ont {ont}` → fila VLAN 100 **y** 1000.

Piloto: 1 VSOL + 1 ZTE de `Generic_1_V100` (gem 2) y 1 de `HF291F96D` (gem 1), no las 438 de un golpe.
