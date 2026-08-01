# Construcción — Acceso VPS → OLT por enrutamiento (GRE)

**Fecha:** 2026-07-20  
**Objetivo:** Que el VPS ispAdmin abra SSH a la OLT en `10.11.104.2:22` vía **GRE al Mikrotik de borde** (hoy **MK2**), sin DST-NAT de puerto público.

## Por qué no basta una ruta IP

`10.11.104.0/24` es privada. Un `ip route add 10.11.104.0/24 via 38.224.231.x` en el VPS **no funciona** por Internet: el ISP del VPS no enruta RFC1918 hacia el CCR.

## Solución: túnel GRE + route (MK2 — prod 2026-08-01)

```text
VPS 212.85.13.47
  gre-mk1 10.255.255.2/30
       │ GRE (proto 47)
       ▼
MK2 38.224.231.4
  gre-ispadmin-vps 10.255.255.1/30
       │ route + SNAT → 10.11.104.89 (ether3)
       ▼
OLT 10.11.104.2:22
```

| Componente | Valor |
|------------|--------|
| GRE VPS | `gre-mk1`, peer `38.224.231.4`, `10.255.255.2/30` |
| GRE MK2 | `gre-ispadmin-vps`, `10.255.255.1/30` |
| Ruta VPS | `10.11.104.0/24 via 10.255.255.1 dev gre-mk1` |
| SNAT MK2 | GRE `10.255.255.0/30` → masquerade → `10.11.104.0/24` |
| Origen visto en OLT | `10.11.104.89` |

Legacy MK1: mismo esquema con `38.224.231.2` y SNAT hacia `10.11.104.88`. Volver: `MK_PUBLIC_IP=38.224.231.2` + `mikrotik-mk1-olt-vps-gre.rsc`.

## Scripts

| Archivo | Rol |
|---------|-----|
| `scripts/mikrotik-mk2-olt-vps-gre.rsc` | GRE + SNAT en **MK2** (prod) |
| `scripts/mikrotik-mk1-olt-vps-gre.rsc` | GRE + SNAT en MK1 (legacy) |
| `scripts/apply-mk2-olt-vps-gre-from-vps.sh` | En VPS: import RSC en MK2 + `setup-vps-olt-gre.sh` |
| `scripts/setup-vps-olt-gre.sh` | Crea GRE y ruta en VPS (`MK_PUBLIC_IP`, default MK2) |
| `scripts/vps-olt-gre.service` | systemd oneshot al boot |
| `scripts/backup_olt_to_gdrive.sh` | Usa `OLT_SSH_HOST=10.11.104.2` |

## Validación

| Fecha | Resultado |
|-------|-----------|
| 2026-07-20 (MK1) | ping ~82 ms; SSH OLT; origen `.88` |
| 2026-08-01 (MK2) | GRE peer `38.224.231.4`; ping ~79 ms; `tomcat9027` → `10.11.104.2:22` OK |

## Rollback

En MK2:

```routeros
/interface gre remove [find name=gre-ispadmin-vps]
/ip firewall nat remove [find comment="IspAdmin VPS GRE -> OLT LAN SNAT"]
```

En MK1 (si se reactiva GRE legacy):

```routeros
/interface gre remove [find name=gre-ispadmin-vps]
/ip firewall nat remove [find comment="IspAdmin VPS GRE -> OLT LAN SNAT"]
```

En VPS:

```bash
systemctl disable --now vps-olt-gre.service
ip tunnel del gre-mk1
```
