# Cutover PPPoE wireless → MK2

**Fecha prep:** 2026-07-21
**Estado:** **cutover ejecutado.** Verificado 2026-09-10: el servidor de MK2 está activo con **51 sesiones**. Ver el estado vigente en [plan-pppoe-migracion-adaptado-2026-09-10.md](./plan-pppoe-migracion-adaptado-2026-09-10.md).

> **Desviaciones respecto de lo planificado (2026-09-10):** el bridge se renombró `LAN_MK1` → `LAN-VLAN1`; el servidor quedó con `default-profile=default` y `max-mtu=1480` **sin regla de MSS clamp**; y el pool `PPOE CLIENTES` (`192.168.26.2-254`) resultó estar **compartido con 88 IP estáticas** de la BD, lo que provoca doble shaping y colas que siguen a la IP en vez de al cliente. Corregirlo es la Fase A del plan PPPoE adaptado.

## Qué hay hoy (MK1)

| Ítem | Valor |
|------|--------|
| Servidor | `PPOE CLIENTES` en bridge `LAN` (**activo**) |
| Sesiones activas | ~53 |
| Secrets | 123 |
| Pool | `PPOE CLIENTES` → `192.168.26.2-254` |
| Pool gamer | `PPOE GAMERS` → `192.168.55.2-254` |
| Gateway | `192.168.26.1/24` y `192.168.55.1/24` en `LAN` |
| Path L2 wireless | `ether7` AirFiber 9 Octubre + `ether5`/`ether6` switches → bridge `LAN` |

Los clientes PPPoE son **wireless** (torres/AirFiber), no ONU GPON. El cutover del uplink OLT VLAN1 a MK2 **no mueve** estas sesiones por sí solo.

## Qué quedó en MK2 (sync 2026-07-21 desde MK1 `38.224.231.2`)

| Ítem | Valor |
|------|--------|
| Pools | `PPOE CLIENTES`, `PPOE GAMERS` |
| Profiles | 8 planes (igual MK1: 50/70/100/150 + CORTE + NEW/NW) |
| Secrets | 123 (reimportados desde MK1) |
| Servidor | `PPOE CLIENTES` en bridge **`LAN_MK1`** — **enabled** |
| Gateways | `192.168.26.1` / `192.168.55.1` en `LAN_MK1` |
| Activas | 0 hasta que el L2 wireless llegue a `LAN_MK1` |

## Path claro (decisión)

1. Config PPPoE ya está en MK2 (`LAN_MK1`). MK1 sigue con su server en `LAN` (no apagar hasta cutover físico).
2. **Para que haya sesiones en MK2** hay que mover el L2 wireless (AirFiber `ether7` MK1 + switches) a un puerto miembro de `LAN_MK1`, y deshabilitar el server en MK1.
3. Sin mover AirFiber, el server MK2 queda listo pero sin PADI.

## Cutover PPPoE (ventana)

Orden sugerido:

1. Conectar AirFiber (y switches si aplica) a puerto(s) de `LAN_MK1` en MK2 (hoy: `ether4`–`ether8`, `sfp-sfpplus3`).
2. En MK1: `/interface pppoe-server server disable [find service-name="PPOE CLIENTES"]`.
3. Validar en MK2: `/ppp active print`, internet en 2–3 clientes wireless.
4. No dejar dos servers activos en el mismo L2.

## Rollback

1. Disable server MK2.
2. Reconectar AirFiber a MK1 `ether7` / bridge `LAN`.
3. Enable server MK1.
4. Clientes re-dial (o esperar keepalive).

## Verificación rápida

```bash
# MK2
/ip pool print where name~"PPOE"
/ppp profile print where name~"PLAN|CORTE"
/ppp secret print count-only
/interface pppoe-server server print
/ppp active print

# MK1 (producción actual)
/ppp active print count-only
/interface pppoe-server server print
```

## Relacionado

- Checklist VLAN1: [checklist-mk2-vlan1-sfp-sfpplus3.md](./checklist-mk2-vlan1-sfp-sfpplus3.md)
- Arquitectura dual: [arquitectura-red-dual-mikrotik.md](./arquitectura-red-dual-mikrotik.md)
