# VPS ↔ OLT vía MK2 GRE — 2026-08-01

## Hecho

- Import `scripts/mikrotik-mk2-olt-vps-gre.rsc` en MK2 (`38.224.231.4`).
- VPS `gre-mk1` repoint a peer MK2 (`setup-vps-olt-gre.sh`, default `MK_PUBLIC_IP=38.224.231.4`).
- Validación: ping `10.11.104.2` ~79 ms; TCP 22 desde `tomcat9027`.

## Reaplicar

En VPS como root:

```bash
/opt/gigafiber/scripts/apply-mk2-olt-vps-gre-from-vps.sh
```

Requiere `MYSQL_ROOT_PASSWORD` o `SPRING_DATASOURCE_PASSWORD` (compose) y acceso SSH `:22` a MK2.

## systemd

Copiar/actualizar `scripts/vps-olt-gre.service` → `/etc/systemd/system/vps-olt-gre.service` con `Environment=MK_PUBLIC_IP=38.224.231.4`.

## Relacionado

- [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md)
- [smartolt-cloudolt-mk2.md](./smartolt-cloudolt-mk2.md)
