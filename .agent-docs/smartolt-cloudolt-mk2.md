# SmartOLT / CloudOLT en MK2

**Fecha:** 2026-07-21  
**Origen:** reglas MK1 `38.224.231.2`  
**Destino:** MK2 `38.224.231.4`

## Qué hace

SmartOLT (cloud `amz.smartolt.com`) gestiona la OLT `10.11.104.2` vía **dst-nat** en el MikroTik público. Solo IPs de la address-list `CloudOLT`.

| Puerto público | Proto | Destino OLT | Servicio |
|----------------|-------|-------------|----------|
| `2333` | TCP | `10.11.104.2:23` | Telnet |
| `2322` | TCP | `10.11.104.2:22` | SSH |
| `2161` | UDP | `10.11.104.2:161` | SNMP |

## Aplicado en MK2

| Ítem | Valor |
|------|--------|
| IP mgmt OLT | `10.11.104.89/24` en `ether3` (MK1 sigue con `.88`) |
| Address-list | `CloudOLT` → `amz.smartolt.com` (+ IPs dinámicas resueltas) |
| NAT | 3 reglas `dst-address=38.224.231.4` comment `CloudOLT` |
| Filter | `forward accept connection-nat-state=dstnat` comment `CloudOLT forward to OLT` |
| Ping OLT | OK desde MK2 → `10.11.104.2` |

## Cutover SmartOLT

1. En el panel SmartOLT, cambiar la IP de acceso OLT de `38.224.231.2` a **`38.224.231.4`** (mismos puertos 2333/2322/2161).
2. Validar ONUs online en SmartOLT.
3. Opcional: deshabilitar reglas CloudOLT en MK1 cuando ya no se usen.

## Rollback

- Reapuntar SmartOLT a `38.224.231.2`.
- En MK2: `/ip firewall nat disable [find comment="CloudOLT"]` si hace falta.

## Relacionado

- [arquitectura-red-dual-mikrotik.md](./arquitectura-red-dual-mikrotik.md)
- GRE VPS (gestión propia, distinto de SmartOLT cloud): `gre-ispadmin-vps` en MK1
