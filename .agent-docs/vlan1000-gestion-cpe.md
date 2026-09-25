# VLAN 1000 — gestión de CPE (TR-069 + staging e2e)

Implementado **2026-09-10**. Separa la gestión del CPE del tráfico de internet del abonado, que hasta hoy compartían la **VLAN 100**.

## Por qué

La VLAN 100 lleva a la vez el internet del abonado y el aprovisionamiento TR-069. Un CPE mal configurado, un pool agotado o un barrido de GenieACS golpean el mismo dominio L2 que el servicio. La VLAN 1000 es **solo gestión**: TR-069, staging e2e y acceso web/SSH a la ONU. La VLAN 100 se queda como está y sigue siendo la de internet.

## Plano de datos

```text
 CPE (WAN mgmt tagged 1000)
   │
   ▼
 ONU  ── gem 2 ──►  OLT MA5608T
                     │  service-port user-vlan 1000
                     ▼
                   0/3/2  (native 101, VLAN 100 y 1000 tagged)
                     │
                     ▼
                   MK2 sfp-sfpplus2
                     │
                     ▼
                   vlan1000-olt   10.20.0.1/22 + 10.20.250.1/24
                     │
                     ▼
                   wg-olt ──► VPS 10.255.255.2 (GenieACS)
```

La VLAN 1000 viaja **tagged por el mismo uplink que la 100**. El native del puerto sigue siendo la 101 de aparcamiento; `0/3/3` (VLAN 1) no se tocó.

## Direccionamiento

| Prefijo | Gateway | Uso | DHCP |
|---------|---------|-----|------|
| `10.20.0.0/22` | `10.20.0.1` | Gestión TR-069 de CPE | Sí, pool `10.20.0.2–10.20.3.254`, lease 1h |
| `10.20.250.0/24` | `10.20.250.1` | Staging e2e | No: la IP la pinta TR-069 |

Mismo tamaño que el par `192.168.252.0/22` + `192.168.250.0/24` que hoy sirve la VLAN 100. Se eligió un prefijo nuevo porque en un router único no pueden coexistir las mismas direcciones en `vlan100-olt` y `vlan1000-olt`.

Los pools `.255`/`.250` de la VLAN 100 **siguen vivos** hasta que terminen las oleadas de migración de parque.

## Configuración aplicada

### OLT (`10.11.104.2`)

```text
vlan 1000 smart
port vlan 1000 0/3 2

ont-lineprofile gpon profile-id 12 profile-name "Generic_1_V100M1000MGM"
  tcont 0 dba-profile-id 2
  tcont 1 dba-profile-id 11
  gem add 1 eth tcont 1
  gem add 2 eth tcont 1
  gem mapping 1 1 vlan 100
  gem mapping 2 1 vlan 1000
  commit

interface gpon 0/1
  ont modify 6 117 ont-lineprofile-id 12
  ont modify 6 116 ont-lineprofile-id 12

service-port vlan 1000 gpon 0/1/6 ont 117 gemport 2 multi-service user-vlan 1000 \
  tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9
service-port vlan 1000 gpon 0/1/6 ont 116 gemport 2 multi-service user-vlan 1000 \
  tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9
```

Script: [`scripts/olt-vlan1000-mgmt.expect`](../scripts/olt-vlan1000-mgmt.expect) (VLAN + uplink) y [`scripts/olt-vlan1000-lab-onu.expect`](../scripts/olt-vlan1000-lab-onu.expect) (profile + ONU ZTE lab). Ambos aceptan `inspect` o `apply`.

**El profile 6 `Generic_1_V100` no se tocó**: lo usan el resto de ONUs en producción y un `commit` reconfigura por OMCI a todas las vinculadas. El profile 12 es un clon exacto del 6 más el mapeo `gem 2 → VLAN 1000`, y solo está vinculado a las ONUs de laboratorio (`0/1/6` ONT 116 y 117).

**Trampa del alta FIBER:** `Generic_1:100` resuelve lineprofile **6**. Si se autoriza la VSOL lab con ese binding, la OLT queda solo con service-port VLAN **100** y el CPE pierde el camino al ACS (`WCD.1` tagged 1000, `10.20.0.0/22`). El Gateway ahora detecta el sufijo `0031C0B6` / `12345B4641531C0B6` y fuerza lineprofile **12** + `service-port vlan 1000 … gemport 2`. No tocar WCD.1 en TR-069: internet (PPPoE o static) va en WCD.2 VLAN 100.

Existía un profile 11 `Generic_1_V100M101TR069MGM` con 0 bindings que ya ensayaba el patrón dual-VLAN, pero usa la **101**, que hoy es la VLAN de aparcamiento del native en `0/3/2`. No sirve: se descartó.

### MK2 (`38.224.231.4`)

[`scripts/genieacs/mk2-mgmt-vlan-1000.rsc`](../scripts/genieacs/mk2-mgmt-vlan-1000.rsc), idempotente. Crea `vlan1000-olt`, los dos gateways, pool + dhcp-server + network, la interface list `OLT-VLAN1000`, NAT masquerade de ambos prefijos (ONU→ACS), **srcnat** `10.255.255.2` → `10.20.0.0/22` por `vlan1000-olt` (ACS→ONU: ping/summon; sin eso la ONU no responde al `/30` de wg), el accept de Connection Request `:7547` desde `10.255.255.2` y el drop de aislamiento hacia `192.168.22.0/24`.

Solo la subinterfaz entra en `OLT-VLAN1000`; el puerto físico no, porque lo comparte con la VLAN 100 y una regla por lista lo capturaría dos veces.

Aplicación vía REST: crear un `/system/script` con el contenido y ejecutarlo. Un `PATCH` con `.id` en la URL devuelve `400 missing or invalid resource identifier`; hay que usar `POST <path>/set` con `.id` en el cuerpo.

### VPS

`10.20.0.0/22` y `10.20.250.0/24` añadidos a los `AllowedIPs` del peer `wg-olt` y a [`scripts/genieacs/wg-olt-customer-routes.sh`](../scripts/genieacs/wg-olt-customer-routes.sh). Se aplicaron en caliente con `wg set` + `ip route replace`, sin rebotar el túnel.

## Verificación hecha

| Comprobación | Resultado |
|---|---|
| VLAN 1000 tagged en `0/3/2`, native intacto | `Native VLAN 101`, state `up` |
| Service-ports ONU ZTE lab (`0/1/6` 117) | 1851 VLAN 100 gem 1 + **1856 VLAN 1000 gem 2** (ONU offline) |
| Service-ports ONU VSOL lab (`0/1/6` 116) | VLAN 100 gem 1 + **1857 VLAN 1000 gem 2**, ambos **up** |
| `vlan1000-olt` en MK2 | `running=true`, ambos gateways `invalid=false` |
| Idempotencia del `.rsc` | 2ª ejecución: filter 21, NAT 10, sin duplicados |
| VPS → `10.20.0.1` y `10.20.250.1` | OK |
| Regresión VLAN 100 / VLAN 1 | SP 457 / 669 sin cambio; PPPoE 51, leases 276, ARP 2406 idénticos al baseline |
| Direcciones inválidas en MK2 | 1, la de siempre: `10.255.255.1/30` del GRE huérfano |

Firewall MK2: 18 → 21 reglas filter, 8 → 10 NAT. Ninguna regla previa se movió ni se desactivó.

## Primera CPE viva: VSOL lab `12345B4641531C0B6`

Migrada **2026-09-10** sin USB: OLT primero (profile 12 + SP 1857), después SPV TR-069 solo en **WCD.1**.

| Campo | Antes | Después |
|-------|--------|---------|
| DeviceId ACS | `B46415-V2804AX15T-12345B4641531C0B6` | igual |
| Tags | `lab`, `sub-2349` | igual |
| OLT | `0/1/6` ONT 116, SN `VSOL-0031C0B6`, profile 6 | profile **12**, SP **1857** VLAN 1000 **up** |
| WCD.1 (TR-069) | DHCP `192.168.255.249` VLAN **100** | DHCP **`10.20.0.2`** VLAN **1000** |
| WCD.2 (internet prueba) | Static `192.168.250.21` VLAN 100 | **sin cambio** |
| ConnectionRequestURL | `http://192.168.255.249:7547/tr069` | `http://10.20.0.2:7547/tr069` |

Orden: nunca cambiar la VLAN del CPE antes del service-port. GPV con `?connection_request` tras el SPV → HTTP 200, 0 faults, TCP `:7547` abierto en `10.20.0.2`.

## Pre-registro OLT en el runner de retag

`scripts/genieacs/retag-tr069-vlan1000.sh` registra el service-port VLAN 1000 **antes** de encolar GenieACS. Login JWT al **Core prod** (`--prod` → `https://api.gigafiberperu.cloud/ispadmin`) y `POST /onu/{sn}/service-port/ensure-mgmt`. Staging no aísla: misma OLT, inventario incompleto, 2 VTY extra. El Gateway, si falta la VLAN: lee el lineprofile **actual**, añade `gem mapping` VLAN 1000 al GEM de internet si hace falta (`commit`), y abre el SP en ese GEM. **No** hace `ont modify` ni rebind a profile 12 (eso cortó internet VLAN 1 en `VSOL00872649`). Por cada ONU pinguea la WAN de internet (VPS/`wg-olt`) antes, tras el SP y tras el CPE; si estaba reachable y se pierde, aborta y **no** hace PUT/task NBI. Idempotente si el CLI dice `already exists` y el display ya lista 1000. No abre SSH a `10.11.104.2` ni llama `/api/olt-gateway/` desde el script. El provision GenieACS solo escribe la WAN TR-069 (pool `192.168.252.0/22`); la WAN de internet no se toca.

La ONU ZTE `ZTEGDC47BFFD` (`0/1/6` ONT 117) sigue **offline** (`dying-gasp`). El stock nuevo sigue necesitando USB en almacén a VLAN 1000.

## Fuera de alcance

- **Tagged 1000 en `0/3/3`**: no. El parque VLAN 1 se migra a **VLAN 100** (mismo NNI `0/3/2` que ya lleva 1000), no se le pone gestión en el uplink legado. [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md).
- **Perfiles de fábrica / GenieACS de stock**: la preconfiguración masiva se hace por USB en almacén, no desde aquí. Esta VSOL se movió en caliente porque ya estaba en GenieACS.
- **Apagar `.255`/`.250` de la VLAN 100**: cuando terminen las oleadas.
- **Segundo service-port en ONUs de producción**: solo laboratorio (`lab` en GenieACS).

## Relacionado

- [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md)
- [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md)
- [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md)
- [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
- [mikrotik-mk2-comandos-catalogo.md](./mikrotik-mk2-comandos-catalogo.md)
- [fase2-tr069-vlan1000-lab-fix-2026-09-18.md](./fase2-tr069-vlan1000-lab-fix-2026-09-18.md) — retag TR-069 100→1000 (GenieACS) con pre-registro OLT vía Core
