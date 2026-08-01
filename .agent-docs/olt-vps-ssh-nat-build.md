# Construcción — Acceso VPS → OLT por enrutamiento (WireGuard)

**Fecha:** 2026-07-20 (GRE); **prod WireGuard:** 2026-08-01  
**Objetivo:** Que el VPS ispAdmin abra SSH a la OLT en `10.11.104.2:22` vía **túnel cifrado al Mikrotik de borde** (hoy **MK2**), sin DST-NAT de puerto público.

## Por qué no basta una ruta IP

`10.11.104.0/24` es privada. Un `ip route add 10.11.104.0/24 via 38.224.231.x` en el VPS **no funciona** por Internet: el ISP del VPS no enruta RFC1918 hacia el CCR.

## Solución actual: WireGuard + route (MK2 — prod 2026-08-01)

```text
VPS 212.85.13.47
  wg-olt 10.255.255.2/30  UDP 51820
       │ WireGuard
       ▼
MK2 38.224.231.4
  wg-ispadmin-vps 10.255.255.1/30  UDP 51830
       │ route + SNAT → 10.11.104.89 (ether3)
       ▼
OLT 10.11.104.2:22
```

| Componente | Valor |
|------------|--------|
| WG VPS | `wg-olt`, peer `38.224.231.4:51830`, `10.255.255.2/30` |
| WG MK2 | `wg-ispadmin-vps`, `10.255.255.1/30` |
| Ruta VPS | `10.11.104.0/24 dev wg-olt` (vía AllowedIPs) |
| SNAT MK2 | túnel `10.255.255.0/30` → masquerade → `10.11.104.0/24` |
| Origen visto en OLT | `10.11.104.89` |

Detalle de despliegue: [olt-vps-wg-mk2-build-2026-08-01.md](./olt-vps-wg-mk2-build-2026-08-01.md).

## Legacy GRE (rollback)

```text
VPS gre-mk1 10.255.255.2/30 ── GRE ── MK2 gre-ispadmin-vps 10.255.255.1/30
```

Legacy MK1: `38.224.231.2` y SNAT hacia `10.11.104.88`. Scripts GRE: `mikrotik-mk1-olt-vps-gre.rsc`, `setup-vps-olt-gre.sh`, `vps-olt-gre.service`.

## Scripts (prod)

| Archivo | Rol |
|---------|-----|
| `scripts/mikrotik-mk2-olt-vps-wg.rsc` | Plantilla WG + SNAT en **MK2** |
| `scripts/apply-mk2-olt-vps-wg-from-vps.sh` | Claves, import RSC en MK2, `setup-vps-olt-wg.sh` |
| `scripts/setup-vps-olt-wg.sh` | `wg-quick up wg-olt`, baja GRE legacy |
| `scripts/vps-olt-wg.service` | systemd oneshot al boot |
| `scripts/mikrotik-mk2-olt-vps-gre.rsc` | GRE rollback MK2 |
| `scripts/backup_olt_to_gdrive.sh` | Usa `OLT_SSH_HOST=10.11.104.2` |

## Validación

| Fecha | Resultado |
|-------|-----------|
| 2026-07-20 (MK1 GRE) | ping ~82 ms; SSH OLT; origen `.88` |
| 2026-08-01 (MK2 GRE) | peer `38.224.231.4`; ping ~79 ms; Tomcat → OLT OK |
| 2026-08-01 (MK2 WG) | UDP 51820/51830; ping ~81 ms; `tomcat9027` → OLT OK |

## Rollback GRE

En VPS:

```bash
systemctl disable --now vps-olt-wg.service
wg-quick down wg-olt
/opt/gigafiber/scripts/apply-mk2-olt-vps-gre-from-vps.sh
systemctl enable vps-olt-gre.service
```

En MK2 (solo si aplica manual):

```routeros
/interface wireguard remove [find name=wg-ispadmin-vps]
/ip firewall nat remove [find comment="IspAdmin VPS WG -> OLT LAN SNAT"]
```
