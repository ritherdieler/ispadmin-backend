# Migración hostDevice MK1 → MK2 (BD prod)

**Fecha:** 2026-08-01  
**Motivo:** pagos/reactivaciones seguían yendo a MK1 (`host_device_id=1`) aunque el borde VLAN1 ya opera en MK2 (`LAN_MK1` / `sfp-sfpplus3`).

## Alcance ejecutado

| Tabla | Cambio |
|-------|--------|
| `subscription` | `host_device_id` `1` → `8` (1309 filas) + `NULL` → `8` (1 fila) |
| `ip_pool` | `host_device_id` `1` → `8` (8 pools) |

Estado final:

| Métrica | Valor |
|---------|-------|
| Suscripciones en MK2 (`id=8`) | **1313** |
| Suscripciones en MK1 (`id=1`) | **0** |
| Pools en MK2 | **9** |
| Pools en MK1 | **0** |

Muestra: suscripción `2124` (`192.168.220.221`) → `host_device_id=8`.

## Red (precondición observada)

En MK2 antes del UPDATE:

- `sfp-sfpplus3` **running**
- Gateways legacy en bridge `LAN_MK1` (ej. `192.168.220.1/24`, `192.168.22.1/24`, `192.168.25.1/24`)
- VLAN100 intacta: `192.168.30.1/24` en `sfp-sfpplus2`

## Address-list `deudores`

| Equipo | Antes | Acción |
|--------|-------|--------|
| MK1 `38.224.231.2` | 41 | Origen de sync |
| MK2 `38.224.231.4` | 0 → **41** | Sync OK (`added=41`, `comment=sync-mk1-2026-08-01`) |

Sin esta sync, cortes vigentes en MK1 no aplicarían en MK2 tras el cambio de `hostDevice`.

## Pendiente / riesgos

- Regenerar o verificar **simple queues** en MK2 (si aún viven solo en MK1).
- Confirmar regla firewall que dropea `deudores` en MK2.
- GRE VPS↔OLT sigue documentado vía MK1; validar si MK1 se apaga.
- `network_device.id=1` sigue en BD con `disabled=1` (rollback / referencia).
- MK2 `vlan_id=100` en BD: altas nuevas dual-VLAN siguen necesitando regla de negocio pool/puerto.

## Rollback BD

```sql
UPDATE subscription SET host_device_id = 1 WHERE host_device_id = 8 AND ip NOT LIKE '192.168.30.%';
-- pools: restaurar host_device_id=1 en segmentos legacy si hace falta
```
