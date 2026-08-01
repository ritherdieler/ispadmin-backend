# VPS ↔ OLT vía MK2 WireGuard — 2026-08-01

## Cambio

Prod migró de **GRE** (`gre-ispadmin-vps` / `gre-mk1`) a **WireGuard** (`wg-ispadmin-vps` / `wg-olt`).

| Lado | Interfaz | UDP | Subred túnel |
|------|----------|-----|--------------|
| VPS | `wg-olt` | listen `51820` | `10.255.255.2/30` |
| MK2 | `wg-ispadmin-vps` | listen `51830` | `10.255.255.1/30` |

Claves persistentes: `/opt/gigafiber/secrets/wg-olt.keys` (chmod 600). Config VPS: `/etc/wireguard/wg-olt.conf`.

## Aplicar / reparar (en VPS como root)

```bash
/opt/gigafiber/scripts/apply-mk2-olt-vps-wg-from-vps.sh
cp /opt/gigafiber/scripts/vps-olt-wg.service /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now vps-olt-wg.service
systemctl disable vps-olt-gre.service
```

## Validación 2026-08-01

- Handshake WG VPS ↔ `38.224.231.4:51830`
- Ping `10.11.104.2` ~81 ms desde host VPS
- `tomcat9027` TCP `10.11.104.2:22` OK
- Unit: `vps-olt-wg.service`

## Rollback a GRE

```bash
systemctl disable --now vps-olt-wg.service
wg-quick down wg-olt
/opt/gigafiber/scripts/apply-mk2-olt-vps-gre-from-vps.sh
systemctl enable vps-olt-gre.service
```

En MK2 el import WG elimina `gre-ispadmin-vps`; el import GRE vuelve a crear GRE.

## NetDiag

Actualizar `criticalInterfaces` del target MK2: sustituir `gre-ispadmin-vps` por `wg-ispadmin-vps` (incidentes `GRE_TUNNEL_DOWN` vs interfaz WG).
