# CRS112 SWICH 4 SFP — loop L2 en sfp10 (MK2 ether5)

Fecha diagnóstico: 2026-07-22  
Acceso: mac-telnet desde MK2 `38.224.231.4` → `C4:AD:34:91:C6:37` (SSH/API a `192.168.1.144` cuelga)

## Equipo

| Campo | Valor |
|-------|--------|
| Identity | `SWICH 4 SFP` |
| Modelo | CRS112-8G-4S |
| Serial | `94DA0B58F0A6` |
| ROS | 6.44.5 (long-term) |
| Bridge MAC / admin-mac | `C4:AD:34:91:C6:37` |
| IP mgmt | `192.168.1.144/24` (+ defconf `192.168.188.1/24`) |
| Uptime al diagnóstico | ~1w5d |

## 1. Confirmación del loop

**Confirmado en `sfp10`.**

Logs recurrentes en el CRS112:

```text
sfp10: bridge port received packet with own address as source address (c4:ad:34:91:c6:37), probably loop
sfp10: bridge port received packet with own address as source address (c4:ad:34:91:c6:40), probably loop
```

- `c4:ad:34:91:c6:37` = MAC del bridge (admin-mac)
- `c4:ad:34:91:c6:40` = MAC local de `sfp10`

En MK2, mismo dominio L2:

```text
ether5: bridge RX looped packet - MAC 04:f4:1c:f1:76:dc
```

(`04:F4:1C:F1:76:DC` = root-bridge visto desde el CRS = bridge LAN_MK1 de MK2)

Reloj del CRS en 1974 (sin NTP): irrelevante para el loop.

## 2. Topología de puertos del bridge CRS112

Un solo bridge `bridge`, `protocol-mode=rstp`, `vlan-filtering=no`, `horizon=none` en todos los puertos, `hw=yes`.

### Puertos activos (Running)

| Puerto | Comment | MAC | Rol RSTP (inferido) | Hosts aprendidos |
|--------|---------|-----|---------------------|------------------|
| **ether2** | PUERTO PARA JARA FUNDO | `…:C6:38` | **root-port** (uplink MK2) | ~318 |
| ether7 | SALIDA CLIENTES SAN GERONIMO | `…:C6:3D` | designated | ~13 |
| sfp9 | *(sin comment)* | `…:C6:3F` | designated | ~86 |
| **sfp10** | **torre san geronimo** | `…:C6:40` | designated | ~44 |
| sfp11 | *(sin comment)* | `…:C6:41` | designated | ~33 |
| sfp12 | *(sin comment)* | `…:C6:42` | designated | ~32 |

### Puertos inactivos (cable down)

| Puerto | Comment |
|--------|---------|
| ether1 | FUNDO SAN CARLOS |
| ether3 | AIRFIBER 99 |
| ether4 | AIRFIBER 100 |
| ether5 / ether6 / ether8 | sin comment |

### Uplink a MK2

```text
MK2 ether5  «SWITCH SFP 4 PUERTOS»  ↔  CRS112 ether2
```

- Neighbor MK2: `192.168.1.144` / `C4:AD:34:91:C6:37` (+ `…:C6:38`) en `ether5` / `LAN_MK1`
- Neighbor CRS: `192.168.22.1` / `04:F4:1C:F1:76:DD` en `ether2`
- RSTP en CRS: `root-bridge=no`, `root-bridge-id=0x8000.04:F4:1C:F1:76:DC`, `root-port=ether2`, `root-path-cost=10`, **`designated-port-count=5`** → **ningún puerto bloqueado**

### MK2 LAN_MK1 (contexto del anillo)

Puertos activos en `LAN_MK1` al momento del diagnóstico:

- `ether5` — SWITCH SFP 4 PUERTOS (este CRS112)
- `ether7` — 9 OCTUBRE AIRFIBER
- `ether8`
- `sfp-sfpplus3` — OLT legacy uplink

## 3. Hipótesis del path redundante

### Path que cierra el anillo (más probable)

```text
MK2 LAN_MK1 ──ether5──► CRS112 ether2 ──bridge──► CRS112 sfp10 («torre san geronimo»)
    ▲                                                      │
    │                                                      ▼
    └──── (retorno L2 vía radio/switch en San Gerónimo / otro uplink LAN_MK1,
           p.ej. AirFiber ether7 u otro enlace que no habla RSTP) ────────┘
```

Evidencia:

1. El CRS solo reporta el loop **entrando por `sfp10`** (retorno del frame con SA = MAC propia).
2. MK2 reporta RX looped en **`ether5`** con su propia MAC de bridge.
3. RSTP **no bloquea** `sfp10` (`designated-port-count=5`): el camino alterno casi seguro pasa por equipos Ubiquiti/bridge transparentes que **no reenvían BPDUs** (OUI frecuentes en vecinos de `sfp10`: `E0:63:DA`, `F0:9F:C2`, `74:AC:B9`, `80:2A:A8`, `18:E8:29`).
4. Los 24/24 MAC vecinos vistos en `sfp10` también aparecen aprendidos/vecinos por MK2 `ether5` (coherente con dominio L2 único + flood; el síntoma decisivo sigue siendo el “own address as source” en `sfp10`).

### Path local secundario (menos probable como único)

`ether7` («SALIDA CLIENTES SAN GERONIMO») + `sfp10` («torre san geronimo») podrían formar anillo local en el sitio SG. `ether7` tiene pocos hosts (~13) frente a `sfp10` (~44); no explica solo el RX looped de MK2 en `ether5`, pero puede participar.

### Qué no es

- CRS310 `38.224.231.8` (uplink WAN): **no** es este switch ni `ether5`.
- Fecha 1974 del CRS: cosmético / NTP ausente.

## 4. Acción recomendada

### Inmediata (prueba segura, reversible) — PROPUESTA, no aplicada

Deshabilitar temporalmente el puerto donde vuelve el loop:

```routeros
/interface ethernet disable sfp10
```

Verificar ~1–2 min:

- En CRS: dejan de aparecer logs `probably loop` en `sfp10`
- En MK2: dejan de aparecer `ether5: bridge RX looped packet`
- Impacto esperado: clientes/APs de **torre San Gerónimo** por esa fibra. Si hay path alterno (p.ej. vía `ether7` u otro SFP), el tráfico puede sobrevivir; si no, cae solo ese ramal.

Rehabilitar si hace falta:

```routeros
/interface ethernet enable sfp10
```

No se aplicó el disable en este diagnóstico: `sfp10` es uplink de torre con ~44 hosts; no está demostrado que sea 100 % redundante con `ether7` sin prueba controlada.

### Mitigaciones permanentes (elegir según impacto)

1. **Cortar el enlace redundante físico/lógico** una vez identificado el segundo path (ideal: mapa de fibra/radio SG ↔ MK2 AirFiber / otro switch).
2. **Horizon split** en CRS si se confirma que `ether2` y `sfp10` no deben verse entre sí a L2:
   ```routeros
   /interface bridge port set [find interface=ether2] horizon=1
   /interface bridge port set [find interface=sfp10] horizon=1
   ```
   (rompe forwarding entre esos puertos; validar que no sea el diseño deseado).
3. **RSTP efectivo end-to-end**: subir `path-cost` en `sfp10` y asegurar que el equipo remoto participe en STP/RSTP (radios bridge suelen anular RSTP).
4. **Loop-protect** en CRS `sfp10` / MK2 `ether5` como red de seguridad (no sustituye cortar el anillo).
5. Corregir comment de `ether2` (hoy dice «JARA FUNDO» pero es el uplink a MK2).

### Comando de monitoreo post-cambio

```routeros
# CRS112
/log print where message~"loop"
/interface bridge monitor bridge once

# MK2
/log print where message~"RX looped"
```

## 5. Datos recolectados (sesión)

- `/system identity|resource print`
- `/interface print terse`, ethernet terse, bridge port terse
- `/interface bridge monitor bridge once` (root = MK2, root-port = ether2)
- `/ip neighbor print` (por puerto)
- `/interface bridge host print` (conteos y muestras sfp10/ether2)
- `/log print where message~"loop"`
- Inicio de `/export hide-sensitive` (comments + bridge admin-mac)

Acceso operativo: mac-telnet (no `interface=`). SSH/API TCP a `192.168.1.144` no usable desde fuera.
