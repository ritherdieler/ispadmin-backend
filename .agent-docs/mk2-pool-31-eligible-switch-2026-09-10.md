# MK2 prod — switch elegibilidad a `192.168.31.1/24`

Fecha: **2026-09-10**

El pool `192.168.30.1/24` (id 8) sigue en MK2 para los ~166 abonados activos, pero **deja de ser elegible**. Las altas nuevas toman `192.168.31.1/24` (id 163).

El seed de 2026-09-05 no se había aplicado: el segmento no existía en `ip_pool` y el gateway no estaba en RouterOS (el cutover VLAN 100 tagged del mismo día dejó `.30`/`.255`/`.250` en `vlan100-olt` y no había `.31`).

## Estado aplicado

| Pieza | Valor |
|-------|--------|
| Router | MK2 (`network_device.id=8`, `38.224.231.4`) |
| Interfaz | `vlan100-olt` (VLAN 100 tagged; no `sfp-sfpplus2`) |
| Gateway | `192.168.31.1/24` — ping local 0% loss; ping `8.8.8.8` src `.31.1` 0% loss |
| NAT | `srcnat masquerade src-address=192.168.31.0/24` (`NAT prod VLAN100 31`) más masquerade WAN genérico |
| address-list | `prod-vlan100-31` = `192.168.31.0/24` |
| `ip_pool` id 163 | `192.168.31.1/24`, `host_device_id=8`, `is_eligible=1` |
| `ip_pool` id 8 | `192.168.30.1/24`, `is_eligible=0` (gateway MK2 **no** se tocó) |

## Scripts

- RouterOS: [`scripts/mikrotik-mk2-pool-31.rsc`](../scripts/mikrotik-mk2-pool-31.rsc) (interfaz `vlan100-olt`)
- MySQL: [`scripts/sql/ip-pool-mk2-vlan100-31-eligible-switch.sql`](../scripts/sql/ip-pool-mk2-vlan100-31-eligible-switch.sql)

## Core WAR (pendiente)

Prod corre `APP_RELEASE=1.0.3+83de8fe` (2026-09-03). Ese WAR solo acepta prefijo `192.168.30.` en `SubscriptionVlanRules.assertPoolAligned`. El prefijo `192.168.31.` está en código desde `69f6674` y **no está desplegado**.

Hasta un deploy Core que incluya `PROD_VLAN100_POOL_PREFIX_31`, un alta FIBER VLAN 100 con IP `.31` falla el assert. El gateway MK2 sí enruta.

## Relacionado

- [mk2-pool-31-prod-2026-09-05.md](./mk2-pool-31-prod-2026-09-05.md)
- [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md)
