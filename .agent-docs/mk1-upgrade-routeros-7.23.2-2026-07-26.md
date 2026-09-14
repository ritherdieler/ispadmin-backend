# Actualización MK1 — RouterOS 7.23.2 stable

**Fecha:** 2026-07-26  
**Equipo:** MK1 `GIGAFIBER 1036` — `38.224.231.2` (CCR1036-8G-2S+)  
**Backup previo:** `.agent-docs/backups/mk1-pre-upgrade-20260726-0939.rsc`

## Resumen

| Etapa | Versión | Acción |
|-------|---------|--------|
| Inicial | 6.48.6 (long-term) | — |
| Fase 1 | 7.12.1 (stable) | Canal `upgrade` → download → reboot |
| Fase 2 | 7.23.2 (stable) | Canal `stable` → download → reboot (vía API desde VPS) |
| Fase 3 | Firmware RB 7.23.2 | `/system routerboard upgrade` → reboot |

**Estado final:** RouterOS **7.23.2 (stable)** — alineado con MK2.

Desde esta fecha **MK1 y MK2 comparten RouterOS 7.23.2 (stable)**. Características v7 y novedades 7.23.x: [routeros-7-gigafiber-cores.md](./routeros-7-gigafiber-cores.md).

## Comandos usados

```routeros
/system package update set channel=upgrade
/system package update check-for-updates
/system package update download
/system reboot

/system package update set channel=stable
/system package update check-for-updates
/system package update download
/system reboot

/system routerboard upgrade
/system reboot
```

## Notas operativas

- Tras el salto 6.x → 7.12.1, **SSH (puerto 22) dejó de responder** temporalmente; Winbox/API (8291/8728) sí respondieron desde el VPS.
- La fase 7.12.1 → 7.23.2 se completó por **API RouterOS (8728)** desde `212.85.13.47`.
- Tras la actualización completa, **SSH volvió a funcionar**.
- Canal de actualización quedó en **`stable`** con modo **HTTPS**.

## Validación post-upgrade

| Campo | Valor |
|-------|-------|
| Identity | `GIGAFIBER 1036` |
| RouterOS | **7.23.2 (stable)** |
| Build | **2026-07-03 09:08:08** |
| Paquete `routeros` | **7.23.2** |
| Canal update | **stable** (HTTPS) |
| Routerboard firmware | **7.23.2** |
| Servicios | SSH, API y Winbox accesibles |
| Interfaces y rutas | revisar en ventana operativa si hubo interrupción de abonados legacy VLAN 1 |

## Riesgos / seguimiento

- Validar integraciones ispAdmin contra MK1 en ROS 7: queues, address-list `deudores`, filter rules, GRE `gre-ispadmin-vps`.
- MK1 sigue marcado `disabled=true` en BD; no afecta altas nuevas pero sí cortes/colas de abonados legacy con `host_device_id=1`.
- Conservar backup `.rsc` hasta confirmar estabilidad 24–48 h.
