# Auditoría red GPON — OLT / MK2

> **Vigente 2026-09-10.** Snapshot original **2026-09-09** (solo lectura). Después: cutovers tagged, VLAN 1000 de gestión, rename `LAN_MK1` → `LAN-VLAN1`, corte de deudores.
>
> Canvas: [auditoria-red-olt-mk2.canvas.tsx](/Users/sergiocarrillo/.cursor/projects/Users-sergiocarrillo-gigafiber-ispadmin-backend/canvases/auditoria-red-olt-mk2.canvas.tsx) (abrir junto al chat).
>
> Nombres: [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md) · Destino VLAN 1: [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md) · Tagged 100: [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md) · Gestión: [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md)

Perímetro: OLT MA5608T, MK2, CRS310, WG VPS. MK1 fuera del plano de datos.

## Tesis vigente

La VLAN de abonado la pinta la **OLT** (`service-port` + `user-vlan` + `tag-transform translate`). El **MK2 termina 802.1Q** en subinterfaces; ya no es gateway untagged.

```text
 Internet abonado     user-vlan 100  → SP vlan 100  → 0/3/2 tagged → vlan100-olt
 Gestión CPE          user-vlan 1000 → SP vlan 1000 → 0/3/2 tagged → vlan1000-olt
 Parque legado        user-vlan 1    → SP vlan 1    → 0/3/3 tagged → vlan1-olt → LAN-VLAN1
 Native aparcamiento  101 en 0/3/2, 102 en 0/3/3 (vacías, sin ONUs)
```

La VLAN **1 no se agranda**: va quedando en desuso; el destino es migrar el internet de abonado a la **VLAN 100**. La **1000** es solo TR-069 / staging / web ONU, no sustituye a la 1.

## Qué se implementó (2026-09-10)

| Ítem | Estado |
|------|--------|
| NNI VLAN 100 tagged (`0/3/2` native **101** → `vlan100-olt`) | Hecho. Corte ~1,07 s |
| NNI VLAN 1 tagged (`0/3/3` native **102** → `vlan1-olt` → `LAN-VLAN1`) | Hecho. Corte ~1,17 s |
| Rename `LAN_MK1` → `LAN-VLAN1` | Hecho |
| No unificar bridges ni un solo NNI | Decidido: [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md) |
| VLAN 1000 gestión en el mismo `0/3/2` | Infra hecha; lab VSOL viva |
| Cerrar rollback VLAN 100 (IPs y scripts swap/guard) | Hecho |
| Corte deudores: `drop` **antes** de los accept de subida | Hecho en MK2. Código con `place-before`; falta deploy WAR |
| VLAN 1 = legado, destino VLAN 100 | Documentado |
| Higiene de colas MK2 vs BD y revisión L3 (default, `192.169.22`) | Hecho. Ver abajo |

## Higiene MK2 2026-09-10

Colas alineadas con BD prod (870 medibles, 973 colas). Detalle y runbook: [mk2-queue-reconcile-2026-08-28.md](./mk2-queue-reconcile-2026-08-28.md).

| Ítem | Resultado |
|------|-----------|
| ACTIVE sin cola | 2 creadas + 3 movidas de su IP anterior (`RETARGET`) |
| Colas ≠ plan | 3 corregidas (796 y 1270 de 400M→200M; 2031 de 20M→200M) |
| Nombres desactualizados | 17 PATCH (NAP recién poblado, cambio de titular o de lugar) |
| Colas de CANCELLED | 14 borradas; **3 excluidas por tener tráfico** (`*113`, `*176`, `*85`) |
| Huérfanas | 24, se dejan: clientes sin `id:`, laboratorio, IPs viejas |
| IPs con dos suscripciones | 5 `CONFLICT`: 629/1629, 657/1302, 1287/1612, 1628/1735, 1895/2344 |

**Por qué los cancelados no están en el firewall.** Hay **una sola** address-list, `deudores` (42 entradas hoy), y dos productores que la reescriben entera:

| Productor | Qué hace |
|-----------|----------|
| `ServiceCutManagerService.processCutService` (cron del día de corte) | `clearAddressListAndFirewallRule()` vacía la lista y añade **solo deudores**. La llamada a `processAndLogCancelledSubscriptions` está comentada |
| `AddressListManagerService.generateAddressListForCancelledSubscriptions` (endpoint `POST /subscription/generate-address-list-cancelled-subscriptions`) | También vacía `deudores` primero y añade **solo cancelados** |

Los dos procesos se pisaban: ejecutar el de cancelados borraba a los 42 deudores, y el siguiente corte mensual borraba a los cancelados.

**Corregido 2026-09-10: dos listas independientes.**

| Lista | Comment de la regla drop | Quién la llena |
|-------|--------------------------|----------------|
| `deudores` | `CORTADO POR DEUDA - LISTA DE DEUDORES` | `ServiceCutManagerService`, rama deudores |
| `cancelados` | `CORTADO POR CANCELACION - LISTA DE CANCELADOS` | `ServiceCutManagerService` (rama cancelados, ya activa) y el endpoint `POST /subscription/generate-address-list-cancelled-subscriptions` |

`CutLists` (`service/mikrotik/CutLists.kt`) es la fuente de verdad del par lista + comment. `MikroTikService` expone `addIpToCutList`, `removeIpFromCutList`, `createCutDropRule` y `removeIpFromAllCutLists`, todos parametrizados por lista; los métodos `*DebtorsList` quedan como alias sobre `CutLists.DEBTORS`. Cada ciclo de corte limpia **solo** su lista y su regla, y crea ambas reglas con `place-before` del primer accept general.

La reactivación por pago (`MikrotikPaymentReactivationHandler`, `ServiceReactivationManager`) ahora limpia la IP de **ambas** listas, para que un recontratado no quede cortado por el residuo de la otra.

**Requiere deploy del WAR**: sin él, prod sigue con una sola lista y con la regla drop recreada al final de la cadena, donde los `accept` de subida se le adelantan.

**Ruta default duplicada: falso positivo.** Las dos `0.0.0.0/0` apuntan al mismo `38.224.231.1` pero viven en tablas distintas, `main` y `toTarazona`; esta última es el policy routing de "Clientes con paginas problematicas" (2 reglas mangle `mark-routing` + SNAT a `8.243.126.161`). Borrar una rompería ese desvío.

**`192.169.22.0/24`: bloqueado, no vacío.** El gateway `192.169.22.1/24` sigue en `LAN-VLAN1`. Sin DHCP, sin address-list y con un solo ARP incompleto. Pero en BD hay **9** filas con IP `192.169.*` (8 CANCELLED y la **840** ACTIVE en `192.169.22.170`, con cola en MK2, 0 bytes y ping 100% loss). Las 9 son el mismo error de tecleo: `192.169` en vez de `192.168`.

Estado de la **840** (Patricia Nora Trujillo Vela, WIRELESS): cliente real y al corriente salvo un recibo vencido el 15-ago-2026; **no** está en `deudores`, así que su falta de tráfico no es un corte. Su IP "correcta" `192.168.22.170` **ya está ocupada** por la suscripción **1609** (ARP vivo, MAC `B4:64:15:F6:0B:AF`), de modo que no basta con arreglar el dígito: hay que asignarle una IP libre de `192.168.22.0/24` (46 activos), mover su cola y recién entonces retirar el gateway `192.169.22.1/24`.

## Estado live 2026-09-10 (post-cambios)

| Caja | Hecho |
|------|--------|
| OLT VLAN 100 | Tagged en `0/3/2`, native **101**. **457** SP |
| OLT VLAN 1 | Tagged en `0/3/3`, native **102**. **669** SP. Parque legado |
| OLT VLAN 1000 | Tagged en `0/3/2`. **2** SP (lab) |
| Total SP | **1127** (1039 up / 88 down) el 2026-09-10 AM |
| MK2 | `vlan100-olt`, `vlan1000-olt` en `sfp-sfpplus2`; `vlan1-olt` → `LAN-VLAN1` |
| Lab ZTE `ZTEGDC47BFFD` | `0/1/6` ONT 117, profile 12, SP 1851+1856. **Offline** (dying-gasp) |
| Lab VSOL `12345B4641531C0B6` | `0/1/6` ONT 116 (`VSOL-0031C0B6`), SP 1857 VLAN 1000 **up**. WCD.1 DHCP **`10.20.0.2`** VLAN 1000. WCD.2 `192.168.250.21` VLAN 100. CR `http://10.20.0.2:7547/tr069` |
| Forward deudores | `CORTADO POR DEUDA` (`*37`) **antes** de `OLT sfp-sfpplus2 forward` (`*11`) |
| GRE `*22` | Sigue `10.255.255.1/30` **invalid** |
| CRS310 | Sigue ROS 6.49.6, SSH/Winbox sin allowlist. El CPU 80–83% del snapshot era **artefacto** (`/tool profile` ~0,5%) |

Gateways VLAN 100 en `vlan100-olt` (no en el puerto físico): `.30.1/24`, `.255.1/22`, `.250.1/24`. Gestión: `10.20.0.1/22` y `10.20.250.1/24` en `vlan1000-olt`.

## Backlog (qué falta)

| Pri | Tema | Estado | Nota |
|-----|------|--------|------|
| P0 | 235 ONUs OLT SP 100 y SmartOLT `vlan=1` | **Pendiente** | Gate de oleadas a VLAN 100. Cifra del snapshot 09-09; no recontada |
| P0 | Cruzar IP/gateway de esos 235 (¿L3 aún en VLAN 1?) | **Pendiente** | Tras tagged el GW de VLAN 100 es `vlan100-olt`, no `sfp-sfpplus2` |
| P0 | Corte deudores alcanzable | **Hecho en MK2** | Deploy WAR para que el cron no la mande otra vez al final |
| P0 | ACTIVE sin cola; colas ≠ plan | **Hecho 2026-09-10** | Recontado: 2 ADD, 20 PATCH (3 de velocidad), 3 RETARGET, 1 RECLAIM. [mk2-queue-reconcile-2026-08-28.md](./mk2-queue-reconcile-2026-08-28.md) |
| P0 | `traffic-flow` / SNMP en MK2 | **Pendiente** | Sin histórico no hay techo de capacidad |
| P1 | Allowlist SSH/Winbox CRS | **Pendiente** | |
| P1 | Borrar GRE `*22`; unificar 3 dst-nat CloudOLT | **Pendiente** | |
| P1 | Cerrar SSH/REST MK1 a Internet | **Pendiente** | Fuera del plano de datos; sigue expuesto |
| P1 | `forward` default-drop | **Pendiente** | El corte ya no depende de eso; la cadena sigue siendo accept por interfaz |
| P1 | DNS cacheante MK2; cola padre / fq-codel | **Pendiente** | |
| P2 | Prohibir altas `subscription.vlan=1` | **Pendiente** | Destino documentado; el backend aún acepta `1` |
| P2 | Poblar `subscription.vlan`; oleadas 1→100 | **Pendiente** | Incluye `ether5` / `ether7` / PPPoE |
| P2 | Colas de cancelados | **Hecho 2026-09-10** | 14 borradas. 3 no: tienen tráfico vivo, borrar la cola las deja sin límite |
| P2 | Colas huérfanas (24) | **A propósito** | Clientes sin `id:` en el nombre, colas de laboratorio y colas viejas por cambio de IP. Solo reporte |
| P2 | Ruta default "duplicada" | **Falso positivo** | Son `main` y `toTarazona`, mismo GW, dos tablas. La segunda la usan 2 mangle `mark-routing`. No se toca |
| P2 | `192.169.22.0/24` | **Bloqueado** | Prefijo de facto vacío pero con 1 fila ACTIVE en BD (sub **840**). Requiere renumerar antes de retirar el gateway |
| P2 | 5 IPs con dos suscripciones (`CONFLICT`) | **Pendiente** | Decisión en BD, no en MK2 |
| P2 | Apagar `.255`/`.250` de VLAN 100 | **A propósito** | Hasta terminar oleadas de gestión |
| — | Corte de **cancelados** | **No** | `processAndLogCancelledSubscriptions` sigue comentado |
| — | VLAN 1000 en parque (no lab) | **No** | Authorize sigue pintando un SP (VLAN 100). USB stock a 1000: almacén |

## Decisiones (grill) — con actualización

| Q | 2026-09-09 | 2026-09-10 |
|---|------------|------------|
| Q1 | VLAN 1 en NNI propio mientras haya residual; destino oleadas a 100 | **Confirmado.** La 1 no se agranda. [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md) |
| Q2 | VLAN 100 plana; más `/24`; no QinQ | Sigue. El NNI **sí** es trunk 802.1Q (100 + 1000 en `0/3/2`) |
| Q3 | Dual-WAN `.255` se queda en VLAN 100 | **Superado en lab:** WCD.1 gestión va a VLAN **1000** / `10.20.0.0/22`. `.255` en VLAN 100 sigue vivo para el parque |
| Q4 | WG = control-plane; GRE `*22` basura | WG hecho. `*22` **sigue sin borrar** |
| Q5–Q8 | Inventario, CRS, canvas | CRS CPU bajado a no-issue. Inventario 235 **sigue** |

**Gate de Q1-C (sigue):** no más oleadas a VLAN 100 hasta reconciliar los 235 desfasados.

---

## Snapshot 2026-09-09 (evidencia original, no reejecutada)

Lectura live sin escrituras. Las cifras de L2 nativo y “MK2 sin VLAN de cliente” **ya no describen la red**.

### Tesis de entonces (obsoleta)

MK2 gateway L3 **untagged**. Única 802.1Q = WAN 450. Dual-WAN = dos IPs en VLAN 100.

```text
ONU user-vlan 100  →  OLT SP vlan 100  →  NNI 0/3/2 native 100 untagged  →  MK2 sfp-sfpplus2
```

### L2 entonces

| Caja | Hecho |
|------|--------|
| OLT `display vlan 1` | Native 1 en `0/3/3` up. `0/3/2` no está en VLAN 1 |
| OLT `display vlan 100` | Native **100** en `0/3/2` up. 421 SP (405 up / 16 down) |
| OLT MCU `0/3` | `native-vlan 2 vlan 100` |
| Lab `0/1/6` ont 117 | SP #1851 vlan 100, up |
| MK2 | Cero `/interface vlan` de cliente. Solo VLAN 450 WAN |
| CRS310 | `vlan-filtering=no`, SVI 450 `38.224.231.8`. ROS 6.49.6. CPU reportado ~80–83% (artefacto). SSH `address=""` |

### Cruce inventario (Q8-C) — clave `slot/port/ont`

SmartOLT 818 ONUs (631 vlan=1, 187 vlan=100).

| Cruce | N |
|-------|---|
| OLT VLAN100 up **y** SmartOLT vlan=100 | 170 |
| OLT VLAN100 up **y** SmartOLT **vlan=1** (desfasado) | **235** |
| OLT VLAN100 up ausente de SmartOLT | 0 |
| SmartOLT vlan=100 y OLT down | 15 |
| SmartOLT vlan=100 no en dump OLT | 2 (`TESTHU` 1/6/16, `tes2` 1/6/35) |

El “421 vs 185” no era tagging misterioso: **CloudOLT no se actualizó** cuando el SP pasó a 100.

Riesgo L3 entonces: ARP `sfp-sfpplus2` 224 hosts `.30`. Si parte de los 235 seguía con GW en `LAN_MK1` (`sfp3`), el L2 salía por `0/3/2` y no encontraba el GW.

### MK2 L3 / NAT / queues (Q8-B)

| Dato | Valor |
|------|--------|
| Queues simple | 966 (185 en `192.168.30`; resto pools VLAN1 ya en MK2) |
| Conntrack | 64 170 / 1 048 576 |
| ARP | 2299 total; 442 `sfp2` (224×.30, 194×.255, 17×.250); 1821 `LAN_MK1` |
| DHCP | `dhcp-provisioning-255` en `sfp2`, pool `/22`, 186 used |
| NAT | masquerade WAN 450; `.252/22` y `.250/24`; SNAT WG; **3× dst-nat CloudOLT** |
| Túnel | `wg-ispadmin-vps` up. `/interface gre` = 0. IP huérfana `10.255.255.1/30` `*22` |
| Mgmt OLT | `10.11.104.89/24` en `ether3` |
| `192.168.31.1` | **ausente** |

ONU lab GenieACS (caché, sin GPV): WAN1 DHCP `192.168.255.236` VLAN 100; WAN2 static `192.168.250.22` VLAN 100. (ZTE `ZTEGDC47BFFD`.)

### Hallazgos de la pasada profunda (09-09, no reejecutada)

- Corte por deuda **no cortaba** (corregido 10-09 en MK2).
- 16/42 deudores y 56 CANCELLED con tráfico; `processAndLogCancelledSubscriptions` comentado.
- 27 ACTIVE sin cola; 96 colas huérfanas; 70 colas de canceladas; `subscription.vlan` NULL en casi todo.
- 29 gateways `/24` en el bridge VLAN 1; `192.169.22.0/24` no es RFC1918.
- CIR simple-queues ~165 Gbps vs WAN 4 Gbps. MK2 CPU no era el techo.
- MCU: solo dos 10G, ambos ocupados. Consolidar VLAN 1→100 es lo que libera un puerto.

## Relacionado

- [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md)
- [checklist-mk2-vlan1-sfp-sfpplus3.md](./checklist-mk2-vlan1-sfp-sfpplus3.md)
- [mikrotik-mk2-comandos-catalogo.md](./mikrotik-mk2-comandos-catalogo.md)
- [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)
