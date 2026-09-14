# Decisión: dos uplinks OLT ↔ MK2, sin bridge unificado (2026-09-10)

> Contexto: [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md) · Nombres: [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md)

Tras dejar las VLANs 1 y 100 **tagged** a cargo del MK2, se evaluó consolidar ambas en un solo NNI y unificarlas en un mismo bridge.

**Decisión: se mantiene un dominio por puerto y la topología asimétrica actual.**

## Estado medido (madrugada 2026-09-10 ~03:20)

| Dominio | Puerto MK2 | MACs | rx / tx |
|---|---|---|---|
| VLAN 100 | `vlan100-olt` | 443 ARP | 5,6 / 125 Mbps |
| VLAN 1 | `vlan1-olt` (OLT `0/3/3`) | 529 | 31 / 122 Mbps |
| VLAN 1 | `ether5` switch SFP | **199** | 2 / **23,8** Mbps |
| VLAN 1 | `ether7` AirFiber 9 Octubre | **11** | ~0 |

Hardware: CCR2116-12G-4S+, switch chip **Marvell 98DX3255**, CPU **2%**. Los tres DAC (`sfp-sfpplus1/2/3`) negocian **10 Gbps**; `sfp-sfpplus4` sin módulo. Bridges `LAN` y `LAN-VLAN1` con `vlan-filtering=false`, `fast-forward=true`. **50 sesiones PPPoE** activas en `192.168.26.0/24`.

## Por qué no un bridge unificado

- **La VLAN 100 no tiene otro miembro L2.** Cero puertos además de la subinterfaz: es tráfico puramente ruteado (3 gateways + DHCP). Meterla en un bridge añade una etapa de conmutación que no usa y rompe la lista `OLT-VLAN100` del firewall.
- **La VLAN 1 sí necesita su bridge.** `ether5` (199 MACs, 24 Mbps de madrugada) y `ether7` (11 MACs) comparten dominio de difusión con las ONUs, más el servidor PPPoE. **El 28% de las MACs del dominio VLAN 1 no son ONUs.**
- **Unificar exigiría `vlan-filtering=yes`** en un bridge vivo con 739 MACs, 29 subredes y 50 sesiones PPPoE. Un PVID o un untagged mal puesto deja la red a oscuras. Riesgo alto, beneficio nulo hoy.

**Cuándo reevaluar:** cuando el parque VLAN 1 (ONUs **y** `ether5`/`ether7`/PPPoE) haya pasado a VLAN 100. Ahí un bridge único con `vlan-filtering` es el diseño correcto, y lo dispara la migración de clientes, no la consolidación de puertos. Destino: [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md).

## Por qué no un solo NNI

Capacidad no es el límite: ambos dominios suman ~285 Mbps sobre enlaces de 10 Gbps. Aun con un pico seis veces mayor son ~1,7 Gbps.

El coste está en la resiliencia. Hoy hay **dos dominios de fallo independientes**: un DAC muerto en `0/3/3` tumba los 669 service-ports de VLAN 1 y la VLAN 100 sigue en pie. Consolidar convierte eso en un punto único que tumba los 1126.

Consolidar solo se paga si el puerto liberado se convierte en redundancia real:

| Opción | Gana | Cuesta |
|---|---|---|
| **Mantener 2 puertos** (elegida) | Aislamiento de fallos, reparto de carga, cero cambios | El segundo puerto no es failover del primero |
| Standby en frío en `0/3/3` | Failover por script en segundos, conserva hardware offload | Config duplicada que hay que mantener alineada |
| LACP 802.3ad | Failover automático, 20 G | Bonding RouterOS lo procesa la **CPU**: sale del fast path del 98DX3255. Requiere verificar `link-aggregation` en la V800R015 de la OLT |

## Cierre del rollback del cutover VLAN 100

Las tres IPs deshabilitadas que quedaban en `sfp-sfpplus2` (`192.168.30.1/24`, `192.168.255.1/22`, `192.168.250.1/24`) se **eliminaron**.

Con ellas se borraron los scripts `vlan100-swap`, `vlan100-rollback` y `vlan100-guard`. Era obligatorio: `vlan100-rollback` hacía `enable` de esas IPs y `disable` de las de `vlan100-olt`. Sin las IPs, ejecutarlo habría dejado la VLAN 100 **sin ningún gateway**.

Verificación posterior: ARP completas `vlan100-olt` **443**, `LAN-VLAN1` **744**, PPPoE **50**, tráfico normal en ambas VLANs.

**Siguen vivos** `vlan1-swap` y `vlan1-rollback`, porque `sfp-sfpplus3` continúa como puerto (deshabilitado) del bridge. Recordatorio: el rollback de la VLAN 1 **no basta desde el MK2**, hay que tocar también la OLT (`native-vlan 3 vlan 1`).

El NNI de VLAN 1 se mantiene **mientras haya residual**. No es el diseño de destino: [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md).
