# Inventario MikroTik — routers GigaFiber

> Hub infraestructura: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)

Documentación de routers MikroTik usados o planificados para uplink / core. Las credenciales de producción viven en la tabla `network_device` (no en este archivo).

## RouterOS 7 — versiones por core

| Router | IP | RouterOS | Build | Canal | RB firmware | Verificado |
|--------|-----|----------|-------|-------|-------------|------------|
| **MK1** | `38.224.231.2` | **7.23.2 (stable)** | 2026-07-03 09:08:08 | stable | **7.23.2** | Post-upgrade 2026-07-26 |
| **MK2** | `38.224.231.4` | **7.23.2 (stable)** | 2026-07-03 09:08:08 | stable | **7.19.6** (pendiente **7.23.2**) | API 2026-07-26 |

Detalle completo: **[routeros-7-gigafiber-cores.md](./routeros-7-gigafiber-cores.md)**

## Routers registrados

| ID DB | Nombre | IP | VLAN (`vlan_id`) | `disabled` | Tipo | Rol |
|-------|--------|-----|------------------|------------|------|-----|
| 1 | Mikrotik CCR | 38.224.231.2 | **1** (legacy) | **`true`** | CLOUD_CORE_ROUTER | Core legacy VLAN 1 — **no elegible** para altas |
| **8** | Mikrotik CCR 2 | **38.224.231.4** | **100** (piloto) | **`false`** | CLOUD_CORE_ROUTER | **Único core habilitado** para altas / selector Android |

- Flag `disabled` en `network_device` (default `false`). Solo cores con `disabled=false` salen en `GET .../coreTypes` y `GET .../connection/cloud-core-routers`.
- **Política altas (2026-07-20):** solo **Mikrotik CCR 2** (`id=8`) queda con `disabled=false`. El resto de `CLOUD_CORE_ROUTER` (p. ej. MK1 `id=1`, y en local `mikrotik_test` `id=7`) van con `disabled=true`.
- Seed VLAN: `scripts/sql/network-device-vlan-seed.sql`
- Seed `disabled` (solo CCR 2 activo): `scripts/sql/network-device-disabled-seed.sql`
- Migración `enabled` → `disabled`: `scripts/sql/20260720_network_device_enabled_to_disabled.sql`
- Seed IP pool MK2 VLAN 100: `scripts/sql/ip-pool-mk2-vlan100-seed.sql` → `ip_pool.id=8`, `192.168.30.1/24`, `host_device_id=8`, `is_eligible=1`
- Selector en registro Android: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md#app-android--selector-de-core) · [selector-cloud-core-router-android-build.md](./selector-cloud-core-router-android-build.md)

## Mikrotik CCR — 38.224.231.2 (existente)

| Campo | Valor |
|-------|-------|
| Identity RouterOS | `GIGAFIBER 1036` |
| RouterOS | **7.23.2 (stable)** — build **2026-07-03 09:08:08**, canal **stable** |
| Paquete `routeros` | **7.23.2** |
| Routerboard firmware | **7.23.2** |
| Versión anterior | 6.48.6 (long-term) — actualizado 2026-07-26 ([detalle](./mk1-upgrade-routeros-7.23.2-2026-07-26.md)) |
| API RouterOS | Puerto **8728** — habilitado |
| Winbox | Puerto **8291** |
| Uso backend | `Subscription.hostDevice`, queues, address-list deudores, filter rules |
| GRE OLT (VPS) | `gre-ispadmin-vps` `10.255.255.1/30` ↔ VPS `10.255.255.2`; SNAT a LAN `10.11.104.0/24` |
| DST-NAT OLT (CloudOLT) | Lista `CloudOLT`; puertos `2322`/`2333`/`2161` → OLT 22/23/161 |
| Script GRE VPS | `scripts/mikrotik-mk1-olt-vps-gre.rsc` — [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md) |

## Mikrotik CCR — 38.224.231.4 (nuevo)

| Campo | Valor |
|-------|-------|
| IP | 38.224.231.4 |
| Identity RouterOS | **`CCR2116 NUEVO`** |
| RouterOS | **7.23.2 (stable)** — build **2026-07-03 09:08:08**, canal **stable** |
| Paquete `routeros` | **7.23.2** |
| Routerboard firmware | **7.19.6** (pendiente upgrade a **7.23.2**) |
| Board | **CCR2116-12G-4S+** |
| Usuario | `gigafiber2023` (mismo que 38.224.231.2) |
| Contraseña | Misma que `network_device.id = 1` |
| API RouterOS | Puerto **8728** — habilitado 2026-07-19 |
| SSH | Puerto **22** — habilitado |
| Contexto | Segundo router para uplink OLT **0/3/2** → `sfp-sfpplus2`. Gateway piloto **`192.168.30.1/24`**. Ver [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md). |
| Subredes VLAN100 | **`192.168.30.0/24`** + **`192.168.31.0/24`** — ONU piloto `192.168.30.202` (ZTE F6600R `ZTEG-DC47C169`). Pool extra: [mk2-pool-31-prod-2026-09-05.md](./mk2-pool-31-prod-2026-09-05.md) |
| IP pools backend | **`192.168.31.1/24`** (id 163, `is_eligible=1`); **`192.168.30.1/24`** (id 8, `is_eligible=0`, gateway MK2 se mantiene) |
| Uplink OLT | **0/3/2** → `sfp-sfpplus2` (VLAN 100 native untagged desde OLT) |

### Prueba de conexión — 2026-07-19 (Fase 1 completada)

| Prueba | 38.224.231.2 | 38.224.231.4 |
|--------|--------------|--------------|
| Ping ICMP | OK | OK |
| Winbox TCP/8291 | OK | OK |
| SSH TCP/22 | — | OK (habilitado por operador) |
| API RouterOS TCP/8728 | OK | **OK** (habilitado vía SSH) |
| Login API (`/system/identity/print`) | OK — `GIGAFIBER 1036`, ROS **7.23.2 (stable)**, RB **7.23.2** | OK — **`CCR2116 NUEVO`**, ROS **7.23.2 (stable)**, RB **7.19.6** |
| VPS prod → API 8728 | OK | OK |
| `network_device` prod DB | id=1 | id=8 |

**Fase 1.1:** API operativa. Verificación: `python3 scripts/mikrotik-mk2-phase1-verify.py` → `status: OK`.

**Nota:** Ambos cores comparten RouterOS **7.23.2 (stable)**, build **2026-07-03**. MK2 aún tiene firmware de placa **7.19.6**; aplicar `/system routerboard upgrade` para alinearlo a **7.23.2** (como MK1).

### Historial — habilitación API (2026-07-19)

SSH habilitado por operador; API habilitada remotamente:

```
/ip service enable api
/ip service set api disabled=no port=8728
/ip service enable api-ssl
```

## Integración backend

| Componente | Archivo |
|------------|---------|
| Conexión API | `MikroTikConnectionService.kt` |
| Datos conexión por dispositivo | `NetworkDevice` + `NetworkDeviceConnectionManager` |
| Cores activos | `findActiveCloudCoreRouters()` → `GET .../coreTypes`, `GET .../connection/cloud-core-routers` |
| Endpoints diagnóstico | `GET /networkDevice/connection/{deviceId}/system-info` |
| Mock dev | `mikrotik.connection.mock.enabled` en `application-dev.properties` |

## Switch CRS310 — RB PUERTOS SFP+ (`38.224.231.8`)

Ver [crs310-rb-puertos-sfp.md](./crs310-rb-puertos-sfp.md). Uplink WAN (Sirion / CCR1036 / CCR2116), **no** el switch de MK2 `ether5`. Gestión VLAN **450**; acceso vía mac-telnet desde MK2.

## Comando de prueba manual (API)

Desde el backend (dependencia `me.legrange:mikrotik`):

```java
ApiConnection.connect("38.224.231.4");
connection.login("gigafiber2023", "<password network_device id=1>");
connection.execute("/system/identity/print");
```
