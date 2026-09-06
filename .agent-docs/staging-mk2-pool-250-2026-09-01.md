# MK2: gateway staging e2e `192.168.250.0/24`

Fecha: 2026-09-01

## Por qué

El e2e FIBER staging asigna IPs del pool MySQL `192.168.250.1/24` y TR-069 deja la WAN cliente en `.250.x` / GW `.250.1`. MK2 no tenía esa dirección: `/rest/ping` a la ONU daba 100% loss aunque `tr069ProvisionStatus=COMPLETE`.

## Aplicado en MK2 (`sfp-sfpplus2`, VLAN 100)

Backup RouterOS: `staging-e2e-250-pre`.

| Recurso | Valor |
|---------|--------|
| Gateway | `192.168.250.1/24` comment `Gateway staging e2e VLAN100` |
| Ruta | `192.168.250.0/24` connected `sfp-sfpplus2` |
| NAT | masquerade `src-address=192.168.250.0/24` (`NAT staging e2e 250`) |
| Aislamiento | drop `staging-e2e-250` → `192.168.22.0/24` |

Sin DHCP (IP estática vía TR-069). Coexiste con `192.168.30.1/24` y `192.168.255.1/24`.

Script idempotente: `scripts/genieacs/mk2-staging-pool-250.rsc`.

## Verificación (2026-09-01)

| Check | Resultado |
|-------|-----------|
| Ping MK2 → `192.168.250.1` | 0% loss (~0.2 ms) |
| Ping MK2 → `192.168.30.1` | 0% loss (sin regresión) |
| Ping MK2 → CPE `192.168.250.21` (e2e 2348) | **OK** 4/4, 0% loss, ~4 ms |

## Fuera de alcance (histórico)

Hasta 2026-09-01 `wg-olt` en el VPS **no** llevaba `192.168.250.0/24`. El ping e2e (`/rest/ping` desde MK2) no lo necesitaba.

**Actualizado 2026-09-05:** el CIDR staging ya está en `AllowedIPs` + rutas `wg-olt` (PostUp). Ver [staging-mk2-pool-250-wg-olt-2026-09-05.md](./staging-mk2-pool-250-wg-olt-2026-09-05.md). CR/GenieACS desde el VPS a `.250.x` ya tiene ruta L3 al gateway MK2.

## Relacionado

- `staging-e2e-fixtures.md`
- `staging-vlan100-pool-2026-09-01.md`
- `mk2-red-aprovisionamiento-255.md`
