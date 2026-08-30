# Diagnóstico técnico convergente — complemento al Documento 2

**Fuente:** spec canónica [02-especificacion-diagnostico-tecnico-convergente.md](./02-especificacion-diagnostico-tecnico-convergente.md) **v1.2** (Word v1.0 queda histórico).  
**Fecha de inventario:** 2026-08-29  
**Alcance:** backend `ispadmin-backend`, backoffice `ispadmin-backoffice`, Android `IpsAdmin`. Observability-web queda fuera (APM de software, no ISP).

Este archivo **no reemplaza** la especificación. Corrige y completa la sección 4 con el inventario del código, el contrato de tráfico (Documento 1) y, en v1.2, el puente ACS↔suscripción, el trigger Inform y las series Wi-Fi.

---

## 1. Lectura de la especificación

El Documento 2 define una **vista técnica única** por suscripción que combina:

| Dominio | Rol en el diagnóstico |
|---|---|
| Tráfico (Documento 1) | Síntoma / evidencia temporal. No causa autónoma. |
| OLT / SNMP | Acceso GPON, óptica, planta, fallas compartidas. |
| ACS / GenieACS | CPE, ruta de gestión TR-069, calidad Wi-Fi. |
| MikroTik / netdiag | Transporte, router, uplinks, salud de red. |
| Dominio ISP | Identidad, plan, NAP, topología, historial de cambios. |

**Principio rector (se mantiene):** diagnosticar es explicar con evidencia, frescura, datos faltantes y confianza. Una anomalía de consumo puede iniciar el análisis; no determina la causa.

**Semántica obligatoria (se mantiene):** conteo de equipos = `associated_device_count` / `connected_device_count`. Nunca «personas».

La sección 4 del Word ya intenta un inventario EXISTE/PARCIAL. Ese mapa **sobreestima** ACS/CPE unificado, histórico óptico y la madurez de la capa convergente. El resto de este documento es el inventario corregido.

---

## 2. Veredicto

La plataforma tiene **cuatro pilares operativos maduros y desacoplados**. No existe la capa que la spec llama diagnóstico convergente: ni identidad versionada, ni `service_health`, ni API 360, ni motor que una OLT + ACS + tráfico + netdiag en un `diagnosis_code`.

| Pilar | Estado | Qué no hay que rehacer |
|---|---|---|
| Bandwidth Intelligence (Doc. 1) | **EXISTE** | Collector, rollups, anomalías, health de source, UI `/bandwidth-intelligence` |
| OLT Gateway | **EXISTE** (snapshot) | Inventario, poll óptico 5 min, live status, reboot, UI OnuDetail |
| NetDiag / NOC | **EXISTE** (router/OLT) | Poll MikroTik, incidentes padre/hijo, ack/resolve/silence, logs OLT, LLM context |
| Dominio ISP + ACS de provisión | **EXISTE** | `subscription`, plan, NAP, ONU SN, `subscription_acs`, refresh/reboot ACS |

| Capa que pide la spec | Estado |
|---|---|
| Identidad convergente versionada | **NO EXISTE** |
| Puente ACS↔suscripción (`subscription_acs` + `tr069_device_id` + tag `sub-{id}`) | **EXISTE** (heurística de provisión; falta `findByGenieacsDeviceId`) |
| `telemetry_source_run` unificado | **PARCIAL** (un run por dominio) |
| `service_health_event` + `evidence_link` | **NO EXISTE** |
| `capability_profile` por modelo/firmware | **NO EXISTE** |
| Motor `diagnosis_code` / `missing_evidence` / confidence cruzado | **NO EXISTE** |
| Vista 360 de suscripción | **NO EXISTE** |
| Telemetría Wi-Fi / associated devices | **NO EXISTE** (camino: preset `2 PERIODIC` + watcher + `acs_wifi_*`; spec 6.1–6.3) |
| Serie histórica óptica por ONU | **NO EXISTE** |
| API unificada `GET /subscription/{id}/cpe-status` | **NO EXISTE** (la UI del backoffice ya la llama) |

---

## 3. Contrato con el Documento 1 (ya implementado)

La spec prohíbe un segundo collector de queues y un cálculo alternativo de P95. Eso ya está cubierto. El diagnóstico **consume** estos artefactos; no los duplica.

### 3.1 Nombres reales vs nombres de la spec

| Spec | Implementación actual | Notas |
|---|---|---|
| `traffic_observation` | `subscription_traffic_sample` | `collected_at`, `sample_status`, `source_run_id`, `queue_id`, plan Mbps |
| `traffic_aggregate` | `subscription_traffic_five_minute` / `_hourly` / `_daily` / `_monthly` | P95, utilización, `seconds_over_80/90/95`, `coverage_pct` |
| `traffic_anomaly_event` | `traffic_anomaly_event` | Coincide. Incluye `confidence` y `evidence_json` |
| `traffic_source_health` | `traffic_source_run` + `GET /traffic/bandwidth/v1/sources` | No hay tabla `*_health`; el health se deriva del último run |
| `observed_at` | **No existe** | Se usa `bucket_start` + `collected_at` |
| `quality_status` global | **No existe** | `TrafficSampleStatus` (OK/MISSING/INVALID/STALE) solo en tráfico |

### 3.2 Tipos de anomalía (ya en `TrafficAnomalyService`)

| Código spec | Estado | Interpretación actual |
|---|---|---|
| `TRAFFIC_DROP` | EXISTE | Caída vs mediana+MAD de la misma hora |
| `NO_TRAFFIC` | EXISTE | Cero consumo con cobertura |
| `TRAFFIC_MISSING` | EXISTE | Falta de muestra / cobertura del collector |
| `PLAN_SATURATION` | EXISTE | Utilización y segundos sobre 80/90/95 |
| `PATTERN_DEVIATION` | EXISTE | Desvío vs mismo weekday |
| `TRAFFIC_SPIKE` | EXISTE (extra) | No está en la spec; conservar |

### 3.3 APIs a consumir (no reimplementar)

```
GET /traffic/bandwidth/v1/overview
GET /traffic/bandwidth/v1/series
GET /traffic/bandwidth/v1/subscriptions
GET /traffic/bandwidth/v1/subscriptions/{id}
GET /traffic/bandwidth/v1/sources
GET /traffic/bandwidth/v1/anomalies
GET /subscription/{id}/traffic
GET /subscription/{id}/traffic/latest|today|day|summary
WS  /subscription-traffic/start
```

Cadencia: poll de colas cada 60 s (`traffic.poll.interval-ms`); anomalías cada 300 s; rollups 5 min / hora / nightly 03:30.

**Regla de implementación:** el diagnóstico referencia `event_id` de `traffic_anomaly_event` y las series por `subscription_id`. Si `freshness=STALE` o `coverage_pct` baja, reduce confidence y lo lista en `missing_evidence`.

---

## 4. Inventario corregido de fuentes

### 4.1 OLT / SNMP — EXISTE (snapshot), PARCIAL (serie y correlación)

| Dato spec | Estado | Dónde | Cadencia |
|---|---|---|---|
| run / match state | EXISTE | `olt_mgr_onu_status_current.run_state`, `match_state` | Inventario ~10 min |
| last down cause | EXISTE | `last_down_cause` | Inventario ~10 min |
| ONU RX / TX, OLT RX | EXISTE | `onu_rx_dbm`, `onu_tx_dbm`, `olt_rx_dbm` + `signal_category` | Señal ~5 min |
| temperatura | EXISTE | `temperature_c` | Señal ~5 min |
| distancia | EXISTE | `distance_m` | Señal ~5 min |
| bias | PARCIAL | SNMP + DTO live `biasCurrentMa`; **no persistido** | Live on-demand |
| voltaje | PARCIAL | DTO live; no en `status_current` | Live on-demand |
| MAC count | NO EXISTE | — | — |
| traps OLT | PARCIAL | `RecentOltSnmpTrapBuffer` in-memory; NetDiag `net_diag_trap_event` | UDP opcional |
| PON / ONT | EXISTE | `OltMgrOnu` board/port/onu_index | Inventario |
| serie óptica histórica | NO EXISTE | Solo current state. History de UI = auditoría de acciones | — |

**Clases:** `OltSignalPollService`, `OltInventorySyncService`, `OltMgrOnu`, `OltMgrOnuStatusCurrent`, `OltMgrSyncRun`.  
**APIs:** `/api/olt-gateway/*`, `/onu/configured`, `/onu/configured/{id}/status`, `/onu/configured/{id}/history`, reboot.

**Implicación para la spec:** la regla «RX degrada en tendencia + flaps» **no se puede evaluar hoy**. Fase 1 debe persistir serie óptica tipada (`onu_rx`, `olt_rx`, `onu_tx`, temp, bias) por suscripción/ONU, no solo el snapshot.

### 4.2 ACS / GenieACS — EXISTE (provisión), NO EXISTE (telemetría)

| Dato spec | Estado | Dónde |
|---|---|---|
| Last Inform | EXISTE (on-demand) | `subscription_acs.last_inform_at` vía refresh NBI |
| modelo / firmware | EXISTE | `product_class`, `software_version`, `hardware_version`, OUI |
| last boot | PARCIAL | `last_boot_at` (`_lastBoot`); no uptime continuo |
| WAN | PARCIAL | `wan_ip_cache` + IP de suscripción; sin estado WAN periódico |
| radios / canal / estándar | NO EXISTE | Solo SPV de SSID/password en alta |
| clientes asociados | NO EXISTE | — |
| métricas por estación | NO EXISTE | — |
| poll ACS periódico | NO EXISTE | Projection NBI: `_id,_lastInform,_lastBoot,_deviceId,ConnectionRequestURL` |

**APIs reales:** `GET/POST /subscription/{id}/acs`, `/acs/refresh`, `/acs/reboot`, `/acs/retry-tr069`.

**Hueco de contrato UI:** el backoffice `CpeManagement` llama `GET /subscription/{id}/cpe-status` y `PUT /subscription/{id}/cpe-config`. **Esos endpoints no existen en el backend.** Hoy hay piezas equivalentes dispersas (`/acs`, reboot ONU, provisión TR-069). Fase 0/1 debe unificar ese contrato o adaptar la UI a los endpoints existentes.

**Capability map:** `Tr069ModelProfile` existe para **escritura** de WAN/Wi-Fi por `productClass`. No es el `capability_profile` de la spec (qué se puede **leer** y con qué calidad).

### 4.2.1 Qué puede devolver ACS — verificado en prod 2026-08-29

NBI `127.0.0.1:7557` en VPS. 6 CPE en GenieACS. Sin `refreshObject` el cache solo tenía SSIDs. Con `refreshObject` + GPV + Connection Request:

| CPE | Last Inform | CR | Hosts / Wi-Fi count | Señal por estación | MB por estación |
|---|---|---|---|---|---|
| ZTE F6600R A (online) | 2026-08-29 | 200 | **SÍ** — 4 hosts, todos `802.11`; `TotalAssociations` 3 (2.4) + 1 (5) | **SÍ** — RSSI −80…−51, SNR 17–47, rate, noise | **NO** — `X_ZTE-COM_WLAN_Bytes*` = 0. Sí hay paquetes Tx/Rx y bytes del **radio** |
| VSOL V2804AX15T (online) | 2026-08-29 | 200 | **SÍ** — 6 hosts (2 Active; 5×`802.11` + 1 Ethernet); `TotalAssociations` 1 | **SÍ** — `X_HW_RSSI=-30`, SNR 16, noise −46, Rx/Tx rate | **NO** — no hay bytes por estación. `WLAN.TotalBytesSent` saturó UINT32 (`4294967295`) |
| ZTE F6600R B (online) | 2026-08-29 | 200 | **SÍ** — 2 hosts `802.11`; `TotalAssociations` 2 + 0 | **SÍ** — RSSI −68/−59/−58, SNR 30–40 | **NO** — bytes por estación = 0; paquetes sí |
| ZTE F6600R C | 2026-08-26 | 202 (cola) | No se leyó | No se leyó | — |
| Huawei HG8145X6 | 2026-08-26 | 202 (cola) | Offline. En export: `Hosts.X_HW_RSSI` / `X_HW_Stats` sin valor | Pendiente GPV cuando Informe | Pendiente |
| ZTE F6600R D | 2026-08-26 | 202 (cola) | No se leyó | No se leyó | — |

Allowlist de lectura (no persistir nombres de host ni SSID):

- Conteo: `Hosts.HostNumberOfEntries`, `Hosts.Host.{i}.Active`, `InterfaceType`, `WLANConfiguration.{i}.TotalAssociations`
- Señal ZTE: `AssociatedDevice.{i}.AssociatedDeviceRssi`, `X_ZTE-COM_WLAN_SNR`, `X_ZTE-COM_WLAN_Noise`, `AssociatedDeviceRate`
- Señal VSOL: `AssociatedDevice.{i}.X_HW_RSSI`, `X_HW_SNR`, `X_HW_Noise`, `X_HW_RxRate`, `X_HW_TxRate`
- Actividad (no MB): ZTE `X_ZTE-COM_WLAN_PacketSend/Received`. Bytes de radio: `WLANConfiguration.{i}.Stats.BytesSent/Received`

Implicaciones:

- `associated_device_count` es viable en ZTE y VSOL. Es equipos, no personas. No persistir `HostName` ni `X_ZTE-COM_AssociatedDeviceName`.
- El Inform periódico **no** trae hosts ni RSSI. GenieACS no tiene webhook: el trigger es un **preset** `gigafiber-wifi-telemetry` con evento `2 PERIODIC` (spec 6.1). **Prohibido** `Hosts.Host.*` en el provision (timeout 50 ms / ~90% CPU).
- Calidad Wi-Fi por estación: declare `AssociatedDevice.*` en el Inform horario; CR solo en botón «actualizar Wi-Fi».
- Ranking de MB por dispositivo **no**. El consumo por abonado sigue siendo MikroTik (Documento 1).
- Huawei prod no contestó CR (último Inform 26 ago). Repetir GPV cuando esté online.

### 4.2.2 Enlace ACS ↔ suscripción

GenieACS no tiene FK a MySQL. El backend ya persiste el puente al alta/reintento TR-069:

| Llave | Tabla / sitio | Uso |
|---|---|---|
| `genieacs_device_id` | `subscription_acs` (PK = `subscription_id`) | Canónica. Índice existe. Falta `findByGenieacsDeviceId`. |
| `tr069_device_id` | `subscription` | Operativa. Fallback. |
| tag `sub-{id}` | GenieACS `_tags` | UI/NBI. No crear puente desde el tag. |

Match: últimos 6 hex de `fiberOnu.sn` (`Tr069SerialMatcher`). Sin fila si no hay `deviceId`. Informs sin puente = `unmapped`, no sample.

### 4.3 MikroTik / netdiag — EXISTE (NOC), PARCIAL (evidencia por servicio)

| Dato spec | Estado | Dónde |
|---|---|---|
| CPU | EXISTE | `/system/resource` en `net_diag_probe_run.payload` |
| memoria | PARCIAL | Live en `/network-devices` (core); no umbral/alerta NetDiag |
| uptime / reboot inesperado | EXISTE | `UNEXPECTED_REBOOT` |
| interfaces | PARCIAL | running/disabled/type; **sin errores/drops** |
| Netwatch | EXISTE | `UPSTREAM_PROBE_FAIL` |
| syslog | EXISTE | UDP + `POST /syslog/ingest` |
| DDM SFP | EXISTE | `OPTICAL_RX_LOW`, `OPTICAL_TX_FAULT` |
| reachability | PARCIAL | `POLL_STALE`, `COMMAND_ERROR`; no ping por suscripción |
| incidente padre / supresión | EXISTE | `CorrelationEngine` + `parent_target_id` |
| mantenimiento | EXISTE | `net_diag_maintenance_window` |

NetDiag diagnostica **targets de red** (router, uplink, PON), no el servicio de un abonado. El puente más cercano es `WispAdminOntSubscriptionAdapter` / contexto LLM GPON.

**No consultar MikroTik por cliente desde diagnóstico.** El tráfico por suscripción ya sale del collector del Documento 1.

### 4.4 Dominio ISP — EXISTE (identidad actual), NO EXISTE (grafo versionado)

| Identificador | Dónde hoy | Versionado |
|---|---|---|
| `subscription_id` | `subscription` + FKs tráfico/ACS | No |
| ONU SN | `fiberOnu.sn`, `olt_mgr_onu.sn`, `subscription_acs.smartolt_serial` | No |
| ACS deviceId | `subscription_acs.genieacs_device_id`, `subscription.tr069_device_id` | No |
| IP | `subscription.ip` (matching de queue) | No |
| queue | `subscription_traffic_sample.queue_id` | Por muestra, no como link |
| router | `subscription.hostDevice` | No |
| NAP | `subscription.napBox` | No |
| OLT / PON | `OltMgrOnu.board/port` | No |
| zona | `OltMgrZone` (inventario OLT); no en suscripción | No |
| MAC WAN | No persistida como identidad | — |

No hay `identity_link` con `valid_from` / `valid_to` / `source` / `confidence`. El puente ACS operativo (4.2.2) basta para las series Wi-Fi. Los conflictos de matching ACS (sufijo de serial) ya se rechazan en provisión; no hay cola de revisión diagnóstica.

---

## 5. Inventario de backoffice (componer, no clonar)

| Pantalla spec | Ruta | Estado real | Reutilizar |
|---|---|---|---|
| TrafficAnalytics | `/traffic-analytics` | PARCIAL: analítica agregada de red; **sin entrada en sidebar** | Gráficos de perfil horario. No es el cockpit de consumo. |
| Bandwidth Intelligence | `/bandwidth-intelligence` + `/subscriptions/:id` | EXISTE: overview, series, ranking, collectors, anomalías con `confidence`/`evidence`/`freshness` | Embeber o enlazar desde la vista 360. |
| Tráfico por suscripción | modal/panel en `/subscriptions` | EXISTE: live WS + 7/30/90d | Tarjeta de consumo de la 360. |
| OnuDetail | `/onus/configured/:externalId` | PARCIAL: live óptica + reboot + history de **acciones** | Vista especializada. Falta serie óptica. |
| ONUs lista | `/onus/configured` | EXISTE: filtros `signalCategory`, sync inventario/señal | Enlace topológico. |
| CpeManagement | `/cpe` | PARCIAL: UI de búsqueda + badges GPON/ACS + forms WAN/Wi-Fi. Backend `cpe-status`/`cpe-config` **ausente** | Conservar como dueña de acciones CPE cuando el contrato exista. |
| ACS modal | `/subscriptions` | EXISTE: lastInform, deviceId, SSID cache, refresh, reboot ACS | Tarjeta ACS de la 360. |
| NOC | `/noc`, `/noc/incidents/:id` | EXISTE: KPIs, filtros, timeline, ack/resolve/silence, maintenance, copiar LLM | Extender con causa/confidence/evidence; no rehacer. |
| Logs OLT | `/noc/olt-logs` | EXISTE | Enlace desde blast radius PON. |
| Network devices | `/network-devices` | PARCIAL: CPU/RAM/tráfico live de core | No es evidencia por suscripción. |
| Vista 360 | — | **NO EXISTE** | Componer las anteriores. |
| `associated_device_count` | — | **NO EXISTE** | Backend + UI. |
| `diagnostic-json` NOC | API sí, UI no | PARCIAL | Superficie de evidencia ya disponible. |

### Android (`IpsAdmin`)

Campo operativo, no diagnóstico: búsqueda, ficha comercial, reboot ONU, restaurar conexión, tickets de asistencia. Sin óptica, tráfico, ACS, NOC ni Wi-Fi. Fuera del alcance de las fases 0–7 salvo que se pida un recorte móvil después.

---

## 6. Motor de correlación: qué se puede evaluar hoy

La tabla de la sección 8 de la spec, contrastada con datos reales:

| Causa probable spec | ¿Evaluable hoy? | Evidencia disponible | Bloqueo |
|---|---|---|---|
| Acceso GPON / energía / CPE | PARCIAL | `run_state`, `last_down_cause`, `NO_TRAFFIC`, ACS `last_inform_at` (si se refrescó) | ACS no se barre; no hay `service_health` |
| Planta externa / degradación óptica | NO | Snapshot RX/TX actual | Sin serie ni flaps persistidos |
| Falla compartida PON/OLT/uplink | PARCIAL | NetDiag padre/hijo + alarmas OLT + logs | No agrupa suscripciones afectadas en un incidente de servicio |
| Demanda / saturación de plan | SÍ | `PLAN_SATURATION`, P95, utilización, óptica current (si se consulta) | Falta cruzar con router sano de forma automática |
| Cobertura / capacidad Wi-Fi | NO en producto | Capability ZTE/VSOL verificado | Falta preset Inform + series `acs_wifi_*` + watcher (spec 6.1–6.3) |
| Ruta de gestión TR-069 (ACS stale) | PARCIAL | `last_inform_at` en refresh manual | Sin poll; Internet puede estar OK sin que nadie lo sepa |
| Cuello MikroTik / transporte | PARCIAL | CPU, Netwatch, DDM, incidentes NOC | Sin drops/errores de interfaz; no ligado a `subscription_id` |
| Falla de telemetría | SÍ (tráfico) / PARCIAL (resto) | `TRAFFIC_MISSING`, `traffic_source_run`, `POLL_STALE` | Sin `telemetry_source_run` unificado |

---

## 7. Fases de la spec, recalibradas

La spec (sección 13) se mantiene. Cada fase arranca desde lo que **ya existe**.

| Fase | Entrega spec | Qué reutilizar | Qué construir | Dependencia |
|---|---|---|---|---|
| **0** | Gap e identidad | Este documento + pilares listados | Catálogo de IDs por suscripción; decidir contrato `cpe-status` vs `/acs` | — |
| **1** | Telemetría y source health | Poll óptico 5 min, ACS refresh, puente `subscription_acs`, source runs | Serie óptica; preset `gigafiber-wifi-telemetry` (`2 PERIODIC`); `findByGenieacsDeviceId`; series `acs_wifi_*`; watcher `_lastInform` sin CR; `quality_status` | 0 |
| **2** | Contrato con tráfico | APIs `/traffic/bandwidth/v1/*` y anomalías | Consumidor idempotente por `event_id`; no nuevo collector | 1 |
| **3** | Identity graph | Campos actuales de `subscription` / ACS / OLT | `identity_link` versionado + cola de conflictos | 0 |
| **4** | Vista 360 | Bandwidth detail, OnuDetail, ACS modal, CpeManagement, NOC | API `service-health` + página que **compone** las pantallas existentes | 1–3 |
| **5** | Correlación explicable | `TrafficAnomalyService`, `CorrelationEngine`, reason codes NetDiag | `diagnosis_code`, `confidence`, `evidence[]`, `missing_evidence[]` | 2–4 |
| **6** | Blast radius | Incidentes padre NetDiag, logs OLT, NAP/PON | Relación incidente↔suscripciones afectadas; supresión sin ocultar abonados | 5 |
| **7** | Runbooks seguros | Reboot ONU/ACS ya auditados en `SubscriptionLog` / `OltMgrAuditLog` | Separar observar vs actuar; rate limit; no acción por LLM | 4–6 |

Fases 4–5 no deben esperar Huawei. `WIFI_QUALITY` puede ser `UNSUPPORTED` / `missing_evidence` hasta GPV de ese modelo. ZTE/VSOL: series cuando el preset Inform esté en prod.

---

## 8. Contrato de salida: huecos vs campos actuales

| Campo spec | Equivalente hoy | Gap |
|---|---|---|
| `diagnosis_code` | `traffic_anomaly_event.anomaly_type` + `net_diag_incident.reason_code` | Sin taxonomía unificada ni códigos GPON/ACS/WIFI |
| `probable_cause` | Título/reason de incidente NOC | No generado por motor cruzado |
| `confidence` | Solo anomalías de tráfico | No en óptica, ACS ni NOC UI |
| `evidence[]` | `evidence_json` (tráfico); timeline NetDiag; `diagnostic-json` (API, no UI) | Sin `evidence_link` ni payload unificado |
| `missing_evidence[]` | — | **No existe** |
| `affected_scope` | Target NetDiag (router/PON) | No scope de suscripción/ONU/NAP |
| `recommended_next_check` | Runbooks NetDiag (ops) | No expuesto por suscripción |
| `suppressing_incident_id` | `CorrelationEngine` padre | Solo intra-NetDiag |

---

## 9. Acciones remotas ya existentes

| Acción | Endpoint | Auditoría | ¿Automática? |
|---|---|---|---|
| Reboot ONU (OLT) | `PUT /subscription/reboot-fiber-onu`, `POST /onu/configured/{id}/reboot` | `SubscriptionLog.REBOOT_FIBER_ONU`, `OltMgrAuditLog` | No |
| Reboot CPE (ACS) | `POST /subscription/{id}/acs/reboot` | `subscription_acs.last_task_*` | No |
| Refresh ACS | `POST /subscription/{id}/acs/refresh` | Snapshot ACS | No |
| Retry TR-069 | `POST /subscription/{id}/acs/retry-tr069` | Provisión | No |
| Config WAN/Wi-Fi | Provisión alta / perfiles `Tr069ModelProfile` | Logs de provisión | No |
| Config CPE unificada | `PUT /subscription/{id}/cpe-config` (solo UI) | — | Endpoint backend ausente |

La spec (sección 11) se cumple en espíritu: ninguna acción disruptiva corre por inferencia. Falta formalizar rol + confirmación + rate limit en la vista 360, y no ligar runbooks LLM a mutaciones.

---

## 10. Privacidad (sin cambio)

Sigue vigente: métricas técnicas y conteos; no nombres de hosts Wi-Fi, navegación ni payloads; no inferir personas; secretos fuera de logs; contexto LLM mínimo. El puente LLM de NOC (`GET /api/netdiag/incidents/{id}/llm-context`) ya existe y debe reutilizarse con el mismo criterio.

---

## 11. Criterios de aceptación que hoy fallarían

De la sección 14 de la spec, el estado actual:

| Criterio | Hoy |
|---|---|
| Identidad correcta ante cambio de IP/queue/ONU/PON | PARCIAL: matching actual; histórico se mezcla si cambia el vínculo |
| Parámetro ACS/OID ausente → UNSUPPORTED/MISSING/ERROR | NO: muchos campos null se leen como «sin dato» |
| Fuentes stale reducen confidence y aparecen en `missing_evidence` | SOLO tráfico (`coverage`/`freshness`); resto no |
| Distinguir GPON / óptica / plan / Wi-Fi / ACS / router / telemetría | NO de forma automática |
| Falla masiva → incidente padre + afectados | PARCIAL: padre NetDiag; sin lista de suscripciones |
| Backoffice sin regresiones traffic/ONU/CPE | Traffic y ONU sí; CPE UI depende de API faltante |
| UI no dice «personas» | Cumple (el campo aún no existe) |
| Acciones remotas con rol/confirmación/audit | PARCIAL: audit en reboot; sin rate limit unificado |

---

## 12. Instrucción actualizada para implementación

1. No construir un quinto collector de tráfico.
2. No clonar OnuDetail, CpeManagement, Bandwidth Intelligence ni NOC.
3. Cerrar primero el contrato CPE (`cpe-status` / `cpe-config` o adaptar la UI a `/acs` + óptica OLT).
4. Persistir serie óptica: sin ella no hay `OPTICAL_DEGRADATION`.
5. ACS Wi-Fi: preset `2 PERIODIC`, no CR masivo, no `Hosts.Host.*`. Watcher por `genieacs_device_id`.
6. Introducir `identity_link` y `service_health` como capa nueva, leyendo de los silos. El puente ACS (4.2.2) no se reescribe.
7. Toda conclusión debe devolver `evidence`, `missing_evidence`, `confidence` y siguiente prueba segura.
8. `associated_device_count` solo cuando el modelo ACS lo exponga; rotular dispositivos, nunca personas.

---

## Referencias de código

- Tráfico: `traffic/service/BandwidthIntelligenceService.kt`, `TrafficAnomalyService.kt`, `V33__bandwidth_intelligence.sql`
- OLT: `oltgateway/service/OltSignalPollService.kt`, `OltInventorySyncService.kt`, `OltMgrOnuStatusCurrent.kt`
- ACS: `wispadmin/service/genieacs/SubscriptionAcsOpsService.kt`, `SubscriptionAcsSyncService.kt`, `Tr069SerialMatcher.kt`, `GenieAcsSubscriptionTagger.kt`, `GenieAcsClient.kt`, `V26__subscription_acs.sql`
- NOC: `netdiag/service/CorrelationEngine.kt`, `NetDiagPollService.kt`
- Backoffice: `features/bandwidth-intelligence/`, `pages/OnuDetail.tsx`, `pages/CpeManagement.tsx`, `pages/Noc.tsx`
