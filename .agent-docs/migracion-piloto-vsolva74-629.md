# Migración piloto VSOLVA74 — suscripción 629

**Fecha:** 2026-07-21  
**Estado:** OK (validado por ARP + HTTP desde MK2 + tráfico en queue; ICMP bloqueado en WAN ONU)

## Cliente

| Campo | Antes | Después |
|-------|-------|---------|
| Suscripción | 629 | 629 |
| Nombre | EUGENIA HONORIO BAZAN | igual |
| ONU SN | VSOL0086BDD9 | igual |
| Modelo | VSOLVA74 | igual |
| IP | 192.168.95.233 | **192.168.30.36** |
| Gateway | 192.168.95.1 | **192.168.30.1** |
| VLAN ONU/OLT | 1 | **100** |
| host_device_id | 1 (MK1) | **8 (MK2)** |
| ip_pool_id | NULL | **8** (`192.168.30.1/24`) |

## Pasos ejecutados

1. Confirmado `ip_pool` id=8 en prod (`192.168.30.1/24`, host MK2).
2. Reserva IP: última simple-queue MK2 `192.168.30.35` + 1 → `192.168.30.36`.
3. Creada cola en MK2: `id:629, usuario:EUGENIA HONORIO BAZAN, plan:duo_basico 80, tipo:FIBER` target `192.168.30.36/32` max-limit 200M/200M.
4. ONU web (Cursor browser) en IP vieja: Network → WAN → perfil `1_INTERNET_R_VID_1` → VLAN 100, IP `.36`, gateway `.1` → **Submit**.
5. SmartOLT: `POST onu/update_main_vlan/VSOL0086BDD9` con `vlan=100` (service-port 677).
6. Reboot ONU vía SmartOLT (recuperó Online tras LOS breve).
7. BD prod actualizada: `ip`, `ip_pool_id=8`, `host_device_id=8`.
8. MK1: eliminada cola residual `target=192.168.95.233/32`.

## Validación

| Check | Resultado |
|-------|-----------|
| SmartOLT status | Online, vlan=100 |
| ARP MK2 `192.168.30.36` | reachable (MAC `B4:64:15:86:BD:E2`) |
| HTTP desde MK2 `/tool fetch http://192.168.30.36/` | **200**, 3 KiB |
| Tráfico queue MK2 | bytes reais (ej. ~95 KB / ~739 KB) rate activo |
| Ping ICMP desde MK2 | **falla** (ONU bloquea ping WAN; otros hosts MK2 sí responden) |

## Notas para el lote

- Tras Submit ONU, actualizar VLAN OLT con SmartOLT `onu/update_main_vlan/{external_id}` **antes o justo después**; si no, el cliente queda offline.
- No usar `onu/disable` en probes.
- Validación de cierre: preferir **ARP reachable + HTTP 200 desde MK2 + bytes en queue** si ICMP está bloqueado; documentar `ping_ok=false` cuando aplique.
- Siguiente IP de reserva: recalcular desde última simple-queue MK2 (`+1`).
