# Cutover VLAN 100 → responsabilidad del MK2 (tagged) — 2026-09-10

> **Rollback cerrado 2026-09-10:** eliminadas las IPs deshabilitadas de `sfp-sfpplus2` y los scripts `vlan100-swap` / `vlan100-rollback` / `vlan100-guard`. Detalle y decisión de topología: [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md).

> **Destino:** la VLAN 1 no se agranda; el internet de abonado va a la VLAN 100. [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md).

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)
> Reemplaza el estado descrito en [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md) y [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md).

La VLAN 100 dejó de viajar **untagged** por `0/3/2`. Ahora la OLT la etiqueta y el **MK2 la termina en la subinterfaz `vlan100-olt`**. Las subredes, IPs de clientes y el pool DHCP **no cambiaron**.

| | Antes | Después |
|---|---|---|
| Native VLAN de `0/3/2` | **100** | **101** |
| VLAN 100 en el trunk | untagged | **tagged** |
| Gateways `.30.1/24`, `.255.1/22`, `.250.1/24` | `sfp-sfpplus2` | **`vlan100-olt`** |
| `dhcp-provisioning-255` | `sfp-sfpplus2` | **`vlan100-olt`** |
| Reglas firewall/mangle | `in/out-interface=sfp-sfpplus2` | **`in/out-interface-list=OLT-VLAN100`** |

Ventana de corte del cambio bueno: **1,07 s**. Alcance: 457 service-ports, ~460 clientes activos.

```text
 CONTROL (independiente del cambio)        DATOS (lo que se modificó)
 ─────────────────────────────────         ──────────────────────────
 VPS 212.85.13.47                          ONU ── GPON ── OLT
   │ WireGuard                                              │ 0/3/2  VLAN 100 tagged
   ▼                                                        ▼
 MK2 ether3 10.11.104.89                   MK2 sfp-sfpplus2 (sin IP)
   │ red fuera de banda                                     │
   ▼                                                        ▼
 OLT meth0 10.11.104.2                     MK2 vlan100-olt ── NAT ── WAN
```

`10.11.104.2` vive en **`interface meth0`**, el puerto de gestión fuera de banda de la placa MCU. No está en ninguna VLAN ni en `0/3/2`, por eso el cutover no puede dejar la OLT incomunicada.

---

## Alcance: la OLT tiene dos uplinks y ambos van al MK2

**MK1 fue retirado.** Los dos NNI de la OLT terminan en el mismo router, por puertos físicos distintos. **Ambos quedaron migrados a tagged el 2026-09-10.**

```text
                          MK2 (38.224.231.4)
                          ──────────────────
 OLT 0/3/2  VLAN 100 tagged ──► sfp-sfpplus2 ──► vlan100-olt      3 gateways
 OLT 0/3/3  VLAN 1   tagged ──► sfp-sfpplus3 ──► vlan1-olt ──┐
                                                             ▼
                                                    bridge LAN-VLAN1  29 gateways
                                                    + ether5 switch SFP
                                                    + ether7 AirFiber
                                                    + servidor PPPoE
```

| | VLAN 1 | VLAN 100 |
|---|---|---|
| Puerto OLT | `0/3/3`, native **102** | `0/3/2`, native **101** |
| Termina en | `vlan1-olt` → bridge `LAN-VLAN1` | `vlan100-olt` |
| Service-ports | **669** | 457 |
| Subredes | **29** | 3 |
| Paquetes subida / bajada | 55 146 M / **121 518 M** | 14 842 M / 31 104 M |
| Reglas firewall | `in/out-interface=LAN-VLAN1` | `in/out-interface-list=OLT-VLAN100` |

El lado VLAN 1 mueve **~3,7× más tráfico**. El bridge de ese dominio L2 es **`LAN-VLAN1`** (renombrado el 2026-09-10; antes `LAN_MK1`). Nombres: [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md).

Detalle del segundo cutover, incluidas sus diferencias de diseño: [checklist-mk2-vlan1-sfp-sfpplus3.md](./checklist-mk2-vlan1-sfp-sfpplus3.md).

### Las dos VLANs de aparcamiento

Como `undo native-vlan` no existe, cada puerto necesita **otra** VLAN miembro que asuma el rol de native para liberar a la de servicio. Ninguna de las dos lleva ONUs ni tráfico:

| Puerto | VLAN de servicio (tagged) | VLAN de aparcamiento (untagged, vacía) |
|---|---|---|
| `0/3/2` | 100 | **101** |
| `0/3/3` | 1 | **102** |

### Reparto de ONUs

De **806 ONUs** con service-port, **301 tienen uno en cada VLAN**: la misma ONU con ambos caminos preparados, andamiaje de la migración gradual hacia la VLAN 100.

| Grupo | ONUs |
|---|---|
| Service-port en ambas VLANs | **301** |
| Solo VLAN 1 | 349 |
| Solo VLAN 100 | 156 |

Los 22 puertos GPON en uso están **mezclados**: la separación es por service-port, no física. Cuál camino usa cada cliente lo decide la VLAN que su CPE etiqueta en la WAN.

Por eso el cutover no pudo afectar al lado VLAN 1: el etiquetado hacia la ONU lo resuelve el `tag-transform translate` de cada service-port, independiente de lo que haga el NNI. Una ONU dual sigue saliendo por `0/3/3` untagged si manda `user-vlan 1`, y por `0/3/2` tagged si manda `user-vlan 100`.

> Si algún día se aplica lo mismo a la VLAN 1 (`native-vlan 3 vlan X` + subinterfaz en el MK2), el alcance es bastante mayor: 29 subredes, 5 puertos ether extra en el bridge y casi 4× el tráfico.

---

## El hallazgo que hizo fallar el primer intento

**`undo native-vlan {puerto}` no existe en V800R015.** La CLI lo rechaza:

```text
undo native-vlan 0
                ^
% Unknown command, the error locates at '^'
```

El eco muestra `undo native-vlan0`: el parser consume el espacio y no reconoce el comando. No es un artefacto del envío — falla igual carácter a carácter, mientras que `undo port vlan 101 0/3 0` sí respeta los espacios en la misma sesión.

**La vía correcta es mover el native a otra VLAN miembro del puerto.** Como `0/3/2` ya era miembro de la 100 y la 101, basta:

```text
interface mcu 0/3
native-vlan 2 vlan 101
```

La 101 pasa a untagged (no tiene service-ports, nadie la usa) y la **100 pasa a tagged**, que es lo que se buscaba.

### Por qué el fallo pasó desapercibido

El primer script envió `undo native-vlan 2` y **solo esperó el prompt, sin leer la salida**. La OLT devolvió `Unknown command`, el prompt volvió, y el script lo dio por bueno. El MK2 ya había movido las IPs a `vlan100-olt`, que esperaba tramas tagged que la OLT seguía enviando untagged → **~2,5 min sin servicio** hasta el rollback manual.

Dos correcciones obligatorias que quedaron en el script definitivo:

1. **Validar la salida** de todo comando de escritura contra `failure|error|unknown command|parameter error`.
2. **Contar solo ARP completas.** El guard original usaba `[:len [/ip arp find interface=vlan100-olt]]`, que durante la caída marcaba **305** — todas incompletas, del propio MK2 buscando clientes inalcanzables. Con esa métrica el auto-rollback nunca habría disparado. La correcta es:

```text
:local n [:len [/ip arp find where interface="vlan100-olt" complete=yes]]
```

---

## Banco de pruebas seguro: `0/3/0`

`0/3/0` y `0/3/1` son GE **sin óptica, offline y sin tráfico**. Ahí se determinó la sintaxis sin tocar producción:

```text
port vlan 101 0/3 0          → OK
native-vlan 0 vlan 101       → OK, "Native VLAN: 101"
undo native-vlan 0           → % Unknown command
undo port vlan 101 0/3 0     → Failure: VLAN has been configured as native VLAN of the ports
native-vlan 0 vlan 1         → OK (restaurar primero el native)
undo port vlan 101 0/3 0     → OK
```

Nota de orden: no se puede sacar un puerto de una VLAN que es su native. Hay que reasignar el native antes.

---

## Validación previa del transporte tagged

Antes del cutover se comprobó que la 101 tagged realmente atraviesa el DAC, con una `vlanif` temporal en la OLT y su par en el MK2 (`172.31.99.0/24`):

```text
interface vlanif 101
ip address 172.31.99.2 255.255.255.0
```

Resultado: **5/5 ping, 0% pérdida**, ARP completo bidireccional, y la OLT reportando `VLAN Encap-mode : single-tag`. Ambos extremos se eliminaron después (`undo interface vlanif 101`).

MTU: `sfp-sfpplus2` tiene `l2mtu=1584` y `vlan100-olt` hereda `l2mtu=1580`, holgado para los 1504 que exige el tag.

---

## Firewall: make-before-break con interface list

Una auditoría encontró **23 referencias a `sfp-sfpplus2`** en la config del MK2, no solo las 3 IPs y el DHCP que contemplaba el plan. Cuatro eran reglas activas:

| Ruta | id | Regla | Paquetes previos |
|---|---|---|---|
| `ip/firewall/filter` | `*11` | forward accept subida | 14 841 528 018 |
| `ip/firewall/filter` | `*2` | forward accept bajada | 31 102 552 698 |
| `ip/firewall/filter` | `*3` | input accept mgmt | 4 743 227 |
| `ip/firewall/mangle` | `*2` | prerouting `toTarazona` | 40 800 |

Las cadenas de RouterOS son *default-accept*, así que no dejarlas migradas no habría cortado a nadie, pero sí habría roto el **policy routing de Tarazona** y cambiado a qué tráfico aplican los drops de `deudores` y `staging`.

Solución: una **interface list con ambas interfaces**, de modo que las reglas coinciden antes y después del swap. Se precarga sin cambiar comportamiento.

```text
/interface list add name=OLT-VLAN100
/interface list member add list=OLT-VLAN100 interface=sfp-sfpplus2
/interface list member add list=OLT-VLAN100 interface=vlan100-olt
```

Las 9 referencias en `ip/route` son rutas conectadas derivadas de las IPs y se mueven solas. Las de `ip/arp` son aprendizaje dinámico, no configuración.

> **Sintaxis REST:** para borrar una propiedad hay que usar el prefijo `!` con `null`. Mandar `{"in-interface": ""}` falla con `ambiguous value of interface, more than one possible value matches input`, porque RouterOS intenta resolver la cadena vacía como nombre de interfaz.
>
> ```json
> PATCH /rest/ip/firewall/filter/*11  {"!in-interface": null, "in-interface-list": "OLT-VLAN100"}
> ```

---

## Scripts persistentes en el MK2

Quedan en `/system/script` para futuros cambios o rollback:

| Script | Qué hace |
|---|---|
| `vlan100-swap` | Deshabilita IPs de `sfp-sfpplus2`, habilita las de `vlan100-olt`, mueve el DHCP |
| `vlan100-rollback` | El inverso. **Idempotente**: ejecutarlo en el estado original no cambia nada |
| `vlan100-guard` | Cuenta ARP completas en `vlan100-olt`; si < 50 ejecuta el rollback y se autoelimina |

El guard se arma como scheduler solo durante la ventana:

```text
/system scheduler add name=vlan100-guard interval=00:05:00 \
  on-event="/system script run vlan100-guard" policy=read,write,policy,test
```

---

## Rollback completo

El orden importa: primero la OLT, después el MK2, porque el MK2 es el que da acceso a la OLT.

```text
# OLT
interface mcu 0/3
native-vlan 2 vlan 100

# MK2
/system script run vlan100-rollback
/system scheduler remove [find name=vlan100-guard]
```

Ambos lados quedan como estaban en los backups de `~/gigafiber/.cutover-backups/` (fuera de los repos git, porque un export de RouterOS incluye hashes de contraseñas y claves WireGuard).

---

## Resultado verificado

| Métrica | Antes | Después |
|---|---|---|
| ARP completas en el gateway | 433 (`sfp-sfpplus2`) | **434** (`vlan100-olt`) |
| Ping a clientes de `.30/.255/.254` | OK | **OK, 0% pérdida** |
| Tráfico subida / bajada (10 s) | fluyendo | **21 877 / 50 905 paquetes** |
| DHCP `dhcp-provisioning-255` | `sfp-sfpplus2` | `vlan100-olt`, leases repoblando |
| Referencias directas a `sfp-sfpplus2` en firewall | 4 | **0** |

Quedan ~39 entradas ARP obsoletas en `sfp-sfpplus2` que envejecen solas.

Los leases DHCP se repueblan de forma gradual: al mover el servidor se vacía su base y los clientes vuelven a pedir al renovar. Con `lease-time=1h`, la recuperación completa toma hasta una hora. Las IPs se conservan porque RouterOS respeta la dirección solicitada.

`save configuration` aplicado en la OLT: la línea 254 del `display current-configuration` dice `native-vlan 2 vlan 101`. **Es obligatorio**, porque sin guardar un reinicio dejaría la OLT en untagged mientras el MK2 espera tagged.

---

## Relacionado

- [olt-vlan100-mk2-uplink.md](./olt-vlan100-mk2-uplink.md)
- [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
- [auditoria-red-olt-mk2-2026-09-09.md](./auditoria-red-olt-mk2-2026-09-09.md)
- [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md)
