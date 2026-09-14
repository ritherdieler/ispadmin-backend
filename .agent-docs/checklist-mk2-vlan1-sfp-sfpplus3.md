# Checklist: VLAN 1 en MK2 (`sfp-sfpplus3`)

> ## Estado 2026-09-10: **COMPLETADO**
>
> La OLT **etiqueta** la VLAN 1 en `0/3/3` y el MK2 la termina en `vlan1-olt`, dentro del bridge `LAN-VLAN1` (antes `LAN_MK1`). Ventana de corte: **1,17 s**. Hermano del cambio de la VLAN 100: [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md). Este NNI es el del **parque legado**; va a vaciarse hacia la VLAN 100. [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md).
>
> | | Antes | Después |
> |---|---|---|
> | Native VLAN de `0/3/3` | **1** | **102** |
> | VLAN 1 en el trunk | untagged | **tagged** |
> | Puerto del bridge `LAN-VLAN1` | `sfp-sfpplus3` | **`vlan1-olt`** |
> | `ether5`, `ether7`, 29 IPs, PPPoE | — | **sin cambios** |
>
> **Validado antes de ejecutar, sin impacto:**
> - VLAN 102 añadida a `0/3/3` como miembro no nativo; la VLAN 1 siguió siendo native.
> - Transporte tagged por `0/3/3` probado con `vlanif102` + `vlan102-test`: **5/5 ping, 0% pérdida**.
> - `native-vlan 3 vlan 1` (rollback OLT) ejecutado como no-op: sintaxis válida en el puerto 3.
> - `vlan1-rollback` probado en seco: **idempotente**.
>
> **Resultado verificado:**
>
> | Métrica | Antes | Después |
> |---|---|---|
> | MACs en el puerto de la OLT | 531 (`sfp-sfpplus3`) | **533** (`vlan1-olt`) |
> | MACs `ether5` / `ether7` | 200 / 12 | **200 / 12** |
> | ARP completas en `LAN-VLAN1` | 745 | **745** |
> | Ping a 8 subredes distintas | OK | **0% pérdida** |
> | Sesiones PPPoE activas | — | **50** |
> | VLAN 100 (`vlan100-olt`) | 436 ARP | **443 ARP**, sin regresión |
>
> `save configuration` aplicado: línea 253 del `display current-configuration` dice `native-vlan 3 vlan 102`.
>
> > Al comparar volcados de `display current-configuration`, contar **sin anclar** (`grep -oE 'service-port [0-9]+ vlan [0-9]+'`). El anclado `^ *service-port` da conteos falsos porque la CLI parte las líneas largas en puntos distintos en cada ejecución. Verificado: 1126 service-ports y 818 SN idénticos antes y después.
>
> ### Rollback
>
> ```text
> # OLT
> interface mcu 0/3
> native-vlan 3 vlan 1
>
> # MK2
> /system script run vlan1-rollback
> ```
>
> ### Diferencia crítica con el cutover de la VLAN 100
>
> Con la OLT en tagged, devolver el bridge a `sfp-sfpplus3` no sirve: el bridge tiene `vlan-filtering=false` y reenviaría las tramas etiquetadas de forma transparente, sin que la capa 3 de `LAN-VLAN1` las procese. **El rollback debe tocar los dos lados**, y por eso no puede delegarse en un scheduler del MK2.
>
> Por la misma razón **no hay make-before-break**: mientras `sfp-sfpplus3` sea un puerto activo del bridge, intercepta todas las tramas, incluidas las etiquetadas, y `vlan1-olt` nunca las vería.
>
> ### Métrica de salud
>
> No sirve contar ARP: las IPs viven en el bridge `LAN-VLAN1`, que no cambia. Hay que contar **MACs aprendidas por puerto** (`/interface bridge host`). Baseline: `sfp-sfpplus3` **529**, `ether5` 200, `ether7` 12.
>
> ### Alcance
>
> El bridge `LAN-VLAN1` es un dominio L2 compartido: la OLT, el switch SFP de 4 puertos (`ether5`) y el AirFiber de 9 de Octubre (`ether7`), más un servidor PPPoE con 123 secrets. Los gateways, el PPPoE y los otros puertos **no se tocan**; solo se sustituye qué puerto alimenta a la OLT.

**Objetivo:** que MK2 termine también el tráfico **VLAN 1** (legacy MK1) por `sfp-sfpplus3`, además del piloto **VLAN 100** en `sfp-sfpplus2`, para poder desconectar MK1 sin migrar ONU por ONU a VLAN 100.

**Fecha:** 2026-07-21  
**Equipo:** MK2 `CCR2116 NUEVO` — `38.224.231.4`  
**Estado 2026-07-21 (histórico):** `sfp-sfpplus3` untagged en el bridge entonces llamado `LAN_MK1`.  
**Estado vigente 2026-09-10:** `sfp-sfpplus3` solo transporte; `vlan1-olt` tagged en bridge **`LAN-VLAN1`**. Ver [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md).

---

## Topología objetivo

```text
MK2 CCR2116
├─ sfp-sfpplus1  → WAN Corp Tarazona (VLAN 450)     [YA OK]
├─ sfp-sfpplus2  → OLT piloto VLAN 100 untagged     [YA OK — 192.168.30.1/24]
└─ sfp-sfpplus3  → OLT / path VLAN 1 (legacy MK1)   [POR IMPLEMENTAR]
```

Regla: **un uplink físico por VLAN de servicio** (igual que hoy MK1=VLAN1 / MK2-piloto=VLAN100), pero ambos terminan en MK2.

---

## Fase A — Preparación (sin cortar clientes)

### A1. Físico / OLT

- [ ] Confirmar qué cable/SFP de MK1 (`sfp-sfpplus2` OLT) se moverá o se duplicará hacia MK2 `sfp-sfpplus3`.
- [ ] Verificar óptica/transceiver en `sfp-sfpplus3` (tipo, potencia).
- [ ] En OLT: confirmar que VLAN 1 del NNI/uplink llegará al puerto que alimenta `sfp-sfpplus3` (mismo path que hoy usa MK1, o trunk equivalente).
- [ ] Ventana de mantenimiento acordada (cutover del uplink VLAN 1).

### A2. Inventario desde MK1 (copiar, no inventar)

Ejecutar en MK1 y guardar salida:

```bash
/ip address print where interface=LAN
/queue simple print count-only
/ip firewall address-list print where list=deudores count-only
/interface pppoe-server server print
/ip firewall nat print
/ip firewall filter print
/ip route print where dst-address=0.0.0.0/0
```

Gateways VLAN 1 activos en MK1 (referencia jul 2026):

`192.168.22.1`, `192.168.21.x` (si existe), `192.168.25.1`, `192.168.26.1`, `192.168.93.1`, `192.168.88.1`, `192.168.20.1`, `192.168.9.1`, `192.168.95.1`, `192.168.33.1`, `192.168.49.1`, `192.168.55.1`, `192.168.0.1`, `192.168.1.1`, `192.168.100.1`, `192.168.200.1`, `192.168.201.1`, `192.168.210.1`, `192.168.211.1`, `192.168.220.1`, `192.168.221.1`, `192.168.123.1`, `192.168.44.1`, `192.168.175.1`, `192.168.168.1`, `192.168.99.1`, …

- [ ] Lista completa de `/ip address` en interface `LAN` de MK1 exportada a archivo.
- [ ] Conteo colas MK1 anotado.
- [ ] Lista `deudores` exportada (o plan de regenerarla desde ispAdmin tras cutover).

### A3. Preparar MK2 (config en frío, sin quitar MK1 aún)

**Importante:** no poner las mismas IPs gateway en MK2 **mientras MK1 sigue conectado al mismo L2**, o habrá **IP duplicada / ARP conflict**.

Opciones seguras:

1. **Preferida:** configurar bridge/VLAN/interface en MK2 sin IPs; agregar gateways **en el segundo del cutover** (después de bajar IPs en MK1 o desconectar su uplink).
2. Alternativa: IPs en MK2 en interfaz disabled hasta el cutover.

Checklist MK2 prep:

- [x] Interfaz `vlan1-olt` con `vlan-id=1` sobre `sfp-sfpplus3`.
- [x] Gateways MK1 en bridge `LAN_MK1` (26 IPs; antes en `vlan1-olt`).
- [ ] Confirmar WAN MK2 OK: `38.224.231.4/27`, default route `0.0.0.0/0 → 38.224.231.1` (ya existe).
- [ ] Confirmar NAT masquerade/src-nat para redes `192.168.0.0/16` (o equivalentes) hacia WAN.
- [x] Firewall VLAN1 (2026-07-21): reglas espejo del piloto VLAN100 sobre `vlan1-olt` (L3 de gateways):
  - `OLT VLAN1 forward` — `chain=forward accept in-interface=vlan1-olt`
  - `OLT VLAN1 return` — `chain=forward accept established,related out-interface=vlan1-olt`
  - `OLT VLAN1 input mgmt` — `chain=input accept in-interface=vlan1-olt`
- [x] No tocar `sfp-sfpplus2` / `192.168.30.1` (VLAN 100) al aplicar firewall VLAN1.

### A4. ispAdmin / BD (después del cutover L2, no antes del tráfico)

- [ ] Plan de UPDATE masivo: `host_device_id = 8` para abonados VLAN1 que queden en MK2.
- [ ] Reasignar `ip_pool.host_device_id` de pools MK1 → MK2 (`8`) **o** crear pools espejo en MK2.
- [ ] Decidir modelo de `network_device.vlan_id` para MK2: hoy `100`; con dual VLAN en el mismo router, altas nuevas deben elegir VLAN según puerto/pool (VLAN 100 vs VLAN 1). Documentar regla de negocio.
- [ ] Regenerar address-list `deudores` y colas desde ispAdmin apuntando a MK2.

---

## Fase B — Cutover (ventana corta)

Orden estricto:

1. [ ] Congelar cambios de altas/migraciones ONU durante la ventana.
2. [ ] En MK1: quitar IPs gateway de `LAN` **o** deshabilitar bridge port del uplink OLT (evitar IP duplicada).
3. [ ] Mover/conectar fibra uplink VLAN 1 a MK2 `sfp-sfpplus3`.
4. [ ] Verificar en MK2: `sfp-sfpplus3` en estado **R** (running).
5. [ ] Aplicar `/ip address add` de todos los gateways VLAN1 en `bridge-vlan1` (o interface elegida).
6. [ ] Verificar NAT + ruta default en MK2.
7. [ ] Prueba rápida:
   - [ ] `/ping` o ARP a 2–3 IPs de clientes VLAN1 conocidas.
   - [ ] ARP `reachable`/`complete` en MK2 para esas IPs.
   - [ ] Cliente de prueba navega (o bytes en queue si ya hay cola en MK2).
8. [ ] Migrar/recrear **simple queues** de MK1 → MK2 (export/import o regeneración ispAdmin).
9. [ ] Address-list `deudores` + regla drop en MK2.
10. [x] PPPoE wireless — sync MK1→MK2 (2026-07-21): pools + 8 profiles + 123 secrets; server `PPOE CLIENTES` en bridge **`LAN_MK1`** (enabled). Cutover físico: mover AirFiber/switches a `LAN_MK1` y disable server MK1 → [cutover-pppoe-wireless-mk2.md](./cutover-pppoe-wireless-mk2.md).
11. [x] SmartOLT CloudOLT en MK2 (2026-07-21): address-list + DNAT 2333/2322/2161 → OLT; IP `10.11.104.89` en `ether3`. Actualizar panel SmartOLT a `38.224.231.4` en cutover — [smartolt-cloudolt-mk2.md](./smartolt-cloudolt-mk2.md).
12. [ ] GRE VPS↔OLT: si se apaga MK1, recrear GRE (o equivalente) en MK2 antes de dar por cerrado.

Rollback rápido:

- [ ] Procedimiento escrito: reconectar uplink a MK1, reactivar IPs en MK1, quitar IPs VLAN1 de MK2.

---

## Fase C — Validación post-cutover

### C1. Red

- [ ] `sfp-sfpplus3` running.
- [ ] Gateways VLAN1 responden en MK2 (`/ip address print`).
- [ ] Muestra de clientes VLAN1: ARP OK + internet OK.
- [ ] Clientes VLAN100 (`192.168.30.x` en `sfp-sfpplus2`) siguen OK (no regresionados).
- [ ] WAN MK2 estable (ping `38.224.231.1`).
- [x] Policy IPs problemáticas (`toTarazona` / SNAT `8.243.126.161` / address-list 14 clientes) migrada a MK2; deshabilitada en MK1 (2026-08-19). IP principal MK2 `38.224.231.4` intacta. Scripts: `mikrotik-mk2-problematic-*.rsc`, `mikrotik-mk1-problematic-disable.rsc`.

### C2. ispAdmin

```sql
-- Tras reasignar host
SELECT host_device_id, COUNT(*) 
FROM subscription 
WHERE service_status <> 'CANCELLED' 
GROUP BY host_device_id;

SELECT id, ip_segment, host_device_id 
FROM ip_pool 
WHERE host_device_id IN (1,8) 
ORDER BY host_device_id, id;
```

- [ ] Abonados VLAN1 con `host_device_id=8`.
- [ ] Pools VLAN1 con `host_device_id=8`.
- [ ] Cortes/reactivaciones probados en un abonado de prueba (lista deudores en MK2).

### C3. Apagado MK1 (solo si C1+C2 OK)

- [ ] Desconectar uplink OLT de MK1.
- [ ] Desconectar o dejar MK1 solo para rollback temporal.
- [ ] Confirmar GRE/OLT mgmt operativo sin MK1 (o vía MK2).
- [ ] Monitoreo 24–48 h: CPU/RAM MK2, drops, quejas.

---

## Fase D — Dos caminos de migración de clientes (conviven)

| Camino | Cuándo | Qué tocar en ONU |
|--------|--------|------------------|
| **A. Uplink VLAN1 → MK2 `sfp-sfpplus3`** | Cutover masivo legacy | Nada (siguen VLAN 1 / misma IP) |
| **B. Migración a VLAN 100** | Piloto / nuevos / limpieza | WAN ONU VLAN 100 + IP `192.168.30.x` + SmartOLT `update_main_vlan` |

Piloto VLAN100 ya hecho: suscripción `629` → `192.168.30.36` (ver `migracion-piloto-vsolva74-629.md`).

---

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| IP gateway duplicada MK1+MK2 en mismo L2 | Bajar IPs MK1 antes de subirlas en MK2 |
| `sfp-sfpplus3` sin link | Validar SFP/fibra/OLT NNI antes de la ventana |
| Olvidar NAT en MK2 | Probar salida a internet de un cliente VLAN1 al minuto 0 |
| Colas solo en MK1 | Export/import o regenerar; sin cola no hay shape |
| Perder GRE al apagar MK1 | Migrar GRE a MK2 antes del apagado definitivo |
| `vlan_id=100` fijo en BD para MK2 | Definir regla de altas (VLAN1 vs 100) al tener dual uplink |

---

## Comandos de verificación rápida (MK2)

```bash
/interface print where name~"sfp-sfpplus"
/interface print stats where name=sfp-sfpplus3
/interface vlan print where name=vlan1-olt
/ip address print where interface=LAN-VLAN1
/ip arp print where interface=LAN-VLAN1
/ip firewall filter print where comment~"OLT VLAN1"
/ping 192.168.22.1 count=2
/ping 38.224.231.1 count=5
/queue simple print count-only
```

---

## Criterio de terminado

- [ ] VLAN 1 operativa en MK2 `sfp-sfpplus3` con gateways legacy.
- [ ] Clientes VLAN1 con internet sin MK1 en el path.
- [ ] VLAN 100 intacta en `sfp-sfpplus2`.
- [ ] BD reconciliada (`host_device_id` / `ip_pool`).
- [ ] Documentación de arquitectura actualizada.
- [ ] MK1 desconectado o en standby de rollback.
