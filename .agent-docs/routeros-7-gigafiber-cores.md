# RouterOS 7 — Cores GigaFiber (MK1 y MK2)

> **Última actualización:** 2026-07-26  
> Hub: [mikrotik-routers-inventario.md](./mikrotik-routers-inventario.md) · [arquitectura-red-dual-mikrotik.md](./arquitectura-red-dual-mikrotik.md)

## Versiones instaladas (por router)

Verificación: MK2 consultado por API el **2026-07-26**; MK1 según validación post-upgrade el **2026-07-26** ([mk1-upgrade](./mk1-upgrade-routeros-7.23.2-2026-07-26.md)).

### MK1 — `38.224.231.2` (`GIGAFIBER 1036`)

| Campo | Valor |
|-------|-------|
| **RouterOS** | **7.23.2 (stable)** |
| **Paquete `routeros`** | **7.23.2** |
| **Build** | **2026-07-03 09:08:08** (misma build que release 7.23.2 stable) |
| **Canal de update** | **stable** (HTTPS) |
| **Versión anterior** | 6.48.6 (long-term) → 7.12.1 → 7.23.2 |
| **Routerboard firmware** | **7.23.2** (aplicado en fase 3 del upgrade) |
| **Hardware** | CCR1036-8G-2S+ |
| **Arquitectura** | tile (RouterOS resource) |
| **Factory software** | 6.48.6 |

### MK2 — `38.224.231.4` (`CCR2116 NUEVO`)

| Campo | Valor |
|-------|-------|
| **RouterOS** | **7.23.2 (stable)** |
| **Paquete `routeros`** | **7.23.2** |
| **Build** | **2026-07-03 09:08:08** |
| **Canal de update** | **stable** |
| **Última versión disponible** | 7.23.2 (al día) |
| **Routerboard firmware actual** | **7.19.6** |
| **Routerboard firmware pendiente** | **7.23.2** (`upgrade-firmware` — aplicar `/system routerboard upgrade`) |
| **Hardware** | CCR2116-12G-4S+ |
| **Arquitectura** | arm64 |
| **Factory software** | 7.8 |
| **Serial** | HKH0AYYKQW1 |

> **Nota:** MK1 y MK2 comparten el **mismo paquete RouterOS 7.23.2** (build 2026-07-03). MK2 aún tiene **firmware de placa 7.19.6**; conviene alinearlo a **7.23.2** como en MK1.

---

## Estado actual (resumen)

Ambos Cloud Core Router ejecutan **RouterOS 7.23.2 (stable)**, canal **stable**, build **2026-07-03**.

| Router | IP | Identity | RouterOS | RB firmware |
|--------|-----|----------|----------|-------------|
| **MK1** | `38.224.231.2` | `GIGAFIBER 1036` | **7.23.2 (stable)** | **7.23.2** |
| **MK2** | `38.224.231.4` | `CCR2116 NUEVO` | **7.23.2 (stable)** | **7.19.6** → pendiente **7.23.2** |

**Implicación operativa:** MK1 y MK2 comparten la misma major de RouterOS. Las integraciones ispAdmin (API, queues, address-list, GRE) deben validarse en **ambos** cores con la misma base de comandos v7.

---

## Qué cambió al pasar MK1 de 6.x a 7.x

| Área | RouterOS 6.48.6 (MK1 antes) | RouterOS 7.23.2 (MK1 y MK2 ahora) |
|------|-----------------------------|-----------------------------------|
| Kernel / routing | Stack clásico v6 | Nuevo kernel Linux; BGP/OSPF/rutas estáticas reescritos |
| API | Solo API clásica (8728) | API clásica + **REST API** (`/rest/...`) |
| Netwatch | Solo ping (`simple`) | ICMP avanzado, TCP, **HTTP/HTTPS GET**, **DNS**, scripts up/down |
| Túneles VPN | IPsec, PPTP, L2TP clásico | + **WireGuard**, VXLAN, L2TPv3 nativos |
| Colas | PCQ, SFQ, etc. | + **CAKE**, **FQ_Codel** |
| IPv6 | Básico | + **IPv6 NAT** |
| Certificados | Manual / import | **Let's Encrypt** integrado |
| NTP | Implementación v6 | Cliente/servidor NTP nuevo |
| Paquetes | Varios paquetes sueltos | Bundle consolidado (+ contenedores `/app` en v7 reciente) |
| CLI | Solo estilo v6 | Estilo v7 (muchos comandos v6 siguen funcionando) |
| The Dude | Versión acoplada a v6 | The Dude v7 (mejor integración; no recomendado como servidor en core de prod) |

---

## Características RouterOS 7 relevantes para GigaFiber

### Monitoreo y diagnóstico (NetDiag / NOC)

| Función | Uso en GigaFiber |
|---------|------------------|
| **Netwatch 7.4+** | Detectar caída de internet aguas arriba (HTTP/DNS), no solo ping al router |
| **REST API** | Polling alternativo desde VPS/backend sin depender solo de librouteros |
| **SNMPv3** | Monitoreo de switches/APs con authPriv desde el VPS |
| **Traps SNMP** | `interfaces`, `start-trap` (reboot), `temp-exception` — eventos push |
| **`/system health`** | CPU temp, PSU, ventiladores — alertas tempranas en CCR |
| **`sysUpTime`** | Detectar reinicios inesperados |

Referencia Netwatch: [documentación MikroTik](https://help.mikrotik.com/docs/spaces/ROS/pages/8323208/Netwatch).

### Core ISP (routing, PPP, firewall)

| Función | Uso en GigaFiber |
|---------|------------------|
| Stack routing v7 | Mejor convergencia BGP/OSPF; rutas estáticas más estables |
| **VRRP + sync conntrack** | Futuro HA entre cores (no desplegado aún) |
| PPP mejorado | Estabilidad PPPoE inalámbrico; parche **CVE-2026-59108** en 7.23.2 |
| Bridge unificado | VLAN filtering; en MK2 bridge `LAN_MK1` para cutover VLAN 1 |
| Firewall / conntrack | Mejor rendimiento bajo carga |

### Seguridad y operación remota

| Función | Uso en GigaFiber |
|---------|------------------|
| **REST API con TLS** | Gestión segura desde VPS |
| **Let's Encrypt** | Certificados para servicios expuestos (API-SSL, etc.) |
| Upgrade **HTTPS** por defecto | Descargas de paquetes desde servidores MikroTik (desde rama 7.23) |
| Parches de seguridad 7.23.2 | Fix de servicio + hardening IPsec/IKEv2 |

### Integración ispAdmin (impacto directo)

| Componente | Notas post-v7 |
|------------|---------------|
| `MikroTikConnectionService` | Validar queries API en MK1; algunos parámetros cambiaron (ej. `count=only` en rutas) |
| Queues `/queue/simple` | Revalidar altas y cambios de plan en MK1 |
| `AddressListManagerService` (deudores) | Revalidar cortes/reactivaciones legacy VLAN 1 |
| GRE `gre-ispadmin-vps` | Confirmar túnel estable tras upgrade MK1 |
| `GET .../system-info` | Debe reportar ROS 7.23.2 en ambos cores |

---

## Novedades de la rama 7.23.x (versión instalada)

### 7.23.0 (mayo 2026) — funciones nuevas de la serie

- Descarga de upgrades por **HTTPS** por defecto
- Ecosistema **`/app` y contenedores** ampliado (Docker, apps empaquetadas)
- Driver Ethernet Broadcom **200G** (arm64/x86)
- Mejoras de estabilidad en bridge, firewall y routing

### 7.23.2 (3 jul 2026) — mantenimiento y seguridad

| Componente | Cambio |
|------------|--------|
| **Seguridad** | Fix de problema en servicios; MikroTik recomienda upgrade a todos |
| **PPP** | Fix **CVE-2026-59108** + mayor estabilidad |
| **IPsec / IKEv2** | Limpieza de SAs, validación y terminación mejoradas |
| **BGP / OSPF** | Correcciones VPLS, IPv6, parámetros OSPF |
| **Switch / QoS** | `ingress-rate` / `egress-rate` hasta **400G** |
| **Upgrade** | Scheduler no interfiere con el proceso de upgrade |
| **WinBox** | Mejoras BGP, WireGuard, MPLS/LDP |

Changelog oficial: [foro MikroTik 7.23.2 stable](https://forum.mikrotik.com/t/7-23-2-stable-is-released/271470).

---

## Validaciones pendientes (MK1 post-upgrade)

Tras el salto 6.48.6 → 7.23.2 en MK1, confirmar en ventana operativa:

- [ ] Queues y límites de ancho de banda en abonados legacy
- [ ] Lista `deudores` — corte y reactivación
- [ ] GRE VPS ↔ OLT Gateway (`gre-ispadmin-vps`)
- [ ] Reglas firewall y DST-NAT CloudOLT
- [ ] Integraciones backend contra MK1 vía API 8728
- [ ] Estabilidad 24–48 h antes de descartar backup `.rsc` pre-upgrade

Backup pre-upgrade MK1: `.agent-docs/backups/mk1-pre-upgrade-20260726-0939.rsc`

---

## Comandos de verificación rápida

En ambos cores (SSH o Winbox terminal):

```routeros
/system resource print
/system routerboard print
/system package print where name=routeros
/system health print
/ip service print where name~"api|ssh|winbox"
```

Desde backend / script:

```
/system/identity/print
/system/package/print where name=routeros
```

Resultado esperado en MK1 y MK2: versión **7.23.2**, canal **stable**.

---

## Referencias

| Documento | Contenido |
|-----------|-----------|
| [mk1-upgrade-routeros-7.23.2-2026-07-26.md](./mk1-upgrade-routeros-7.23.2-2026-07-26.md) | Procedimiento y validación upgrade MK1 |
| [mikrotik-routers-inventario.md](./mikrotik-routers-inventario.md) | Inventario routers, credenciales, pruebas API |
| [arquitectura-red-dual-mikrotik.md](./arquitectura-red-dual-mikrotik.md) | Topología dual VLAN, cutover MK1→MK2 |
| [Upgrading to v7 — MikroTik Docs](https://help.mikrotik.com/docs/spaces/ROS/pages/115736772/Upgrading+to+v7) | Guía oficial migración v6→v7 |
