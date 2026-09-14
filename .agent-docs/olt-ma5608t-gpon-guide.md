# Guía GPON — Huawei MA5608T (Gigafiber)

Documentación de referencia para reemplazar SmartOLT con **olt-gateway** propio. Basada en inventario real de la OLT, documentación oficial Huawei y guía práctica de configuración GPON.

| Campo | Valor |
|-------|-------|
| Modelo | **MA5608T** |
| Firmware | **MA5600V800R015C00** (patch SPH106 HP1013) |
| IP gestión | **10.11.104.2** (red interna) |
| Prompt CLI | `MA5608T>` / `MA5608T#` |
| Gateway LAN | Mikrotik **GIGAFIBER 1036** — `ether3` = `10.11.104.88/24` |

## Hardware (slots)

| Slot | Tarjeta | Uso |
|------|---------|-----|
| 0 | H805GPFD | GPON → `interface gpon 0/0` |
| 1 | H806GPFD | GPON → `interface gpon 0/1` |
| 3 | H801MCUD1 | Control (activa) |

## Perfiles GPON activos (inventario parcial)

| Perfil | ONUs aprox. |
|--------|-------------|
| `Generic_1_V1` | 522 |
| `line-profile_10` | 182 |
| `SMARTOLT_FLEXIBLE_GPON` | 50 |

> Antes de autorizar ONUs, verificar IDs reales con `display ont-lineprofile gpon all` y `display ont-srvprofile gpon all`.

---

## Fuentes de documentación

### Oficial Huawei

| Documento | Contenido | Enlace |
|-----------|-----------|--------|
| **SmartAX MA5608T Support** | Portal principal: Configuration Guide, Command Reference, GPON Feature Guide | https://support.huawei.com/enterprise/en/access-network/smartax-ma5608t-pid-9121152 |
| **SmartAX MA5600T Support** | Misma familia CLI (MA5600T/MA5603T/MA5608T), Commissioning Command Reference | https://support.huawei.com/enterprise/en/access-network/smartax-ma5600t-pid-18133 |
| **Configuration Manual** | Manual completo FTTH/FTTB/voz (~1979 págs) | https://www.manualslib.com/products/Huawei-Smartax-Ma5608t-6924610.html |
| **Commissioning and Configuration Guide** | Flujo GPON: perfiles, ONT, service-port (serie V800R011+) | PDF en portal Huawei / mirrors por versión |

Firmware instalado: **V800R015C00**. Usar documentación de la serie **MA5600T&MA5603T&MA5608T V800R015–R019** (misma familia CLI; cambios menores entre patches).

### Guía práctica (referencia principal operativa)

**Huawei OLT-MA5608T-GPON Configuration Practice** — CEI Technology / ceitatech:

https://www.ceitatech.com/news/huawei-olt-ma5608t-gpon-configuration-practice/

Cubre de forma secuencial:

1. IP de gestión y usuario admin con Reenter > 1
2. VLAN de servicio y puerto upstream
3. DBA profile
4. ont-lineprofile (TCONT, GEM, VLAN mapping)
5. ont-srvprofile (puertos ETH/POTS)
6. Registro ONU (SN y LOID)
7. ont port native-vlan
8. service-port
9. Registro batch y borrado (orden service-port → ont delete)

### Otras referencias útiles

| Recurso | URL |
|---------|-----|
| How to add an ONT (Telecomate) | https://www.telecomate.com/how-to-add-an-ont-to-an-olt/ |
| Configuración rápida MA5608T (Batna24) | https://www.batna24.com/gb/blog/how-to-quickly-configure-the-olt-huawei-ma5608t-with-ont-huawei |
| GPON ONT distributed mode (Thunder-link) | https://jornathunderlinkcom.wordpress.com/2017/01/06/configuring-a-gpon-ont-distributed-mode/ |

---

## Acceso SSH

### Credenciales actuales

| Campo | Valor |
|-------|-------|
| Usuario super | `root` |
| Password super | `admin` |
| Reenter `root` | **1** (fijo, no modificable) |

### Usuario gateway (creado 2026-07-16)

Usuario dedicado para **olt-gateway** y automatización. Preferir este sobre `root`.

| Campo | Valor |
|-------|-------|
| Usuario | `oltadmin` |
| Password | `GigaOlt2026` (cambiar en producción; pasar vía `OLT_GATEWAY_PASSWORD`) |
| Nivel | Administrator (3) |
| Profile | `root` |
| Reenter | **4** (máximo Huawei: 0–4) |
| Appended info | `olt-gateway` |
| Estado al crear | Offline |

Verificación: `display terminal user all` muestra `oltadmin` con Reenter Num = 4.

**Uso exclusivo del sistema:** `oltadmin` es solo para olt-gateway. El gateway usa **1 sesión SSH** serializada (`OltCliBus`, `pool-size=1`). `max_concurrent_cli_sessions=4` es el Reenter teórico de la OLT; el resto queda libre para operadores (`root`/Admin). Detalle: [olt-gateway-cli-bus.md](./olt-gateway-cli-bus.md), [olt-gateway-3layer.md](./olt-gateway-3layer.md).

Login SSH:

```bash
ssh -o KexAlgorithms=diffie-hellman-group-exchange-sha1 \
    -o HostKeyAlgorithms=ssh-rsa \
    -o PubkeyAcceptedKeyTypes=ssh-rsa \
    -o Ciphers=aes128-cbc \
    oltadmin@10.11.104.2
```

Env para el backend (`application-dev.properties`):

```bash
export OLT_GATEWAY_USER=oltadmin
export OLT_GATEWAY_PASSWORD='GigaOlt2026'
export OLT_GATEWAY_MOCK_ENABLED=false
```

> Reservar `root` para consola/emergencias. En la OLT ya existen otros Admin con Reenter 4 (`gigafiber2025`, `gigafiber3031`, etc.) usados por SmartOLT; `oltadmin` es el de la plataforma propia.

### SSH directo (algoritmos legacy)

```bash
ssh -o KexAlgorithms=diffie-hellman-group-exchange-sha1 \
    -o HostKeyAlgorithms=ssh-rsa \
    -o PubkeyAcceptedKeyTypes=ssh-rsa \
    -o Ciphers=aes128-cbc \
    root@10.11.104.2
```

### Vía Mikrotik

Desde la LAN / consola MK1:

```bash
ssh gigafiber2023@38.224.231.2
/system ssh 10.11.104.2 user=root
```

### VPS ispAdmin → OLT (GRE + enrutamiento MK1)

Aplicado **2026-07-20**. MK1 solo enruta; no hay DST-NAT de puerto público.

| Campo | Valor |
|-------|-------|
| Túnel | GRE `gre-mk1` / `gre-ispadmin-vps` (`10.255.255.0/30`) |
| SSH destino | **`oltadmin@10.11.104.2:22`** |
| SNAT | origen túnel → `10.11.104.88` hacia LAN OLT |
| Scripts | `mikrotik-mk1-olt-vps-gre.rsc`, `setup-vps-olt-gre.sh`, `vps-olt-gre.service` |

```bash
ssh \
  -o KexAlgorithms=+diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1 \
  -o HostKeyAlgorithms=+ssh-rsa \
  -o PubkeyAcceptedAlgorithms=+ssh-rsa \
  -o Ciphers=aes128-cbc \
  oltadmin@10.11.104.2
```

Detalle: [olt-vps-ssh-nat-build.md](./olt-vps-ssh-nat-build.md)

### Preparación de sesión (evitar paginación)

En SmartAX MA5608T **`screen-length` no aplica** (o no existe). La forma verificada en vivo para volcar **toda** la salida sin `---- More ----` es:

```text
enable
config
mmi-mode enable
quit
```

| | |
|--|--|
| Fuente | [Disabling paging in Huawei (Stack Exchange)](https://networkengineering.stackexchange.com/questions/42438/disabling-paging-in-huawei) — respuesta: *MA5608T model has no command 'screen-length'… use `mmi-mode enable`* |
| Qué hace | *machine-machine interaction mode*: la CLI no pagina con `---- More ----` |
| Validación Gigafiber | `GET /onus` ~758 ONUs: slot-all slot 0 + port-all slot 1 (~85 s primera vez; ~25 s con cache) |
| Implementación | `HuaweiCliSession.prepareSession()` en olt-gateway (cada connect/reconnect) |
| No usar | `scroll 512` / `screen-length 0 temporary` en esta OLT |

**Cuidado:** no ejecutar `mmi-mode ?` justo antes de `mmi-mode enable`: el `?` deja el CLI esperando parámetro y el siguiente comando se interpreta mal.

Fallback en el gateway: si aún aparece More, se envía espacio y se detecta el prompt en la cola del buffer (`HuaweiCliPromptDetector`).

Detalle API/sesión: [olt-gateway-read-mvp.md](./olt-gateway-read-mvp.md) — **Paginación CLI y mmi-mode**.

---

## Jerarquía de modos CLI

```text
MA5608T>                              Usuario (display básico)
MA5608T#                              enable — lectura ampliada
MA5608T(config)#                      config — configuración global
MA5608T(config-if-gpon-0/X)#          interface gpon 0/X
MA5608T(config-gpon-lineprofile-N)#   ont-lineprofile gpon profile-id N
MA5608T(config-gpon-srvprofile-N)#    ont-srvprofile gpon profile-id N
```

| Regla | Detalle |
|-------|---------|
| Lectura | Comandos `display ...` en modo `#` |
| Escritura | Requiere `config` y navegación al submodo |
| Perfiles | `commit` obligatorio en line/srv profile antes de `quit` |
| Salida | `quit` sube un nivel |

---

## Notación de sintaxis Huawei

| Símbolo | Significado |
|---------|-------------|
| `<K>` | Keyword obligatorio |
| `<U>` | Valor numérico |
| `<S>` | String |
| `{ a \| b }` | Elegir una opción |
| `[opt]` | Parámetro opcional |
| `F/S/P` | Frame/Slot/Port — ej. `0/1/0` = slot 1, puerto PON 0 |

En Gigafiber:

- Slot **0** → `interface gpon 0/0`
- Slot **1** → `interface gpon 0/1`
- Dirección GPON completa: `gpon 0/1/0` = slot 1, PON 0, ONT id 0

---

## Modelo de configuración GPON (Profile Mode)

Flujo usado por SmartOLT y recomendado para olt-gateway:

```text
dba-profile
    ↓
ont-lineprofile (TCONT + GEM + VLAN mapping)
    ↓
ont-srvprofile (puertos ONT)
    ↓
interface gpon + port ont-auto-find enable
    ↓
ont confirm / ont add
    ↓
service-port
```

### 1. Perfil DBA (ancho de banda upstream)

```text
config
dba-profile add profile-id 100 type3 assure 102400 max 1024000
display dba-profile all
```

### 2. Perfil de línea

```text
ont-lineprofile gpon profile-id 100
  tcont 1 dba-profile-id 100
  gem add 0 eth tcont 1
  gem mapping 0 1 vlan 101
  commit
  quit
display ont-lineprofile gpon all
display ont-lineprofile current
```

Reglas GEM mapping (guía práctica ceitatech):

- Un GEM port puede mapear múltiples VLANs (índice mapping distinto)
- Un índice mapping puede repetirse en distintos GEM ports
- Una VLAN solo puede mapearse en un GEM port
- Máximo 7 VLAN mappings por gemport

### 3. Perfil de servicio

```text
ont-srvprofile gpon profile-id 100
  ont-port eth 1
  port vlan eth 1 101
  commit
  quit
display ont-srvprofile gpon all
display ont-srvprofile current
```

### 4. Interfaz GPON + autofind

```text
interface gpon 0/1
  port 0 ont-auto-find enable
  display ont autofind 0
  display ont autofind all
```

### 5. Autorizar ONU (reemplazo SmartOLT `authorize`)

**Desde autofind (recomendado):**

```text
interface gpon 0/1
ont confirm 0 ontid 0 sn-auth 4857544311E70E9A omci \
  ont-lineprofile-id 10 ont-srvprofile-id 10 desc cliente_1
```

**Registro offline:**

```text
ont add 0 0 sn-auth ZTEG00000001 omci \
  ont-lineprofile-id 100 ont-srvprofile-id 100
```

**Batch (mismo perfil):**

```text
ont confirm 0 all sn-auth omci ont-lineprofile-id 10 ont-srvprofile-id 10
```

Modos de autenticación: `sn-auth`, `loid-auth`, `password-auth`, `mac-auth`.  
Gestión: `omci` (ONT residencial GPON) o `snmp` (MDU).

### 6. VLAN nativa del puerto ONT

```text
ont port native-vlan 0 0 eth 1 vlan 101
```

### 7. Service port

```text
service-port vlan 100 gpon 0/1/0 ont 0 gemport 0 multi-service user-vlan 101
display service-port all
```

`user-vlan` debe coincidir con el VLAN del gem mapping en el line profile.

### 8. Eliminar ONU

Orden obligatorio:

```text
undo service-port vlan 100 gpon 0/1/0 ont 0 gemport 0
interface gpon 0/1
  ont delete 0 0
```

Algunos comandos piden confirmación `(y/n)[n]:`.

### 9. Reiniciar ONU

```text
interface gpon 0/1
  ont reset 0 0
```

---

## Comandos de lectura (MVP olt-gateway)

Implementación REST: [olt-gateway-read-mvp.md](./olt-gateway-read-mvp.md) — paquete `com.dscorp.wispadmin.oltgateway`, Postman en `postman/olt-gateway-read-mvp.json`.

| Endpoint propuesto | Comando OLT |
|--------------------|-------------|
| `GET /health` | Sesión SSH activa |
| `GET /olt/info` | `display version`, `display board 0` (tabla chassis frame 0; en V800R015 `display board 1+` → Parameter error). Parser: `BoardParser.parseAll` |
| `GET /onus/autofind` | `display ont autofind all` |
| `GET /onus` | Descubre slots GPON; slot-all por slot con fallback port-all. Gigafiber: ~758 ONUs |
| `GET /onus/by-sn/{sn}` | `display ont info by-sn {sn}` |
| `GET /onus/{...}/optical` | `display ont optical-info {port} {ont-id}` |
| Signal poll (bulk, no REST aún) | Dentro de `interface gpon 0/{slot}`: `display ont optical-info {port} all` (~3–15 s/puerto). Parser: `OpticalInfoParser.parseAll` + `oltRxPowerDbm`. Categoría: `SignalCategoryCalculator` (ONU Rx). Ver [olt-gateway-optical-signal-parser.md](./olt-gateway-optical-signal-parser.md) |
| Perfiles | `display ont-lineprofile gpon all`, `display ont-srvprofile gpon all` |

> **Nota by-sn:** el CLI MA5608T solo acepta `sn-value` (12–16 chars). **No** añadir `all` — provoca `% Too many parameters`.

> **Nota optical bulk:** no existe óptica chassis-wide. Bulk óptimo = `{port} all` por puerto GPON. SmartOLT “Signal” ≈ columna **OLT Rx ONT power(dBm)**.

Comandos adicionales útiles (guía práctica):

```text
display port info 0
display port ont-register-info all
display port state all
display ont info 0 all
display ont info 0 0
display current-configuration
display terminal user all
```

> Inventory live: `display ont info 0 <slot> all` por slot; si timeout → `display ont info 0 <slot> <port> all`. `display ont info 0 all` (frame completo) puede exceder 180 s en slots grandes. `display ont info all` (sin frame) → `% Parameter error`.

---

## Mapeo SmartOLT → CLI Huawei

Backend actual (`RealOltService`) usa 6 operaciones SmartOLT:

| SmartOLT API | CLI MA5608T |
|--------------|-------------|
| `onu/unconfigured_onus` | `display ont autofind all` |
| `onu/get_onus_details_by_sn/{sn}` | `display ont info by-sn {sn}` |
| `onu/authorize_onu` | `ont confirm ... sn-auth ... omci ont-lineprofile-id X ont-srvprofile-id Y` |
| `onu/move/{sn}` | `ont delete` + `ont confirm/add` en nuevo puerto/slot |
| `onu/delete/{id}` | `undo service-port ...` → `ont delete` |
| `onu/reboot/{id}` | `ont reset {port} {ont-id}` |

---

## Formato GPON SN

Display puede mostrar: `HDVG290A4D77`  
Registro interno (16 hex): `48445647290A4D77`

Conversión: cada carácter ASCII → 2 dígitos hex (ej. H=48, D=44, V=56, G=47).

---

## Convenciones y trampas

| Tema | Detalle |
|------|---------|
| `commit` | Obligatorio en ont-lineprofile y ont-srvprofile |
| `undo` | Niega comando previo (`undo service-port ...`) |
| Reenter | `root` = 1 sesión; error `"Reenter times have reached the upper limit"` |
| Borrado ONU | Siempre eliminar service-port antes de `ont delete` |
| Confirmaciones | `ont delete all`, `undo service-port` masivo pueden pedir `y` |
| Paginación | Preferir `mmi-mode enable`; sin eso, `---- More ----` y scripts/expect hacen timeout |
| Perfiles SmartOLT | Usar IDs existentes en OLT, no asumir 100/10 genéricos |
| TR-069 | Soportado en line profile; requiere ACS propio |

---

## Usuario admin recomendado (Reenter 4)

**Ya creado:** `oltadmin` (ver sección Acceso SSH arriba).

Comando usado:

```text
config
terminal user name
  User Name: oltadmin
  User Password: ********
  User profile name: root
  User's Level: 3
  Permitted Reenter Number: 4
  User's Appended Info: olt-gateway
display terminal user all
```

Resultado: `Adding user successfully`. Reenter = 4 (máximo del sistema).

**Política gateway:** `OltCliBus` abre **1 sola sesión SSH** (pool-size=1). Inventory, signal_poll, adhoc y writes compiten en cola. El Reenter restante queda libre para operadores humanos/ops. Ver [olt-gateway-cli-bus.md](./olt-gateway-cli-bus.md).

### Signal poll óptico (bulk)

No existe óptica chassis-wide. Bulk óptimo por puerto, dentro de `interface gpon 0/{slot}`:

```text
interface gpon 0/0
display ont optical-info 0 all
display ont optical-info 1 all
...
quit
```

SmartOLT “Signal” ≈ columna **OLT Rx ONT power**. Categoría en gateway usa **ONU Rx** (`good` ≥ −25, `warning` ≥ −27, `critical` &lt; −27). Detalle parser: [olt-gateway-optical-signal-parser.md](./olt-gateway-optical-signal-parser.md).

---

## Estructura sugerida para olt-gateway

```text
OltSession
  ├── connect(host, user, password)
  ├── enterEnable()
  ├── query: DisplayCommands       # solo display en modo #
  └── mutate: ConfigCommands       # config → gpon → ont → quit
        ├── authorizeOnu(sn, lineProfileId, srvProfileId, slot, port, ontId)
        ├── deleteOnu(f/s/p, ontId)    # service-port primero
        └── rebootOnu(port, ontId)
```

Organizar por capas de modo CLI, no por strings sueltos. Validar `F/S/P/port/ontid` antes de enviar.

---

## Scripts de exploración CLI

| Script | Propósito |
|--------|-----------|
| `scripts/olt-cli-explore.expect` | Exploración `?` en una sola sesión SSH |
| `scripts/olt-cli-to-markdown.py` | Genera salida raw → `.agent-docs/olt-ma5608t-cli-explore-output.md` |

Salida raw temporal: `/tmp/olt-cli-explore-raw.txt`

Reglas de ejecución:

- Una sola sesión SSH por corrida
- No paralelizar con sesión manual de `root`
- `pkill -f olt-cli-explore` antes de reintentar

---

## Infraestructura relacionada

| Componente | Detalle |
|------------|---------|
| Mikrotik CCR | CCR1036-8G-2S+, RouterOS 6.48.6, IP `38.224.231.2` |
| VPS backend | `212.85.13.47` — `api.gigafiberperu.cloud` |
| OLT en internet | No expuesta; solo red privada `10.11.104.0/24` |
| Conectividad VPS↔OLT | IPsec site-to-site, OLT Gateway en LAN, o mesh VPN (Tailscale/ZeroTier) |

---

## Referencias cruzadas

- Salida exploración CLI (`?`): [olt-ma5608t-cli-explore-output.md](./olt-ma5608t-cli-explore-output.md) (generado por script)
- Mock OLT backend: [OLT_MOCK_README.md](../OLT_MOCK_README.md)
- Interfaz servicio: `OltService.kt`, `RealOltService.kt`
