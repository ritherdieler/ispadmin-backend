# OLT VLAN 100 — uplink MK2 (0/3/2)

> Hub: [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)
>
> ⚠️ **Desactualizado desde 2026-09-10.** La VLAN 100 ya **no** viaja untagged: el native de `0/3/2` es la **101** y la 100 sale **tagged** hacia `vlan100-olt` en el MK2. Estado vigente: [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md). Nombres de interfaces: [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md). Lo que sigue documenta el montaje piloto original de 2026-07-19.

Configuración aplicada **2026-07-19** en MA5608T `10.11.104.2` para enrutar tráfico piloto hacia MikroTik **38.224.231.4** (gateway **`192.168.30.1/24`** en **`sfp-sfpplus2`**, tráfico native untagged desde OLT).

## Topología L2

| OLT puerto | VLAN | MikroTik | Estado |
|------------|------|----------|--------|
| **0/3/3** | **1** (legacy) | MK1 `38.224.231.2` — **hoy MK2 `sfp-sfpplus3`** | Sin cambios — 759 ONUs |
| **0/3/2** | **100** (piloto) | MK2 `38.224.231.4` | Native VLAN **100**, link up — hoy native **101**, VLAN 100 tagged |

```text
ONU (IP estática) ──► OLT [bridge VLAN 100] ──► 0/3/2 untagged ──► MK2 sfp-sfpplus2 (192.168.30.1) ──► NAT ──► Internet
```

---

## Comandos OLT — VLAN 100 uplink

Script: `scripts/olt-vlan100-uplink.expect apply`

```text
enable
config
vlan 100 smart
port vlan 100 0/3 2
interface mcu 0/3
  native-vlan 2 vlan 100
quit
undo port vlan 1 0/3 2
quit
save configuration
```

---

## Comandos OLT — verificación uplink

```text
display vlan 100
display vlan 1
display current-configuration section mcu
display current-configuration section vlan-config
```

Resultado esperado:

```text
display vlan 100
  → 0/3/2  Native VLAN 100  up

display vlan 1
  → 0/3/3 up (0/3/2 ya NO aparece)
```

---

## ONU piloto autorizada ✅

| Campo | Valor |
|-------|-------|
| CPE | ZTE F6600R SN **`ZTEG-DC47C169`** |
| GPON | **0/1/6**, ont-id **127** |
| Perfiles | `PILOT_VLAN100` line/srv **profile-id 100** |
| Service-port | **#551** — `vlan 100`, **user-vlan 100** |
| IP abonado | `192.168.30.202/24`, GW `192.168.30.1`, WAN VLAN **100** tagged |
| Estado | GPON **online**, service-port **up**, ping MK2 OK (~2 ms) |
| Internet CPE | **Pendiente** — ver sección [Validación internet](#validación-internet) |

Script: `scripts/olt-onu-pilot-mk2-authorize.expect`

### Perfiles GPON (profile-id 100)

```text
enable
config
mmi-mode enable

ont-lineprofile gpon profile-id 100 profile-name PILOT_VLAN100
tcont 1 dba-profile-id 11
gem add 1 eth tcont 1
gem mapping 1 1 vlan 100
commit
quit

ont-srvprofile gpon profile-id 100 profile-name PILOT_VLAN100
ont-port eth 1
port vlan eth 1 100
commit
quit
```

### Alta ONU + service-port

```text
interface gpon 0/1
ont confirm 6 ontid 127 sn-auth 5A544547DC47C169 omci ont-lineprofile-id 100 ont-srvprofile-id 100 desc "PILOTO-MK2"
ont port native-vlan 6 127 eth 1 vlan 100
quit

service-port vlan 100 gpon 0/1/6 ont 127 gemport 1 multi-service user-vlan 100 tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9

save configuration
```

> **user-vlan 100** (no 1): el ZTE F6600R etiqueta VLAN 100 en la WAN.

### Verificación ONU piloto

```text
display ont info by-sn 5A544547DC47C169
display service-port port 0/1/6 ont 127
display ont optical-info 6 127
display ont info summary 0/1/6
```

---

## Validación internet

**2026-07-19 ~23:45** — monitoreo MK2 (`/tool torch interface=sfp-sfpplus2 duration=30`):

| Prueba | Resultado |
|--------|-----------|
| MK2 → `192.168.30.202` | ✅ |
| MK2 → `8.8.8.8` / `1.1.1.1` | ✅ |
| ONU → destinos externos | ❌ No se observó tráfico (solo ping a gateway) |
| HTTP CPE `192.168.30.202` | ✅ Responde (UI ZTE accesible desde MK2) |

El path MK2→WAN está operativo. Falta confirmar routing/NAT en el **ZTE** (revisar modo Router vs Bridge, DNS, default route).

---

## MK2: gateway en interfaz padre

> ⚠️ **Invertido el 2026-09-10.** Hoy el native de `0/3/2` es la VLAN **101**, la 100 sale **tagged** y los gateways viven en **`vlan100-olt`**, no en `sfp-sfpplus2`. Ver [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md). El párrafo siguiente describe el montaje anterior.

Con `native-vlan 2 vlan 100` en OLT, el tráfico VLAN 100 sale **untagged** hacia MK2. La IP **`192.168.30.1/24`** debe estar en **`sfp-sfpplus2`**, no en `vlan100-olt`.

Comandos MK2: [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md) — scripts `mikrotik-mk2-pilot-uplink.rsc`, `mikrotik-mk2-pilot-gateway-fix.rsc`.

Verificación MK2:

```bash
export MIKROTIK_PASSWORD='...'
./scripts/mikrotik-mk2-pilot-verify.sh
```

---

## Service-ports legacy

Los service-ports existentes siguen en **vlan 1** → salen por **0/3/3**. No tocar **0/3/3**.

> **Corregido 2026-09-10:** `0/3/3` ya no va a MK1, que fue retirado. Termina en el **MK2**, puerto `sfp-sfpplus3`, dentro del bridge `LAN_MK1` (nombre heredado). Los dos uplinks de la OLT llegan al mismo router por puertos físicos distintos.

---

## Relacionado

- [mikrotik-mk2-config-olt-uplink.md](./mikrotik-mk2-config-olt-uplink.md)
- [mikrotik-mk2-comandos-catalogo.md](./mikrotik-mk2-comandos-catalogo.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
- Plan: `.cursor/plans/multi_mikrotik_gigafiber_a973760c.plan.md`
