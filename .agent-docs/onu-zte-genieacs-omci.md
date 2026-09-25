# Configuración de una ONU ZTE F6600R para GenieACS mediante OMCI

Procedimiento validado en una Huawei MA5608T R015 para la ONU ZTE con serial
`ZTEGDC47BFFD`.

## Parámetros del caso validado

| Parámetro | Valor |
|---|---|
| OLT | Huawei MA5608T R015 |
| Frame/slot/PON | `0/1/6` |
| Interfaz GPON | `gpon 0/1` |
| ONT-ID | `16` |
| Serial | `ZTEGDC47BFFD` |
| Perfil de línea | `30` — `GF_V100_M1000_TR069` |
| Perfil de servicio | `11` — `F6600RV9.0.21` |
| VLAN de gestión/TR-069 | `1000` |
| GEM de gestión | `2` |
| Perfil TR-069 | `2` — perfil GenieACS vigente |

El perfil de línea 30 debe tener TR-069 habilitado, GEM 1 para VLAN 100 y GEM 2
para VLAN 1000. El perfil TR-069 debe apuntar al endpoint vigente de GenieACS
del entorno; no se deben copiar contraseñas al repositorio.

## Regla de plataforma: perfil genérico para ZTE F6600R

La plataforma debe autorizar las ONU ZTE F6600R (`F6600R` / `F6600RV9.0.21`)
con el perfil de servicio homologado `11 — F6600RV9.0.21`. No se debe elegir el
perfil de servicio por el nombre de otro modelo, como `6 — XN020-G3v` o `13 —
Generic_1_V100`, aunque la OLT lo acepte.

La combinación estándar es:

| Función | Perfil | Uso |
|---|---|---|
| Línea Internet simple | `6 — Generic_1_V100` | GEM/VLAN 100 |
| Línea dual Internet + TR-069 | `30 — GF_V100_M1000_TR069` | GEM 1/VLAN 100 y GEM 2/VLAN 1000 |
| Servicio físico F6600R | `11 — F6600RV9.0.21` | Capacidades del modelo homologado |
| Servidor TR-069 | Perfil vigente, actualmente `2` | URL y credenciales del ACS |

El perfil `30` puede cambiar según el entorno o la estrategia de gestión, pero
el perfil de servicio de la F6600R debe permanecer en `11`. La selección del
perfil genérico debe quedar en el resolver de autorización de la plataforma;
no debe depender de una selección manual por ONU.

## Estándar mínimo: TR-069 por OMCI

Este es el procedimiento estándar para una ZTE cuyo `EquipmentID` sea
`F6600RV9.0.21` en esta MA5608T. Configura únicamente la gestión necesaria
para TR-069; no crea PPPoE, no crea el service-port de VLAN 100 y no cambia
parámetros Wi-Fi ni WAN del CPE.

Los dos comandos OMCI son independientes y deben ejecutarse consecutivamente:

```text
ont ipconfig ...
ont tr069-server-config ...
```

El primero crea el host IP de gestión y el segundo asocia el servidor ACS. La
CLI de Huawei no permite fusionarlos en un único comando.

### Parámetros requeridos

| Parámetro | Valor estándar | Motivo |
|---|---|---|
| Line profile | `30` | Incluye TR-069, GEM 2 y VLAN 1000 |
| Service profile | `11` | Perfil exacto para `F6600RV9.0.21` |
| VLAN de gestión | `1000` | Transporte del host IP OMCI |
| GEM de gestión | `2` | Mapeado por el line profile 30 a VLAN 1000 |
| Tablas de tráfico | No se especifican | La OLT aplica su QoS por defecto al service-port mínimo |
| IP host | `ip-index 0`, DHCP, prioridad `2` | Host reservado para TR-069 |
| Perfil de servidor TR-069 | `2` | Perfil vigente de GenieACS en la OLT |

### Limpieza de una autorización anterior

Confirmar primero el `frame/slot/PON`, el puerto y el `ONT-ID`. Esta secuencia
elimina la ONU y sus service-port; es destructiva para ese equipo.

```text
enable
config
undo service-port port 0/{slot}/{port} ont {ontId}
interface gpon 0/{slot}
ont delete {port} {ontId}
quit
quit
save configuration
```

La OLT puede pedir `<cr>` después de `undo service-port` y confirmación `y`.
Después de borrar, esperar que el serial aparezca de nuevo:

```text
enable
display ont autofind all
```

### Alta mínima de TR-069

Ejecutar los comandos exactamente en estos contextos:

```text
enable
config
interface gpon 0/{slot}
ont add {port} {ontId} sn-auth {serial} omci ont-lineprofile-id 30 ont-srvprofile-id 11
quit
service-port vlan 1000 gpon 0/{slot}/{port} ont {ontId} gemport 2 multi-service user-vlan 1000 tag-transform translate
interface gpon 0/{slot}
ont ipconfig {port} {ontId} ip-index 0 dhcp vlan 1000 priority 2
ont tr069-server-config {port} {ontId} profile-id 2
quit
quit
save configuration
```

`desc` en `ont add` es opcional. Si la OLT muestra `{ <cr>|desc<K> }`, pulsar
Enter para continuar sin descripción. Esperar el mensaje `Configuration file
had been saved successfully` antes de cerrar sesión.

El service-port estándar no incluye `inbound traffic-table` ni `outbound
traffic-table`: la MA5608T acepta el comando y deja QoS en el valor por defecto.
Agregar tablas explícitas solo si una política de tráfico del servicio lo exige.

Para el caso validado de este documento, los valores son `{slot}=1`,
`{port}=6`, `{ontId}=16` y `{serial}=ZTEGDC47BFFD`.

### Verificación exclusiva en la OLT

```text
enable
config
interface gpon 0/{slot}
display ont ipconfig {port} {ontId}
display ont info {port} {ontId}
```

La evidencia mínima de éxito es:

```text
Run state: online
Management mode: OMCI
ONT IP host index: 0
ONT config type: DHCP
ONT manage VLAN: 1000
ONT IP: una dirección de la red de gestión
TR069 management: Enable
TR069 server profile ID: 2
```

`Config state: normal` es el estado deseado. Si queda en `failed`, no afirmar
que el aprovisionamiento OMCI está completamente sano aunque el host DHCP haya
obtenido una IP; revisar la compatibilidad OMCI del modelo y los perfiles
aplicados antes de añadir configuraciones adicionales.

Un reinicio `ont reset {port} {ontId}` no forma parte del estándar mínimo. Solo
usarlo si es necesario provocar un Inform nuevo y se acepta la interrupción del
servicio de esa ONU.

## Verificación en GenieACS

Desde el VPS donde está disponible la NBI:

```bash
curl -G http://127.0.0.1:7557/devices \
  --data-urlencode 'query={"_id":"5872C9-F6600R-ZTEGDC47BFFD"}' \
  --data-urlencode 'projection=_id,_lastInform,_deviceId'
```

El dispositivo debe aparecer con estos identificadores:

```json
{
  "_deviceId": {
    "_Manufacturer": "ZTE",
    "_OUI": "5872C9",
    "_ProductClass": "F6600R",
    "_SerialNumber": "ZTEGDC47BFFD"
  }
}
```

`_lastInform` debe ser reciente. También se puede buscar directamente por
serial:

```bash
curl -G http://127.0.0.1:7557/devices \
  --data-urlencode 'query={"_deviceId._SerialNumber":"ZTEGDC47BFFD"}' \
  --data-urlencode 'projection=_id,_lastInform,_deviceId'
```

## Limitaciones conocidas

En esta MA5608T R015 no están disponibles de forma utilizable los comandos
`ont wan-config`, `ont internet-config` ni `ont wan-profile` para crear una
WAN interna del router ZTE. La parte de la OLT se realiza mediante:

```text
ont ipconfig ... dhcp vlan 1000 priority 2
ont tr069-server-config ... profile-id 2
```

La configuración de una WAN interna adicional del F6600R, si fuera necesaria,
debe hacerse mediante GenieACS o la interfaz de gestión del CPE.

## Intento de cambio de SSID por OMCI

Para la ZTE `ZTEGDC47BFFD` se consultó directamente el CLI de la MA5608T
R015:

```text
interface gpon 0/1
ont ?
ont wlan-config ?
ont wifi-config ?
ont home-gateway-config 6 16 profile-id ?
display ont home-gateway-profile all
```

Resultado:

- `ont wlan-config` y `ont wifi-config`: comando desconocido.
- `ont home-gateway-config`: solo permite asociar un `profile-id` o
  `profile-name`; no acepta un SSID ni una clave Wi-Fi.
- `display ont home-gateway-profile all`: `Failure: The profile does not exist`.

Por lo tanto, en este firmware R015 no es posible cambiar el SSID de la ZTE
por OMCI desde la CLI de la OLT. El SSID `omci` que se aplicó durante la
prueba se envió previamente mediante un `setParameterValues` de GenieACS; no
debe considerarse una configuración OMCI.

Para modificar el SSID por OMCI sería necesario disponer de una OLT/firmware
que soporte perfiles home-gateway con parámetros WLAN, un perfil compatible
creado por U2000, o un modelo de ONU que implemente esas entidades OMCI con
esta OLT. En la configuración actual, las alternativas son GenieACS o la
interfaz local del F6600R.
