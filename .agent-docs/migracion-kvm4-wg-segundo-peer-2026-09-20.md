# Dual-run WireGuard KVM4 (2026-09-20)

Segundo peer MK2 hacia el VPS nuevo `2.24.66.53` **sin** cortar el túnel de producción.

## Por qué no `10.255.255.2` en ambos

Dos peers WireGuard con el mismo `allowed-address=10.255.255.2/32` (o la misma IP de interfaz en dos VPS) pelean el handshake. Dual-run usa un `/30` aparte.

| VPS | Interfaz | Address | MK2 peer comment | Endpoint |
|---|---|---|---|---|
| Prod `212.85.13.47` | `wg-olt` | `10.255.255.2/30` | `ispAdmin VPS` | `212.85.13.47:51820` |
| KVM4 `2.24.66.53` | `wg-olt` | `10.255.254.2/30` | `ispAdmin VPS KVM4` | `2.24.66.53:51820` |

MK2 `wg-ispadmin-vps` listen `51830` tiene **las dos** direcciones (`10.255.255.1/30` y `10.255.254.1/30`).

## Qué no hacer

No ejecutar `scripts/apply-mk2-olt-vps-wg-from-vps.sh` ni importar `mikrotik-mk2-olt-vps-wg.rsc` en dual-run: **borra todos los peers** y regenera claves MK2.

Plantilla ADD-only: `scripts/mikrotik-mk2-olt-vps-wg-kvm4-add-peer.rsc`.

## Claves KVM4

Nuevas en `/opt/gigafiber/secrets/wg-olt-kvm4.keys`. Pubkey VPS: `NMOqsYCnViGwOa1nlyHFHlqefckqV9urDbdRbWNI3SM=`. Las de prod (`wg-olt.keys` / peer `w52E4lMwg2LjvNORyyABnM8oDk6Ja8bzQhM8NpqGTBg=`) no se tocaron.

## Smoke 2026-09-20

- KVM4: ping `10.255.254.1` y `10.11.104.2` 0% loss.
- Prod: ping `10.255.255.1` y `10.11.104.2` 0% loss, handshake vigente.
- `wg-quick@wg-olt` enabled/active en KVM4.

Cutover `.cloud` sigue bloqueado hasta OK explícito.
