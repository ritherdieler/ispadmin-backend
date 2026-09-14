# Cablear usuario de prueba lab (staging) — suscripción 2329

Runbook para armar o recrear el abonado de laboratorio VSOL usado en validación ACS / 360.
Fecha de captura: 2026-08-31 (datos en `ispadmin_staging`).

Relacionado: [acs-wifi-lab-staging-2026-08-31.md](./acs-wifi-lab-staging-2026-08-31.md), [staging-360-fuentes-olt-acs-2026-08-31.md](./staging-360-fuentes-olt-acs-2026-08-31.md), [ambientes-local-staging-prod.md](./ambientes-local-staging-prod.md).

## Objetivo

Un abonado **solo de staging** cuyo CPE físico se pueda:

1. Ver en GenieACS (tag `lab`).
2. Enlazar a inventario OLT (serial Huawei/VSOL).
3. Opcionalmente medir tráfico (cola MikroTik + `host_device`).

**No** añadir este device a `SERVICE_HEALTH_PILOT_*` del `.env` de prod: el watcher de prod lo tomaría.

## Inventario actual (referencia)

| Pieza | Valor actual |
|-------|----------------|
| Suscripción | **2329** (`ACTIVE`, `FIBER`) |
| Nombre en BD | `LAB` / `VSOL` |
| IP WAN | `192.168.88.99` |
| Serial Genie / `fiber_onu_sn` | `12345B4641531C0B6` |
| Sufijo hex ACS | `31C0B6` |
| GenieACS `_id` | `B46415-V2804AX15T-12345B4641531C0B6` |
| Product class | `V2804AX15T` (manufacturer VSOL) |
| Tag GenieACS | `lab` → `subscription_acs.lab = 1` |
| Serial OLT (SNMP) | **`VSOL0031C0B6`** |
| OLT | `gigafiber-ma5608t` (`olt_mgr_olt.id = 2`) |
| F/S/P / ONT | board **1**, port **6**, onu_index **10** |
| External id OLT | `gigafiber-ma5608t_1_6_10` (`olt_mgr_onu.id = 7627`) |
| Host MikroTik staging | `network_device.id = 8` · **Mikrotik CCR 2** · `38.224.231.4` |
| Plan / pool / host en #2329 | `host_device_id=8`, `plan_id=1` (lab-basico); ver [lab-trafico-mikrotik-2329-2026-08-31.md](./lab-trafico-mikrotik-2329-2026-08-31.md) |

Provision flags en #2329: `mikrotik_provision_status=COMPLETE`, `olt_provision_status=COMPLETE`, `tr069_provision_status=COMPLETE`, `tr069_device_id` = GenieACS `_id`.

## 1. GenieACS (perfil / ONU)

### Device

- `_id`: `B46415-V2804AX15T-12345B4641531C0B6`
- Serial Number: `12345B4641531C0B6`
- Product class: `V2804AX15T`
- OUI: `B46415`

### Tag de laboratorio (obligatorio en staging)

En NBI GenieACS (o UI), asegurar el tag **`lab`** (constante `GenieAcsSubscriptionTags.LAB`).

No es un tag gestionado (`sub-` / `t:` / `c:`): el sync ACS solo proyecta `subscription_acs.lab = true` si `lab` está en `_tags`.

Tras etiquetar, forzar sync ACS o esperar el upsert; comprobar:

```sql
SELECT subscription_id, genieacs_device_id, serial_suffix, product_class, lab
FROM subscription_acs WHERE subscription_id = 2329;
-- lab debe ser 1; serial_suffix = 31C0B6
```

### Perfil TR-069 (modelo)

El alta/ops resuelve el perfil por `product_class` / tipo ONU:

- Clave: **`V2804AX15T`** (alias builtin `VSOLVA74`).
- Tabla staging: `tr069_model_profile` (importada; si falta la fila, el registry dinámico / builtin cubre tests; en ops de prod se importa desde CSV/device).
- Wi‑Fi telemetría 360: **radio 1 = 5 GHz**, **radio 5 = 2.4 GHz** (alineado a `wlan5Path` / `wlan24Path`). GPV de totales en ambos `TotalAssociations`; estaciones por banda en `acs_wifi_station_sample` (MAC hasheada en `station_key`, sin nombres).

No hace falta un “perfil Genie” distinto al de prod: el mismo `V2804AX15T`. Lo que aísla staging es el tag **`lab`** + fila en `ispadmin_staging`.

### Matching Genie ↔ OLT

| Vista | Serial |
|-------|--------|
| Suscripción / ACS | `12345B4641531C0B6` |
| Inventario OLT | `VSOL0031C0B6` |

Puente: últimos **6** hex = `31C0B6` (`Tr069SerialMatcher.normalizeSuffix`). El 360 usa ese sufijo en `HealthOnuAdapter` / `IdentityService`.

## 2. Suscripción (BD staging)

Mínimo para ACS + identidad OLT:

```text
id                  = 2329 (o el nuevo id si se recrea)
service_status      = ACTIVE
installation_type   = FIBER
ip                  = 192.168.88.99          -- IP real del CPE en red
fiber_onu_sn        = 12345B4641531C0B6     -- serial Genie (no el VSOL0…)
tr069_device_id     = B46415-V2804AX15T-12345B4641531C0B6
```

Fila ACS (creada/actualizada por sync):

```text
subscription_acs.genieacs_device_id = B46415-V2804AX15T-12345B4641531C0B6
subscription_acs.product_class      = V2804AX15T
subscription_acs.serial_suffix      = 31C0B6
subscription_acs.lab                = 1
```

Inventario OLT (tras sync SNMP staging o copia controlada):

```text
olt_mgr_onu.sn           = VSOL0031C0B6
olt_mgr_onu.board/port   = 1 / 6
olt_mgr_onu.onu_index    = 10
olt_mgr_onu.external_id  = gigafiber-ma5608t_1_6_10
```

Comprobar 360:

```http
GET /ispadmin-staging/subscription/2329/service-health
```

Identidad esperada: `lab=true`, `ACS=…`, `ONU=12345…`, `ONU_ID=7627`, `PON=2:1:6`, `OLT=2`.

## 3. Cola MikroTik (tráfico / host)

Cableado aplicado **2026-08-31** — detalle y verificación: [lab-trafico-mikrotik-2329-2026-08-31.md](./lab-trafico-mikrotik-2329-2026-08-31.md).

| Campo | Valor |
|-------|--------|
| `host_device_id` | **8** (Mikrotik CCR 2 · `38.224.231.4`) |
| `plan_id` | **1** (`lab-basico` 200/200 Mbps) |
| Cola | `[stg] id:2329, usuario:LAB VSOL, …` → `192.168.88.99/32` |
| Poll staging | ~60 s · `matched=1` en logs |

El collector usa **`host_device`** (no basta con `ip` en la ficha). `ip_pool_id` sigue NULL (IP lab fuera del pool staging).

### Host de staging

| Campo | Valor |
|-------|--------|
| `network_device.id` | **8** |
| Nombre | Mikrotik CCR 2 |
| IP API | `38.224.231.4` |
| Tipo | `FIBER_ROUTER` |

### Convención de nombre (staging)

Con `gigafiber.environment.tag=stg`, `QueueManagerService.buildQueueName` antepone **`[stg]`**:

```text
[stg] id:2329, usuario:<nombre> <apellido>, lugar:<lugar>, nap:<nap>, plan:<plan>, tipo:<tipo>
```

Comment de cola: `env=stg` (y tipo de instalación si aplica).

Parser: `SimpleQueueNameParser` — solo toca colas cuyo name pertenece al env (`[stg] …`); no borra colas de prod sin prefijo.

### Cómo cablear la cola

1. Asignar host a la suscripción (staging):

```sql
UPDATE subscription
SET host_device_id = 8
WHERE id = 2329;
```

2. (Opcional) Plan con velocidades reales para `max-limit` (`uploadSpeed`/`downloadSpeed` en Mbps). Sin plan, la cola puede crearse con límites vacíos/incorrectos.

3. Crear/recrear la simple queue **desde el WAR staging** (para que el prefijo sea `[stg]`), p. ej. el flujo de reprovisión MikroTik / recreate queue de esa suscripción, o API/ops que invoque `QueueManagerService.recreateQueueForSubscription` con perfil staging.

4. En el CCR, la cola debe quedar así (ejemplo):

```text
/queue simple
name="[stg] id:2329, usuario:LAB VSOL, ..."
target=192.168.88.99/32
max-limit=<up>M/<down>M
comment="env=stg ..."
```

5. Verificar que **no** exista una cola de prod sin `[stg]` apuntando al mismo target si quieres aislar; el manager staging solo elimina/recrea las de su env tag.

### Nota de red

`192.168.88.99` es IP de lab en la red real del MK, **no** del pool típico de staging (`192.168.250.0/24` cuando se usa pool dedicado). La cola puede existir igual si el CCR enruta esa IP; el `ip_pool_id` puede seguir NULL si no se quiere mezclar con asignación automática de pool.

## 4. Deploy para recolectar

Solo ACS / Wi‑Fi lab:

```bash
./scripts/deploy.sh --env staging --with servicehealth
```

ACS + OLT + netdiag + tráfico (adaptadores 360):

```bash
./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic
```

**CR obligatorio** cuando haga falta frescar el CPE (nombres, RSSI, validar deploy): GPV + `connection_request` en GenieACS (o `WIFI_REFRESH` confirmado). Sin CR el watcher lee caché vieja. Ver [acs-wifi-station-display-name-2026-08-31.md](./acs-wifi-station-display-name-2026-08-31.md#connection-request-cr--obligatorio-cuando-haga-falta).

Backoffice contra staging:

```bash
cd ../ispadmin-backoffice
npx vite --mode staging --port 3010
# → http://localhost:3010/subscriptions/2329/service-health
```

Apagar collectors al cerrar:

```bash
./scripts/deploy.sh --env staging
```

Dejar **tag `lab`** y `subscription_acs.lab=1` (y la cola `[stg]` si se creó).

## 5. Checklist rápido

- [ ] GenieACS: device `B46415-V2804AX15T-12345B4641531C0B6` con tag `lab`
- [ ] `subscription_acs.lab = 1` y `serial_suffix = 31C0B6`
- [ ] `fiber_onu_sn` = serial Genie; OLT tiene `VSOL0031C0B6` en 1/6/10
- [ ] `GET …/service-health` → `identity.lab=true`, `ONU_ID` / `PON` presentes
- [ ] Tras deploy / datos stale: **CR + GPV** (o WIFI_REFRESH) y nueva muestra ACS
- [x] (Tráfico) `host_device_id = 8` + simple queue `[stg] id:2329…` target `192.168.88.99/32`
- [ ] WAR staging con `--with` adecuado; prod sin este device en pilotos `.env`

## 6. Qué no hacer

- No meter el GenieACS id en `SERVICE_HEALTH_PILOT_*` de prod.
- No crear cola **sin** prefijo `[stg]` desde un proceso staging (contaminaría naming de prod).
- No cambiar `fiber_onu_sn` a `VSOL0031C0B6` sin revisar Genie/ACS: el canónico de abonado es el serial Genie; el puente es el sufijo `31C0B6`.
