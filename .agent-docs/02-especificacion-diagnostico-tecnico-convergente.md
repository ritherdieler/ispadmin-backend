# GIGAFIBER PERÚ

# Especificación del sistema de diagnóstico técnico convergente

**Documento 2 de 2** — correlación de tráfico, OLT/SNMP, ACS/GenieACS y MikroTik

Versión **1.2** · 29 agosto 2026  
Sustituye a la v1.1 del mismo archivo y a `02_Especificacion_Diagnostico_Tecnico_Convergente_GigaFiber.docx` (v1.0).  
Inventario contrastado con código de `ispadmin-backend`, `ispadmin-backoffice`, `IpsAdmin` y verificación live del ACS de producción (GenieACS NBI, 6 CPE).

---

## Contexto objetivo

Este documento define cómo diagnosticar problemas técnicos de una suscripción combinando evidencias de acceso GPON, CPE/Wi-Fi, router, topología, incidentes y consumo. Recibe la analítica de ancho de banda del **Documento 1** como una fuente, sin duplicar su recolección.

**Principio rector.** Diagnosticar significa explicar con evidencia, frescura, datos faltantes y confianza. Una anomalía de consumo puede iniciar o reforzar el análisis, pero no determina por sí sola la causa.

**Usuarios objetivo:** NOC · soporte · planta externa · operaciones · ingeniería

**Cambio respecto a v1.0.** La sección 4 del Word original sobreestimaba ACS/CPE unificado, histórico óptico y la capa convergente. La v1.1 corrigió el estado real y el capability map ACS.

**Cambio respecto a v1.1.** Recolección Wi-Fi B/C por Inform horario (preset GenieACS `2 PERIODIC`), no solo on-demand. Puente ACS↔suscripción documentado (`subscription_acs`, `tr069_device_id`, tag `sub-{id}`). Series `acs_wifi_*` sin Connection Request masivo ni `Hosts.Host.*`. Fase 1 recalibrada.

---

## 1. Propósito y límites

Construir una vista técnica única del servicio para distinguir problemas de capacidad, acceso GPON, señal óptica, CPE/ACS, Wi-Fi, router y telemetría. El sistema debe reducir el tiempo de diagnóstico y evitar intervenciones equivocadas.

**Incluye:** identidad convergente, adquisición OLT/ACS/MikroTik, correlación, incidentes, evidencia, confianza, blast radius, vista 360 y acciones remotas controladas.

**Consume:** las series y anomalías ya implementadas del Documento 1 (`subscription_traffic_sample`, rollups, `traffic_anomaly_event`, `traffic_source_run`). Nombres de la v1.0 (`traffic_observation`, `traffic_aggregate`, `traffic_source_health`) se mapean en la sección 2; no se reimplementan.

**No incluye:** un segundo collector de queues, una segunda política de retención de tráfico ni un cálculo alternativo de P95/bytes. Observability-web (APM de software) queda fuera de alcance.

**Semántica obligatoria.** OLT o ACS pueden exponer equipos/MAC/hosts asociados. Ese valor representa **dispositivos observados**, no número de personas. La UI, API y reglas deben llamarlo `associated_device_count` o `connected_device_count`. No persistir `HostName` ni `X_ZTE-COM_AssociatedDeviceName`.

---

## 2. Contrato con la analítica de consumo

El Documento 1 **ya está implementado** (Bandwidth Intelligence). El diagnóstico referencia el `event_id` de `traffic_anomaly_event` y consulta las series por `subscription_id`. Si el contrato de tráfico está stale o con baja cobertura, reduce `confidence` y lo muestra como `missing_evidence`.

### 2.1 Interpretación diagnóstica (sin cambio de política)

| Entrada Documento 1 | Interpretación diagnóstica | Prohibición |
|---|---|---|
| `TRAFFIC_DROP` | Buscar caída GPON, ACS stale, router, energía o cambio normal. | No declarar corte solo por tráfico bajo. |
| `NO_TRAFFIC` | Verificar estado ONU/CPE y si el cliente simplemente no usó servicio. | No equiparar cero con ausencia de muestra. |
| `TRAFFIC_MISSING` | Priorizar salud del collector/router antes de evaluar al cliente. | No generar incidente técnico del abonado sin evidencia. |
| `PLAN_SATURATION` | Correlacionar plan, P95, Wi-Fi, equipos asociados y salud del router. | No recomendar upgrade por un pico aislado. |
| `PATTERN_DEVIATION` | Comparar baseline, cobertura y eventos de otras fuentes. | No etiquetar abuso, malware o conducta. |
| `TRAFFIC_SPIKE` | Extra ya implementado; tratar como síntoma, no causa. | Conservar; no es abuso. |

### 2.2 Nombres reales (no duplicar)

| Nombre v1.0 | Implementación actual | Notas |
|---|---|---|
| `traffic_observation` | `subscription_traffic_sample` | `collected_at`, `sample_status`, `source_run_id`, `queue_id`, plan Mbps |
| `traffic_aggregate` | `subscription_traffic_five_minute` / `_hourly` / `_daily` / `_monthly` | P95, utilización, `seconds_over_80/90/95`, `coverage_pct` |
| `traffic_anomaly_event` | `traffic_anomaly_event` | Incluye `confidence` y `evidence_json` |
| `traffic_source_health` | `traffic_source_run` + `GET /traffic/bandwidth/v1/sources` | El health se deriva del último run |
| `observed_at` | No existe como campo global | Usar `bucket_start` + `collected_at` en tráfico; `polled_at` en OLT |

**APIs a consumir:** `GET /traffic/bandwidth/v1/overview|series|subscriptions|subscriptions/{id}|sources|anomalies`, `GET /subscription/{id}/traffic` (+ `latest|today|day|summary`), WebSocket live.

**Cadencia:** poll de colas 60 s; anomalías 300 s; rollups 5 min / hora / nightly 03:30.

**Regla:** no consultar MikroTik por cliente desde diagnóstico. El tráfico por suscripción ya sale de este collector.

---

## 3. Fuentes técnicas y semántica

| Fuente | Datos verificados / candidatos | Aporte |
|---|---|---|
| OLT / SNMP | Run/match state, last down cause, ONU RX/TX, OLT RX, temperatura, distancia. Bias y voltaje en live, no persistidos. Traps en buffer. MAC count no existe. | Acceso GPON, óptica, planta y fallas compartidas. |
| ACS / GenieACS | Last Inform, modelo/firmware, SSIDs de provisión. Puente `subscription_acs` (4.7). Tras Inform+preset o `refreshObject`: `TotalAssociations`, RSSI/SNR por estación (ZTE y VSOL). Bytes por estación **no**. | CPE, ruta de gestión y calidad Wi-Fi. Histórico por Inform (6.1). No ranking de MB. |
| MikroTik / netdiag | CPU, uptime, Netwatch, syslog, DDM, incidentes padre/hijo. Interfaces sin errores/drops. Memoria solo live de core. | Transporte, router, uplinks y salud de red. |
| Analítica de tráfico | Observaciones, P95, utilización, anomalías y health del collector. | Demanda y síntoma temporal, no causa autónoma. |
| Dominio ISP | Suscripción, plan, router, ONU SN, OLT/PON, NAP. Zona en inventario OLT, no en suscripción. | Identidad, topología y alcance. Sin grafo versionado. |

**Disponibilidad por modelo.** No asumir que todo CPE u ONU expone los mismos parámetros. Mantener capability map por fabricante/modelo/firmware (sección 4.2) y distinguir `UNSUPPORTED`, `MISSING`, `STALE` y `ERROR`.

---

## 4. Estado real de la plataforma (inventario 2026-08-29)

### 4.1 Veredicto

Hay **cuatro pilares operativos maduros y desacoplados**. No existe aún la capa convergente (`service_health`, identidad versionada, API 360, `diagnosis_code` cruzado).

| Pilar | Estado | Reutilizar — no reconstruir |
|---|---|---|
| Bandwidth Intelligence (Doc. 1) | EXISTE | Collector, rollups, anomalías, UI `/bandwidth-intelligence` |
| OLT Gateway | EXISTE (snapshot) | Inventario, poll óptico ~5 min, live status, reboot, OnuDetail |
| NetDiag / NOC | EXISTE (router/OLT) | Poll MikroTik, padre/hijo, ack/resolve/silence, logs OLT, LLM |
| Dominio ISP + ACS de provisión | EXISTE | `subscription`, plan, NAP, ONU SN, `subscription_acs`, refresh/reboot |

| Capa convergente | Estado |
|---|---|
| Identidad versionada (`identity_link`) | NO EXISTE |
| Puente ACS↔suscripción (`subscription_acs` + `tr069_device_id`) | EXISTE (heurística de provisión; ver 4.7) |
| `telemetry_source_run` unificado | PARCIAL (un run por dominio) |
| `service_health_event` + `evidence_link` | NO EXISTE |
| `capability_profile` de lectura | NO EXISTE (`Tr069ModelProfile` es de **escritura**) |
| Motor `diagnosis_code` / `missing_evidence` | NO EXISTE |
| Vista 360 | NO EXISTE |
| Serie histórica óptica | NO EXISTE |
| API `GET /subscription/{id}/cpe-status` | NO EXISTE (la UI `/cpe` ya la llama) |
| Telemetría Wi-Fi en producto | NO EXISTE (ACS sí puede; el backend no la pide). Camino: 6.1–6.3 |

### 4.2 ACS — verificado en producción (GenieACS, 29 ago 2026)

6 CPE en el ACS. El Inform periódico **no** trae hosts ni señal: el cache solo tenía SSIDs. Tras `refreshObject` + GPV + Connection Request:

| CPE | CR | Equipos | Señal por estación | MB por estación |
|---|---|---|---|---|
| ZTE F6600R A (online) | 200 | 4 hosts, todos `802.11`. `TotalAssociations` 3 (2.4) + 1 (5) | RSSI −80…−51 dBm, SNR 17–47, rate, noise | **No.** `X_ZTE-COM_WLAN_Bytes*` = 0. Sí hay paquetes y bytes del **radio** |
| VSOL V2804AX15T (online) | 200 | 6 hosts (2 Active; 5 Wi-Fi + 1 Ethernet). `TotalAssociations` 1 | `X_HW_RSSI=−30`, SNR 16, noise −46, Rx/Tx rate | **No.** `WLAN.TotalBytesSent` saturó UINT32 (`4294967295`) |
| ZTE F6600R B (online) | 200 | 2 hosts `802.11`. `TotalAssociations` 2 + 0 | RSSI −68 / −59 / −58, SNR 30–40 | **No.** Bytes por estación = 0 |
| ZTE F6600R C | 202 cola | Sin lectura (último Inform 26 ago) | — | — |
| Huawei HG8145X6 | 202 cola | Offline. Export declara `Hosts.X_HW_RSSI` y `X_HW_Stats` vacíos | Pendiente GPV | Pendiente |
| ZTE F6600R D | 202 cola | Sin lectura (último Inform 26 ago) | — | — |

**Allowlist de lectura** (no persistir nombres de host ni SSID):

- Conteo: `Hosts.HostNumberOfEntries`, `Hosts.Host.{i}.Active`, `InterfaceType`, `WLANConfiguration.{i}.TotalAssociations`
- Señal ZTE: `AssociatedDevice.{i}.AssociatedDeviceRssi`, `X_ZTE-COM_WLAN_SNR`, `X_ZTE-COM_WLAN_Noise`, `AssociatedDeviceRate`
- Señal VSOL: `AssociatedDevice.{i}.X_HW_RSSI`, `X_HW_SNR`, `X_HW_Noise`, `X_HW_RxRate`, `X_HW_TxRate`
- Actividad (no MB): ZTE `X_ZTE-COM_WLAN_PacketSend/Received`
- Bytes de radio (SSID entero): `WLANConfiguration.{i}.Stats.BytesSent/Received`

**Implicación.** `associated_device_count` y `WIFI_QUALITY` son viables on-demand en ZTE y VSOL. Ranking de MB por celular/laptop **no** se diseña sobre ACS. El consumo del abonado es MikroTik (Documento 1). Huawei: repetir GPV cuando Informee.

**Hoy el backend no lee nada de esto.** Projection NBI: `_id,_lastInform,_lastBoot,_deviceId,ConnectionRequestURL`. `subscription_acs` guarda identidad, firmware, IP WAN cacheada y SSIDs de provisión. El vínculo device↔suscripción **sí** se persiste (sección 4.7); faltan el lookup inverso y las series B/C.

**Hueco UI/API.** `/cpe` llama `GET /subscription/{id}/cpe-status` y `PUT /…/cpe-config`. Esos endpoints no existen. Piezas reales: `/subscription/{id}/acs`, reboot ONU, provisión TR-069.

GenieACS 1.2 **no tiene webhook** on-Inform. El trigger nativo es un **preset** que ejecuta un **provision** en la misma sesión CWMP. Prod hoy (`configure-genieacs-pilot.sh`): `PeriodicInformInterval = 3600`; presets `bootstrap` (0 BOOTSTRAP / 0 BOOT / 1 BOOT), `tplg-router`, `mstc-router`; provision `default` pide WAN IP + SSID con `{path: hourly}` y **prohíbe** `Hosts.Host.*` (recorrerlo superó el timeout de 50 ms y puso el VPS ~90% CPU; `GenieAcsPilotProvisionsTest`, `.agent-docs/genieacs-cr-credentials.md`). El histórico Wi-Fi no puede reabrir ese camino.

### 4.3 OLT / óptica

| Dato | Estado | Cadencia |
|---|---|---|
| run / match / last down | EXISTE en `olt_mgr_onu_status_current` | Inventario ~10 min |
| ONU RX/TX, OLT RX, temp, distancia, `signal_category` | EXISTE | Señal ~5 min |
| bias / voltaje | PARCIAL — DTO live, no persistidos | On-demand |
| MAC count | NO EXISTE | — |
| traps | PARCIAL — buffer in-memory; NetDiag sí persiste | UDP opcional |
| serie óptica histórica | NO EXISTE — History de UI = auditoría de acciones | — |

Sin serie óptica no se evalúa «RX degrada en tendencia + flaps».

### 4.4 MikroTik / netdiag

EXISTE como NOC de **targets de red** (router, uplink, PON), no del servicio de un abonado. CPU, Netwatch, syslog, DDM, incidente padre/`CorrelationEngine`, ventanas de mantenimiento. Falta: errores/drops de interfaz, memoria alertada, ping por suscripción, lista de abonados afectados.

### 4.5 Dominio ISP e identidad

`subscription_id` es el eje. Hoy hay ONU SN, ACS deviceId, IP, queue (por muestra), router y NAP **sin versionar**. No hay `identity_link` (`valid_from` / `valid_to` / `source` / `confidence`). El puente ACS operativo **sí existe** (4.7). Conflictos de matching ACS por sufijo de serial ya se rechazan en provisión; no hay cola diagnóstica.

### 4.6 Backoffice — componer, no clonar

| Pantalla | Ruta | Estado | Uso en 360 |
|---|---|---|---|
| Bandwidth Intelligence | `/bandwidth-intelligence` + `/subscriptions/:id` | EXISTE (freshness, confidence, evidence) | Tarjeta de consumo / anomalías |
| Tráfico por suscripción | modal/panel en `/subscriptions` | EXISTE (live WS) | Tarjeta live |
| TrafficAnalytics | `/traffic-analytics` | PARCIAL — sin sidebar | Perfil de red, no cockpit |
| OnuDetail | `/onus/configured/:id` | PARCIAL — live + reboot; history ≠ serie óptica | Vista especializada óptica |
| CpeManagement | `/cpe` | PARCIAL — UI adelante del backend | Dueña de acciones CPE cuando exista contrato |
| ACS modal | `/subscriptions` | EXISTE | Tarjeta ACS |
| NOC | `/noc`, `/noc/incidents/:id` | EXISTE | Extender causa/evidence; `diagnostic-json` no está en UI |
| Logs OLT | `/noc/olt-logs` | EXISTE | Enlace blast radius |
| Vista 360 | — | NO EXISTE | Página nueva que **embebe o enlaza** lo anterior |

**Android (`IpsAdmin`):** búsqueda, ficha comercial, reboot ONU, restaurar conexión. Fuera de las fases 0–7 salvo recorte móvil posterior.

### 4.7 Enlace ACS ↔ suscripción (código 2026-08-29)

GenieACS no guarda un FK a MySQL. El backend **ya escribe el puente** al terminar o reintentar el alta TR-069 (`Tr069PostInstallProvisioner` → `SubscriptionAcsSyncService.upsertFromProvision`).

```
onu.sn (SmartOLT)
        │
        ▼
Tr069SerialMatcher (últimos 6 hex)
        │
        ├──────────────► subscription.tr069_device_id
        ├──────────────► subscription_acs.genieacs_device_id  (= GenieACS _id)
        └──────────────► tag ACS  sub-{subscriptionId}         (no está en SQL)
                                      │
                                      ▼
                         InformWatcher → acs_wifi_*_sample.subscription_id
```

**Tres llaves, en este orden**

| # | Llave | Dónde | Uso |
|---|---|---|---|
| 1 | Canónica | `subscription_acs.genieacs_device_id` = GenieACS `_id` | Watcher y 360. Índice `idx_subscription_acs_device_id`. PK = `subscription_id`. Migración `V26__subscription_acs.sql`. |
| 2 | Operativa | `subscription.tr069_device_id` | Misma `_id`; escrita en el provisioner. Fallback si no hay fila ACS. |
| 3 | En el ACS | tag `sub-{subscriptionId}` (`GenieAcsSubscriptionTagger`) | UI/NBI. También `t:INTERNET\|DUO\|TV` y `c:{NOMBRE}`. El job **no** inventa suscripción a partir del tag. |

**Match inicial.** Últimos 6 hex de `fiberOnu.sn` vs `serialNumber` / `_id` (`Tr069SerialMatcher`). Ej.: `VSOL0031C0B6` ↔ `…31C0B6`; `HWTCC6FBA6AA` ↔ `…FBA6AA`. Colisión → `Ambiguous` → `MANUAL_REQUIRED`. No hay MAC ni CPE SN en el matching.

**Huecos para el watcher**

- `SubscriptionAcsRepository` no tiene `findByGenieacsDeviceId`. Hay que añadirlo (o SQL equivalente).
- No hay UNIQUE en `genieacs_device_id`.
- Si no hay `deviceId` no se crea fila `subscription_acs` (`upsert` sale si `deviceId` es null): timeout de Inform, serial inválido, ACS off. GET `/acs` → 404.
- Dos fuentes de verdad: ops usa `existing?.genieacsDeviceId ?: subscription.tr069DeviceId`.

**Cuándo no hay puente.** Informs de CPE sin fila se descartan o se cuentan como `unmapped` en `telemetry_source_run` ACS. No re-matchear la flota por sufijo de 6 hex en cada tick.

---

## 5. Identidad y topología

`subscription_id` es el eje del diagnóstico; relacionar ONU SN, `olt_id`, frame/slot/port/ont_id, ACS `deviceId`, MAC WAN, IP, router y queue.

Versionar enlaces con `valid_from`, `valid_to`, `source`, `confidence` y `verified_at`. IP, queue y posición PON pueden cambiar.

Resolver por serial/vínculo de provisión **antes** de IP, MAC o sufijos. Conflictos pasan a revisión; nunca se unen silenciosamente.

Modelar jerarquía `router/uplink → OLT → PON → ONU → suscripción` para incidentes compartidos y afectados.

Mantener snapshot histórico cuando un diagnóstico del pasado deba reproducir plan y topología vigentes en esa ventana.

**Hoy:** los IDs existen en silos (`subscription`, `subscription_acs`, `olt_mgr_onu`, muestras de tráfico). El puente ACS (4.7) basta para las series Wi-Fi. Fase 3 crea `identity_link`; no reescribir esos silos.

---

## 6. Adquisición y frescura

| Flujo | Cadencia inicial | Regla de carga / calidad |
|---|---|---|
| Tráfico | Contrato Documento 1 (60 s / rollups) | No consultar MikroTik por cliente. |
| Óptica ONU | 5 min online + traps | SNMP walk por columna/PON; persistir **serie**, no solo snapshot. |
| Estado / inventario OLT | 10–60 min o evento | Separado de óptica. |
| ACS / CPE | Ver política 6.1–6.3 | Identidad en cache; B/C en Inform horario (preset). CR solo on-demand. |
| Salud MikroTik | 1 min crítica / 5 min estable | Reutilizar netdiag y source health. |

Toda observación debe incluir `observed_at` (o equivalente del dominio), `collected_at`, `source`, `quality_status` y `error_reason`. La correlación usa ventanas tolerantes a desfase y disminuye confianza cuando las fuentes no son contemporáneas.

### 6.1 Política de recolección ACS

El Inform periódico **no** trae hosts ni señal por defecto. Un `refreshObject` + GPV con Connection Request despierta el CPE. Eso **no** es el camino del histórico: GenieACS 1.2 no tiene webhook; el trigger es un **preset** que, cuando el CPE Informa, ejecuta un **provision** en la misma sesión CWMP.

Ya existe `PeriodicInformInterval = 3600` en el provision `inform`. El histórico pide conteo y señal **en esa sesión** (~1 muestra/hora/CPE) sin Connection Request.

**Restricción operativa ya sufrida.** El provision `default` **no** recorre `Hosts.Host.*`: superó el timeout de 50 ms y puso el VPS al ~90% CPU. Ningún provision nuevo puede reabrir ese camino. Preferir escalares (`TotalAssociations`, `HostNumberOfEntries`) y `AssociatedDevice.*` (RSSI/SNR).

**Trigger normativo (por construir)**

| Pieza | Valor |
|---|---|
| Preset `_id` | `gigafiber-wifi-telemetry` |
| Evento | `2 PERIODIC` (Inform horario; no BOOT ni CR) |
| Canal | `default` o canal propio, `weight` > 10 |
| Precondition | opcional: `DeviceID.ProductClass` F6600R / V2804AX15T |
| Provision | `gigafiber-wifi-telemetry`, `{path: hourly}` |
| Allowlist | `HostNumberOfEntries`; `WLANConfiguration.{1,5}.TotalAssociations`; ZTE `AssociatedDevice.*.AssociatedDeviceRssi` / SNR / noise / rate; VSOL `AssociatedDevice.*.X_HW_RSSI` / `X_HW_SNR` |
| Prohibido | `Hosts.Host.*` |

Scripts: `scripts/genieacs/configure-genieacs-pilot.sh`, `apply-provisions-via-nbi.sh`. Test que **prohíba** `Hosts.Host` (mismo patrón que `GenieAcsPilotProvisionsTest`).

**Tres capas**

| Capa | Qué | Cómo | Cuándo | Connection Request |
|---|---|---|---|---|
| A · Identidad / frescura | `lastInform`, `lastBoot`, `productClass`, firmware, `deviceId`, CR URL | `GET` NBI con projection mínima (la de hoy) | Al abrir 360 / ACS; barrido de cache cada 15–30 min | **No** |
| B · Conteo | `HostNumberOfEntries`, `TotalAssociations` por radio | Declare en el Inform (`2 PERIODIC`). **No** listar `Hosts.Host.*` | 1×/hora en el Inform. On-demand: botón «actualizar Wi-Fi» | Solo on-demand si cache B > 15 min **y** `lastInform` fresco (< 2× intervalo) |
| C · Señal por estación | ZTE: RSSI, SNR, noise, rate. VSOL: `X_HW_RSSI`, SNR, noise, Rx/Tx rate | Declare `AssociatedDevice.*` en el mismo Inform | Igual que B | Igual que B; **nunca** en barrido |

**Prohibido recolectar o persistir**

- `HostName`, `X_ZTE-COM_AssociatedDeviceName`, SSIDs de invitados, passwords, árbol TR-069 completo.
- Recorrer `Hosts.Host.*` en provision o en job.
- Bytes por estación (`X_ZTE-COM_WLAN_Bytes*` = 0 en prod; VSOL no los tiene). Marcar `UNSUPPORTED`.
- MAC completa en UI/LLM: si se guarda para correlacionar, hash (`station_key`); no MAC en claro.
- `Hosts` de equipos Ethernet como si fueran Wi-Fi: filtrar `InterfaceType=802.11` o `Layer2Interface` → WLAN (solo on-demand, nunca en el provision horario).

**Permitido persistir**

- Conteo: `associated_device_count`, `associated_2g`, `associated_5g`, `lan_device_count`.
- Por estación (sin nombre): banda, RSSI, SNR, noise, rate, paquetes Tx/Rx (ZTE, proxy de actividad, no MB), `observed_at`, `station_key`.
- Bytes de **radio/SSID** (opcional, tendencia gruesa). En VSOL `TotalBytesSent` puede saturar UINT32: si el valor es `4294967295`, `quality_status=INVALID`.
- Meta de corrida: `telemetry_source_run` ACS con leídos / ausentes / unmapped / error / lag.

**Connection Request**

- Máximo **1 CR por CPE cada 10 min**.
- Máximo **N CR concurrentes** en el ACS (empezar en 3; no un barrido de toda la flota).
- Si `lastInform` es viejo: **no** CR. Emitir `ACS_STALE` / `missing_evidence`. Internet puede seguir activo.
- HTTP 202 (tarea en cola) ≠ dato fresco. Esperar Inform o marcar `STALE`.
- Timeout corto; no reintentar en bucle.
- Reservado al botón «actualizar Wi-Fi» (y al motor solo si Inform fresco y sample B/C > 15 min).

**Barrido periódico (capa A + copia de cache B/C)**

Cada 15–30 min (capa A) o 1–2 min (watcher de Inform, 6.2): leer el cache NBI **sin** CR. Actualizar `last_inform_at` y, si `_lastInform` avanzó y el provision ya llenó allowlist, escribir series (6.3). No encolar `refreshObject`/`getParameterValues` con CR en ese ciclo.

**Frescura**

| Edad del último sample B/C exitoso | `quality_status` |
|---|---|
| < 15 min | `FRESH` (raro salvo on-demand; el Inform es ~1 h) |
| < 2× `PeriodicInformInterval` (~2 h) | `FRESH` para series horarias |
| 2 h – 6 h | `STALE` (usable con menos confidence) |
| Nunca o GPV fallido | `MISSING` o `ERROR` |
| Parámetro no existe en el `capability_profile` | `UNSUPPORTED` (Huawei hasta GPV live; bytes por estación en todos) |

**Por modelo**

- ZTE F6600R y VSOL V2804AX15T: capas B y C **soportadas** (verificado prod 2026-08-29).
- Huawei HG8145X6: capa A sí; B/C `UNSUPPORTED` hasta un Inform+GPV exitoso.
- Modelo nuevo: solo capa A hasta completar allowlist con un CPE de lab/prod.

**Quién dispara B/C**

1. **El CPE**, al Informar cada ~3600 s → preset `gigafiber-wifi-telemetry` (camino normal del histórico).
2. Operador en vista 360 / CPE («actualizar Wi-Fi») → CR si Inform fresco y sample > 15 min.
3. Motor de diagnóstico, **después** de descartar GPON down, telemetría faltante y ACS stale, cuando la hipótesis restante es Wi-Fi o saturación de plan + muchos equipos. Preferir el último sample horario; CR solo si hace falta evidencia más fresca.
4. Nunca un job que recorra todas las suscripciones con CR.

### 6.2 Resolver Inform → suscripción

Job ligero (p. ej. cada 1–2 min), no por suscripción:

1. `GET /devices/?projection=_id,_lastInform,…allowlist` (sin CR).
2. Si `_lastInform` > watermark del device → candidato a sample.
3. Resolver `subscription_id`: `findByGenieacsDeviceId(_id)` → fallback `subscription.tr069_device_id` → si ambos vacíos, **no** sample (contar `unmapped`).
4. No re-matchear la flota por sufijo de 6 hex en cada tick.
5. No usar el tag `sub-*` para crear el puente.
6. Registrar `telemetry_source_run` ACS (leídos / ausentes / unmapped / `UNSUPPORTED`).

### 6.3 Series Wi-Fi persistidas

Tablas nuevas (Flyway), análogas a `subscription_traffic_sample` (`collected_at`, `sample_status`):

| Tabla | Granularidad | Campos |
|---|---|---|
| `acs_wifi_count_sample` | 1 fila por Inform / suscripción | `associated_device_count`, `associated_2g`, `associated_5g`, `lan_device_count` |
| `acs_wifi_station_sample` | 1 fila por estación / Inform | banda, RSSI, SNR, noise, rate, paquetes (ZTE), `station_key` = hash MAC |
| `acs_wifi_status_current` | opcional, 1 fila por suscripción | last sample para la 360 |

Retención: conteos ~90 días; estaciones 14–30 días (más volumen, más PII residual). Purga nightly como `SubscriptionTrafficRetentionService`.

Huawei HG8145X6: capa A siempre; B/C `UNSUPPORTED` hasta un Inform+GPV exitoso.

No implementar ranking de MB por estación. El volumen del abonado sigue siendo MikroTik (Documento 1).

---

## 7. Persistencia diagnóstica

Conservar current state para lectura rápida y series tipadas para óptica/CPE cuando aporten tendencia. No almacenar el árbol TR-069 completo ni nombres de dispositivos finales.

Series ACS Wi-Fi (sección 6.3): `acs_wifi_count_sample`, `acs_wifi_station_sample`, `acs_wifi_status_current` opcional.

Crear `telemetry_source_run` por fuente/equipo (reutilizar `traffic_source_run`, `olt_mgr_sync_run`, `net_diag_probe_run` y añadir run ACS con leídos / unmapped / `UNSUPPORTED`).

Crear `service_health_event` para hechos derivados y `evidence_link` para referenciar observaciones/eventos sin duplicar payloads.

Persistir `identity_link` versionado y `capability_profile` de **lectura** por modelo/firmware (distinto de `Tr069ModelProfile` de escritura).

El incidente conserva diagnóstico, confianza y snapshots mínimos; las series siguen en sus dominios propietarios.

---

## 8. Motor de correlación y diagnóstico explicable

| Evidencia correlacionada | Causa probable | Siguiente prueba | ¿Evaluable hoy? |
|---|---|---|---|
| ONU offline/lastDown + tráfico ausente + ACS stale | Acceso GPON, energía o CPE | Vecinos del PON, causa y evento óptico | PARCIAL |
| RX degrada en tendencia + flaps | Planta externa o degradación óptica | Comparar ONU RX/OLT RX, distancia y vecinos | **NO** — falta serie óptica |
| Varias ONU del PON caen juntas | Falla compartida PON/OLT/uplink | Incidente padre y blast radius | PARCIAL — NetDiag padre; sin lista de abonados |
| P95/tiempo sobre plan + óptica/router sanos | Demanda o saturación del plan | Ver plan, baseline y persistencia | **SÍ** (falta cruce automático) |
| WAN/ONU online + Wi-Fi débil/retries + varios equipos | Cobertura/capacidad Wi-Fi local | Tendencia de RSSI/conteo (series 6.3) + on-demand si hace falta | Viable en ZTE/VSOL tras Fase 1 (Inform + series); no en producto hoy |
| OLT online + tráfico presente + ACS stale | Ruta de gestión TR-069 | Salud ACS y Last Inform; Internet puede seguir activo | PARCIAL |
| Muchos clientes + CPU/drops/uplink altos | Cuello MikroTik/transporte | Capacidad de interfaz, drops y P95 de red | PARCIAL — sin drops |
| `TRAFFIC_MISSING` + poll stale | Falla de telemetría | Source run, reachability y collector | **SÍ** (tráfico) |

---

## 9. Contrato de salida del diagnóstico

| Campo | Requisito | Equivalente hoy |
|---|---|---|
| `diagnosis_code` | Taxonomía estable: `GPON_DOWN`, `OPTICAL_DEGRADATION`, `WIFI_QUALITY`, `ACS_STALE`, `ROUTER_CAPACITY`, `TELEMETRY_GAP`, etc. | `anomaly_type` + `reason_code` en silos |
| `probable_cause` | Texto breve, técnico y comprensible. | Título de incidente NOC |
| `confidence` | 0–1 o categorías, derivado de calidad, frescura, coherencia y cantidad de fuentes. | Solo anomalías de tráfico |
| `evidence[]` | Fuente, métrica/evento, `observed_at`, valor/resumen y enlace. | `evidence_json`; `diagnostic-json` no está en UI |
| `missing_evidence[]` | Datos esperados ausentes, stale, unsupported o con error. | **No existe** |
| `affected_scope` | Suscripción, ONU, PON, OLT, router, interfaz, NAP o zona. | Target NetDiag |
| `recommended_next_check` | Próxima prueba segura y no disruptiva. | Runbooks ops, no por suscripción |
| `suppressing_incident_id` | Incidente padre cuando la causa compartida suprime duplicados. | Solo intra-NetDiag |

**Explicabilidad.** La UI debe mostrar qué evidencias aumentaron o redujeron `confidence`. Un score sin desglose no es suficiente.

---

## 10. Vistas y flujos de backoffice

### 10.1 Vista 360 de suscripción

Estado actual: Internet/GPON/ACS, diagnóstico, confianza y frescura por fuente.

Timeline alineado: tráfico y anomalías del Documento 1, eventos ONU, óptica, Last Inform, reinicios y acciones.

Tarjetas reutilizadas/enlazadas: Bandwidth Intelligence, OnuDetail, ACS/CPE y incidente NOC. No clonar esas pantallas.

Pregunta operativa: ¿es consumo/capacidad, acceso GPON, CPE/Wi-Fi, router o falta de datos?

### 10.2 NOC por topología

Extender NOC existente con causa probable, `confidence`, `affected_scope`, evidence y enlaces a OLT/PON/ONU/suscripción.

Conservar filtros, mantenimiento, ack/resolve/silence, timeline, logs OLT y contexto LLM.

Agrupar incidentes por ancestro/topología y mostrar blast radius sin ocultar abonados afectados.

Exponer en UI el `GET /api/netdiag/incidents/{id}/diagnostic-json` ya existente.

### 10.3 ONU y CPE

Conservar OnuDetail como vista especializada de óptica live y acciones OLT. Añadir serie óptica cuando exista.

Conservar CpeManagement para búsqueda y configuración; **cerrar el contrato backend** (`cpe-status`/`cpe-config` o adaptar la UI a `/acs` + óptica).

Añadir telemetría Wi-Fi y `associated_device_count` cuando el modelo la exponga (ZTE/VSOL en Inform horario + on-demand). Mostrar tendencia de equipos y señal, no un punto aislado. Nunca rotularlo «personas».

Separar observar/diagnosticar de reiniciar/configurar/eliminar, con confirmación y auditoría.

---

## 11. Incidentes compartidos y automatización

Crear incidente padre cuando múltiples suscripciones del mismo PON/OLT/router coinciden en ventana y evidencia. NetDiag ya suprime por ancestro de **target**; falta la relación con abonados.

Suprimir notificaciones duplicadas, pero conservar relaciones de cada cliente afectado y su estado.

Ventanas de mantenimiento existentes deben inhibir o anotar alertas según alcance y tiempo.

Runbooks pueden recomendar refresh, ping, lectura live o revisión de vecinos. Reboot/config/delete requieren rol, confirmación, rate limit y auditoría.

No ejecutar una acción disruptiva automáticamente por una inferencia o respuesta LLM.

**Acciones ya existentes (no automáticas):** `PUT /subscription/reboot-fiber-onu`, `POST /onu/configured/{id}/reboot`, `POST /subscription/{id}/acs/reboot|refresh|retry-tr069`. Audit en `SubscriptionLog` / `OltMgrAuditLog` / `subscription_acs.last_task_*`.

---

## 12. Privacidad y seguridad

Recolectar métricas técnicas y conteos, no nombres de dispositivos finales, navegación, dominios, payloads ni contenido. El ACS ZTE expone `X_ZTE-COM_AssociatedDeviceName`: **no persistir ni enviar a LLM**.

Proteger MAC/device identifiers y limitar retención/exposición. Mostrar agregados cuando baste para soporte.

No inferir número de personas, identidad, edad, actividad, abuso o conducta a partir de dispositivos/volumen.

Secretos SNMP/OLT/ACS/RouterOS fuera de logs; allowlists de parámetros y comandos; auditoría de acciones. Connection Request no masivo.

Contexto enviado a LLM debe minimizar datos personales y distinguir hechos, inferencias y datos faltantes. Reutilizar `GET /api/netdiag/incidents/{id}/llm-context`.

---

## 13. Implementación incremental

| Fase | Entrega | Reutilizar | Construir |
|---|---|---|---|
| 0 | Gap e identidad | Este documento | Catálogo de IDs; decidir `cpe-status` vs `/acs` |
| 1 | Telemetría y source health | Poll óptico, ACS refresh, source runs, puente `subscription_acs` | Serie óptica; preset `gigafiber-wifi-telemetry` (`2 PERIODIC`); `findByGenieacsDeviceId`; series `acs_wifi_*`; watcher `_lastInform` **sin CR**; `quality_status` |
| 2 | Contrato con tráfico | APIs `/traffic/bandwidth/v1/*` | Consumidor idempotente por `event_id` |
| 3 | Identity graph | Campos actuales de `subscription` | `identity_link` versionado + conflictos |
| 4 | Vista 360 | Bandwidth, OnuDetail, ACS, CPE, NOC | API `service-health` + composición UI |
| 5 | Correlación | Anomalías + `CorrelationEngine` | `diagnosis_code`, evidence, `missing_evidence` |
| 6 | Blast radius | Incidentes padre NetDiag | Relación incidente → abonados |
| 7 | Runbooks seguros | Reboot ONU/ACS ya auditados | Separar observar vs actuar; no acción por LLM |

Fases 4–5 no bloquean por Huawei offline: `WIFI_QUALITY` puede ser `UNSUPPORTED` / `missing_evidence` hasta GPV de ese modelo. ZTE/VSOL ya demostraron RSSI on-demand; el histórico se llena cuando el preset Inform esté en prod.

---

## 14. Pruebas y aceptación

| Criterio | Estado actual | Meta |
|---|---|---|
| Identidad correcta ante cambio de IP/queue/ONU/PON | PARCIAL | Ningún histórico se mezcla |
| Parámetro ACS/OID ausente → `UNSUPPORTED`/`MISSING`/`ERROR` | NO | No romper el ciclo ni volver cero |
| Fuentes stale reducen confidence y aparecen en `missing_evidence` | Solo tráfico | Todas las fuentes |
| Distinguir GPON, óptica, plan, Wi-Fi, ACS, router, telemetría | NO automático | Escenarios controlados |
| Falla masiva → incidente padre + afectados | PARCIAL (NetDiag) | Sin tormenta de notificaciones |
| Backoffice sin regresiones traffic/ONU/CPE | Traffic y ONU sí; CPE API faltante | Cerrar contrato CPE |
| Conteo rotulado como dispositivos asociados | Cumple (campo aún no existe) | Test de UI impide «personas» |
| Acciones remotas con rol, confirmación y audit | PARCIAL | Ninguna solo por motor/LLM |
| ACS: refreshObject de `AssociatedDevice` en ZTE/VSOL | Verificado prod 2026-08-29 | Test de integración con allowlist |
| ACS: provision horario **sin** `Hosts.Host.*` | `default` ya lo prohíbe | Test del provision `gigafiber-wifi-telemetry` |
| ACS: Inform sin puente → unmapped, no sample | — | Test del watcher |
| ACS: `findByGenieacsDeviceId` → `subscription_id` | Índice existe; query no | Test de repositorio + watcher |
| ACS: no persistir hostnames | — | Test de proyección |

---

## 15. Instrucción para implementación

Construye el diagnóstico técnico convergente reutilizando oltgateway, genieacs, netdiag, traffic y los componentes existentes del backoffice.

**Antes de modificar**

1. Este documento es el inventario EXISTE / PARCIAL / NO EXISTE.
2. Por modelo, usar el capability map de la sección 4.2 (ZTE/VSOL verificados en prod; Huawei pendiente de Inform).
3. Identidad: puente ACS de la 4.7 para series; `identity_link` versionado en Fase 3. Contrato con el Documento 1 (nombres reales de la sección 2.2).
4. Recolección ACS: secciones 6.1–6.3. No CR masivo. No `Hosts.Host.*` en provisions.

**Implementa por fases:** fuente/quality/frescura → identity graph → vista 360 → correlación explicable → incidentes compartidos y extensión del NOC → runbooks seguros.

**No** dupliques la recolección de tráfico. **No** llames «personas» a dispositivos asociados. **No** diseñes ranking de MB por estación sobre ACS. Toda conclusión entrega `evidence`, `missing_evidence`, `confidence` y siguiente prueba segura.

---

## Resumen ejecutivo

| Tema | Decisión |
|---|---|
| Responsabilidad | Diagnosticar con múltiples fuentes. Tráfico es síntoma, no causa. |
| Tráfico | Entrada del Documento 1 ya implementado; no segundo collector. |
| Fuentes | OLT/SNMP + ACS/GenieACS + MikroTik/netdiag + dominio ISP. |
| ACS (prod) | ZTE/VSOL: conteo y RSSI en Inform horario (preset, por construir) + on-demand. Bytes por estación no. Huawei offline. |
| Enlace ACS | `subscription_acs.genieacs_device_id` + `tr069_device_id` + tag `sub-{id}`. Lookup inverso por construir. |
| Identidad | `subscription_id` + enlaces ONU/ACS/IP/queue versionados (por construir). |
| Salida | Causa probable, confidence, evidence, faltantes, alcance y siguiente prueba. |
| Backoffice | Componer Bandwidth Intelligence, OnuDetail, CpeManagement y NOC. |
| Incidentes | Padre compartido, blast radius y supresión sin ocultar afectados. |
| Dispositivos | `associated_device_count`; nunca personas; no persistir hostnames. |
| Acciones | Separadas del diagnóstico, autorizadas y auditadas. Nunca por LLM. |

FIN DE LA ESPECIFICACIÓN v1.2
