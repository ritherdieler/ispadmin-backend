# Staging pool `192.168.250.0/24` — Internet MK2 + reachability VPS

Fecha: 2026-09-05

## Objetivo

Dejar el pool e2e staging usable para:

1. Salida a Internet desde la WAN cliente (NAT en MK2).
2. Llegar desde el VPS (`wg-olt`) a `.250.x` (CR GenieACS / diagnóstico).

## MK2 (`sfp-sfpplus2`, VLAN 100)

Ya aplicado (script `scripts/genieacs/mk2-staging-pool-250.rsc`):

| Recurso | Estado 2026-09-05 |
|---------|-------------------|
| `192.168.250.1/24` | presente, comment `Gateway staging e2e VLAN100` |
| NAT `masquerade` `src-address=192.168.250.0/24` | comment `NAT staging e2e 250` |
| Drop a `192.168.22.0/24` | list `staging-e2e-250` |
| Ping `src-address=192.168.250.1` → `8.8.8.8` / `1.1.1.1` | **0% loss** |

Forward VPS→abonados: regla `IspAdmin VPS WG forward` + CR GenieACS `:7547` en `wg-ispadmin-vps`.

## VPS `wg-olt` (nuevo)

Antes: `ip route get 192.168.250.16` salía por `eth0` (sin ruta).

Aplicado:

| Cambio | Dónde |
|--------|--------|
| `AllowedIPs` + `192.168.250.0/24` | `/etc/wireguard/wg-olt.conf` (+ backup `*.bak-staging250-*`) |
| `ip route replace 192.168.250.0/24 dev wg-olt` | runtime |
| PostUp/PostDown | `/opt/gigafiber/genieacs/wg-olt-customer-routes.sh` (incluye `.250`) |

Repo (persistencia en git):

- `scripts/genieacs/wg-olt-customer-routes.sh`
- `scripts/genieacs/apply-genieacs-wg-customer-routes.sh`

## Verificación

| Check | Resultado |
|-------|-----------|
| VPS → `192.168.250.1` (GW MK2) | **0% loss** (~81 ms vía `wg-olt`) |
| VPS → `192.168.250.16` (CPE sub 2382) | host unreachable (WAN TR-069 aún no aplicada en el HGU) |
| MK2 → Internet desde `.250.1` | **0% loss** |

## Relacionado

- [staging-mk2-pool-250-2026-09-01.md](./staging-mk2-pool-250-2026-09-01.md)
- [staging-fiber-e2e-runbook.md](./staging-fiber-e2e-runbook.md)
- Catálogo: [mikrotik-mk2-comandos-catalogo.md](./mikrotik-mk2-comandos-catalogo.md)
