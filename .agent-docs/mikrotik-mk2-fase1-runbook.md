# Fase 1 — MikroTik 38.224.231.4 (MK2)

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)

## Estado — 2026-07-19 ✅ Fase 1.1 completada

| Paso | Estado |
|------|--------|
| SSH TCP/22 | OK (habilitado por operador) |
| API TCP/8728 | OK (habilitado vía SSH) |
| Login API | OK — identity `CCR2116`, RouterOS 7.23.2 |
| VPS prod → API | OK |
| DB prod `network_device.id=8` | OK |

Comandos aplicados por SSH:

```
/ip service enable api
/ip service set api disabled=no port=8728
/ip service enable api-ssl
```

## Verificación

```bash
export MIKROTIK_PASSWORD='...'
python3 scripts/mikrotik-mk2-phase1-verify.py
```

Salida esperada MK2: `'status': 'OK'`.

## Backend (Fase 1.2)

Con `mikrotik.connection.mock.enabled=false`:

```http
GET /ispadmin/networkDevice/connection/8/system-info
Authorization: Bearer <token>
```

## Diferencia MK1 vs MK2

| | MK1 (`.2`) | MK2 (`.4`) |
|---|------------|------------|
| Identity | GIGAFIBER 1036 | CCR2116 |
| RouterOS | 6.48.6 (long-term) | 7.23.2 (stable) |
| Board | CCR1036-8G-2S+ | CCR2116-12G-4S+ |

Validar queues, address-list y filter rules en ROS 7 antes de producción masiva.

## Fase 2 — uplink OLT + piloto ✅ (2026-07-19)

| Paso | Estado |
|------|--------|
| Cable **0/3/2** → `sfp-sfpplus2` | OK |
| VLAN 100 OLT uplink | OK — [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md) |
| MK2 gateway `192.168.30.1/24` en `sfp-sfpplus2` | OK — [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md) |
| ONU piloto `ZTEG-DC47C169` | OK — ping MK2 ~2 ms |

Verificación: `./scripts/mikrotik-mk2-pilot-verify.sh`

## Siguiente: Fase 3

Mapeo NAPs → VLAN/hostDevice + backend `FiberInstallationStrategy`. Plan: `.cursor/plans/multi_mikrotik_gigafiber_a973760c.plan.md`.
