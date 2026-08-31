# Diagnóstico técnico convergente — as-built (Documento 2)

Estado: **piloto vivo en prod (verificado 2026-08-31)**. WAR con módulo opt-in; `.env` tiene `SERVICE_HEALTH_ENABLED=true` y allowlist `2310,2328`. Preset ACS `gigafiber-wifi-telemetry` aplicado a dos F6600R (`ZTEGDC47BF8F`, `ZTEGDC47DAD1`). Notificaciones compartidas siguen en false.

Spec origen: `02_Especificacion_Diagnostico_Tecnico_Convergente_GigaFiber.docx` (política) y [02-especificacion-diagnostico-tecnico-convergente.md](./02-especificacion-diagnostico-tecnico-convergente.md) (v1.2 contrastada con código).  
Productor de tráfico: [01-implementacion-analitica-consumo-ancho-banda.md](./01-implementacion-analitica-consumo-ancho-banda.md).  
Deploy: [deploy-prod-service-health-2026-08-30.md](./deploy-prod-service-health-2026-08-30.md).

Alcance: backend `servicehealth` + backoffice `src/features/service-health/`. Android y observability-web no se modificaron.

## Entregas y activación por fase

| Fase | Implementación | Activación / límite |
| --- | --- | --- |
| 0 | Dominio `servicehealth`, repositorios internos, contrato CPE compatible, permisos ADMIN/TECHNICIAN, capacidades explícitas | Los GET consultan datos guardados; no envían comandos. WAN solo ADMIN. Formularios bloqueados sin capacidad explícita. |
| 1 | Histórico óptico por lectura, eventos de inventario y alarmas OLT persistidas, watcher ACS, conteos/estaciones, perfiles de lectura y corridas | `enabled`, `optical-enabled`, `acs-enabled` y lista piloto. Memoria y errores/drops reutilizan las sondas NetDiag. |
| 2 | Consumo de anomalías por `lastEvaluatedAt` + ID, solapamiento de 120 s, actualización de cierres/reaperturas | No recalcula bytes, P95 ni anomalías. Conserva `TRAFFIC_SPIKE`. |
| 3 | Vínculos con vigencia desde verificación, observación de persistencia de Subscription/SubscriptionAcs, reconciliación periódica y conflictos | Sin asignación retrospectiva. Resolución ADMIN auditada después de corregir el dominio de provisión. |
| 4 | `/subscriptions/:id/service-health`, panel de tráfico existente, calidad por fuente, series y timeline separados, enlaces CPE/ONU/NOC | Se puede utilizar con correlación y acciones desactivadas. Incluye tendencias ópticas en OnuDetail. |
| 5 | Siete reglas deterministas, evidencia congelada, confianza categórica, faltantes y siguiente comprobación | `correlation-enabled=false` inicialmente. No depende de LLM ni ejecuta acciones. |
| 6 | Reutilización de padres NetDiag, agrupación de tres ONU, afectados y recuperación individual, mantenimiento y JSON en NOC | `shared-incidents-enabled=false` inicialmente. Sin nuevas notificaciones. Respeta ack/silence y no reabre por la misma caída un padre resuelto manualmente. |
| 7 | Refresh manual Wi-Fi, configuración TR-069, seguimiento de confirmación, idempotencia y límites compartidos con aliases | `actions-enabled=false`, `config-enabled=false`. Reinicios sin una lectura posterior de boot no se consideran confirmados. |

Las fases 0–7 están en el WAR y en el backoffice de prod. Las banderas permiten activarlas por separado. El esquema V35+V36 es aditivo: aplicarlo a mano; no activar Flyway sobre el historial completo. Hibernate `ddl-auto=update` puede crear las tablas al arrancar. V36 hay que aplicarlo **antes o con** el WAR que rekeyea Wi-Fi por `observed_at` (si no, coexisten dos UNIQUE y el upsert queda ambiguo).

### Contrato con Documento 1 (qué consume de verdad)

El lector no llama a MikroTik. Toma el último `subscription_traffic_sample` (Mbps + `sample_status`) y `traffic_source_run`, más `traffic_anomaly_event` OPEN copiados a `service_traffic_evidence`.

| Evento Documento 1 | Uso en `DiagnosisEngine` |
|--------------------|--------------------------|
| `PLAN_SATURATION` | Único `diagnosis_code` de tráfico: exige GPON online, óptica sana y CPU sana; enlace a `/bandwidth-intelligence/subscriptions/{id}` |
| `TRAFFIC_DROP` / `NO_TRAFFIC` / `TRAFFIC_SPIKE` / `PATTERN_DEVIATION` | No generan código propio. Tráfico 0 con muestra OK → estado `internet=UNKNOWN`, nunca `GPON_DOWN` |
| `TRAFFIC_MISSING` / collector stale | `TELEMETRY_GAP` si el collector de tráfico o ACS/OLT está STALE/ERROR |
| Mbps fresco > 0 + ACS Inform stale | `ACS_STALE` (gestión, no corte de Internet) |

Códigos propios del motor: `GPON_DOWN`, `OPTICAL_DEGRADATION`, `WIFI_QUALITY`, `ACS_STALE`, `PLAN_SATURATION`, `ROUTER_CAPACITY`, `TELEMETRY_GAP`. Confianza categórica `LOW`/`MEDIUM`/`HIGH` (no 0–1). El conteo Wi-Fi se llama `associated_device_count`; la UI no usa «personas».

## Huecos vs Word / v1.2

- Notificaciones de incidente compartido: flag aparte, default false; `BlastRadiusService` no dispara avisos nuevos.
- `TRAFFIC_DROP` no abre un diagnóstico de corte por sí solo (cumple la prohibición del Word).
- MAC count OLT no existe. Bytes por estación ACS: `UNSUPPORTED`. Huawei B/C Wi-Fi: `UNSUPPORTED` hasta GPV validado.
- Preset `gigafiber-wifi-telemetry` **aplicado** en prod a los dos F6600R del piloto (2026-08-31).
- Piloto de 72 h, carga NBI y precisión por firmware: en curso (allowlist `2310,2328`).

## Componentes principales

Backend: `src/main/kotlin/com/dscorp/wispadmin/servicehealth/`.

- `HealthEvidenceReader`: solo repositorios/cache. Separa estado GPON, óptica, ACS y tráfico. Tráfico antiguo se interpreta en America/Lima; ACS e interfaces nuevas usan UTC. `UtcInstantType` aplica UTC a la persistencia de este dominio sin cambiar la zona JDBC de los módulos anteriores.
- `OltHistoryService`: recibe observaciones sin cambiar los colectores existentes; valores ópticos constantes generan nuevas muestras. Ausencias permanecen null. `OltAlarmHistoryService` consume alarmas ya almacenadas en NetDiag.
- `AcsTelemetryService` y `WifiTelemetry`: proyección de hojas permitidas, lotes acotados, timestamps por parámetro y seguimiento persistente. Relee una misma sesión para completar el cache. Muestras y cursor comparten transacción.
- `IdentityService`: puente canónico ACS con fallback no contradictorio. El bloqueo de la suscripción serializa reconciliaciones. No elige la suscripción con menor ID.
- `HealthEvaluationService`, `DiagnosisEngine`, `BlastRadiusService`: consumo incremental de tráfico, reglas explicables y relaciones con NOC. Las evidencias de conclusiones anteriores se conservan al cambiar identidad o evidencia.
- `RemoteActionService`, `HealthAccess`, `LegacyTechnicalActionFilter`: reserva transaccional antes del I/O, permisos y seguimiento. Las confirmaciones verifican también la identidad física y ACS congelada al pedir la acción.

El login TECHNICIAN dirige a CPE y limita las rutas a las vistas técnicas habilitadas; no concede acceso a administración financiera. La eliminación de ONU no se ofrece a ese perfil en la vista vinculada.

Backoffice: `src/features/service-health/`; reutiliza `SubscriptionTrafficPanel`. Las series ópticas se separan visualmente al cambiar ONU/PON. El conteo Ethernet permanece desconocido si no hay una medición de esa semántica; nunca se obtiene por resta.

## API

Rutas relativas al contexto existente `/ispadmin`:

- `GET /subscription/{id}/cpe-status`: contrato camelCase existente, GPON/ACS separados y capacidades explícitas. 404 si no existe la suscripción; parcial si faltan fuentes.
- `GET /subscription/{id}/service-health`: resumen y reglas habilitadas, `diagnosis_code`, `missing_evidence`, confianza y siguiente comprobación.
- `GET /subscription/{id}/service-health/series?from=...&to=...`: óptica, conteos y señal; máximo 90 días, 24 h por defecto.
- `GET /subscription/{id}/service-health/timeline?page=0&size=50&from=...&to=...`: eventos paginados y últimas acciones de la ventana.
- `GET /onu/configured/{externalId}/optical-series`: histórico de la ONU física; no mezcla otra ONU que haya pertenecido al mismo abonado.
- `POST /subscription/{id}/acs/wifi-refresh`: petición manual limitada.
- `PUT /subscription/{id}/cpe-config`: WAN/Wi-Fi con validación completa previa.
- `GET /subscription/{id}/service-health/actions/{actionId}`: estado y confirmación por sección.
- `GET /api/netdiag/incidents/{id}/affected-subscriptions?page=0&size=50`: afectados, recuperación, alcance y diagnóstico. Conserva la protección API-key de NetDiag cuando esté habilitada, además del bearer del operador.
- `GET /service-health/identity-conflicts?page=0` y `POST /service-health/identity-conflicts/{id}/resolve`: ADMIN; resolución con `subscriptionId` y `reason`.

Acciones requieren bearer, `X-Confirm-Action: true` e `Idempotency-Key` estable para el intento. HTTP 202 significa pendiente. `CONFIRMED` requiere evidencia posterior; `FAILED` indica rechazo conocido; `UNVERIFIED` conserva incertidumbre y no dispara un reintento. WAN y Wi-Fi tienen estados separados.

Con el módulo habilitado, los aliases de reinicio/provisión también pasan por la reserva y quedan restringidos a las suscripciones piloto. Fuera del piloto rechazan la acción; con `SERVICE_HEALTH_ENABLED=false` se conserva el comportamiento legado para reversión. Revisar esta restricción operativa antes de activar el piloto. Los aliases devuelven `X-Service-Health-Action-Id` para consultar la auditoría.

## Configuración inicial

Todas estas banderas tienen valor `false` por defecto:

```properties
SERVICE_HEALTH_ENABLED=false
SERVICE_HEALTH_OPTICAL_ENABLED=false
SERVICE_HEALTH_ACS_ENABLED=false
SERVICE_HEALTH_CORRELATION_ENABLED=false
SERVICE_HEALTH_SHARED_INCIDENTS_ENABLED=false
SERVICE_HEALTH_ACTIONS_ENABLED=false
SERVICE_HEALTH_CONFIG_ENABLED=false
SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS=
SERVICE_HEALTH_PILOT_ACS_DEVICE_IDS=
SERVICE_HEALTH_STATION_HMAC_KEY=
```

La lista de suscripciones vacía recolecta cero abonados. `PILOT_ACS_DEVICE_IDS` permite observar también equipos piloto sin puente y contabilizarlos como `unmapped`; no les fabrica una suscripción. La clave HMAC debe ser un secreto estable de al menos 32 bytes: se exige al activar ACS/acciones. No guardarla en Git ni rotarla sin planificar la discontinuidad de `station_key`.

Valores ajustables bajo `service.health`: ACS 120 s (solo lectura de cache NBI; **no** persiste Wi-Fi si el Inform no trajo parámetros frescos), evaluación/tráfico/alarma/agrupación 60 s; Inform esperado 3600 s; frescura óptica 600 s y estado 1200 s. Degradación: 3 dB/24 h, 12 muestras y dos flaps. Wi-Fi: RSSI < -75 dBm o SNR < 20 dB en dos lecturas recientes. Saturación reutiliza cobertura de `TrafficProperties`, óptica de `SignalCategoryCalculator` y CPU de `NetDiagProperties`.

La clave de `acs_wifi_count_sample` es `(device_id, subscription_id, observed_at)` del `TotalAssociations` (V36). `inform_at` guarda el Inform de esa sesión CWMP. Un Inform posterior sin GPV Wi-Fi no inserta `MISSING` ni pisa `acs_wifi_status_current`. `lastInform` de `subscription_acs` sí se actualiza cada ciclo.

Retención: óptica/conteos 90 días, estaciones 14, corridas 30 y eventos terminados 180. Los vínculos históricos se conservan. Acciones y conflictos se conservan como auditoría. Los resúmenes de evidencia en eventos sobreviven a la purga de muestras técnicas.

## GenieACS

Archivos: `scripts/genieacs/provisions/gigafiber-wifi-telemetry.js` y `scripts/genieacs/apply-wifi-telemetry.py`.

El provision usa `path` y `value` recientes de forma separada, siguiendo la [documentación de GenieACS](https://docs.genieacs.com/en/stable/provisions.html). No recorre `Hosts.Host.*`; permite el escalar `HostNumberOfEntries`. No pide SSID, contraseña, nombre de estación ni un subtree completo. Las MAC de asociación solo existen transitoriamente en memoria para obtener HMAC contextualizado por suscripción; no se almacenan en claro en este dominio.

Primero revisar el preset sin enviar cambios:

```sh
python3 scripts/genieacs/apply-wifi-telemetry.py --device-id '<deviceId-piloto>'
```

Solo durante el despliegue autorizado, añadir `--apply` y configurar `GENIEACS_NBI_URL`. El preset tiene evento `2 PERIODIC`, canal propio, peso 20 y filtro explícito de IDs/modelos. No dispara CR. Reversión del preset: `--disable --apply`.

## Límites deliberados y validación pendiente

- Huawei y modelos no validados: identidad/frescura disponible, Wi-Fi `UNSUPPORTED`.
- La proyección está limitada a 32 instancias de estación por radio. Si hay más, el conteo se conserva y se registra `STATION_LIMIT_EXCEEDED`; la señal por estación es parcial. Las lecturas NBI se fraccionan para no exceder el tamaño de URL; medir este coste en el piloto.
- Los perfiles de lectura registran modelo/firmware, pero no inventan una validación de firmware: `verified_at` permanece vacío hasta validación operativa.
- No existe en el código inspeccionado un canal seguro de modificación WAN OMCI equivalente a TR-069. `canWriteWanViaOmci=false`; no se inventaron comandos. Esta capacidad queda pendiente de un adaptador validado.
- WAN TR-069 admite la asignación IP/VLAN actual. Migraciones de asignación deben pasar primero por provisión; no se cambia silenciosamente la identidad de tráfico.
- Una contraseña write-only o un firmware que no refresque valores tras configurar puede terminar `UNVERIFIED`. Aceptar una tarea no se presenta como configuración aplicada.
- El evaluador recorre la lista piloto cada minuto para vencer frescura. El consumo de anomalías sí usa cursor incremental; una cola de suscripciones modificadas para flotas mayores queda como optimización posterior.
- La revisión ADMIN certifica una corrección hecha en el dominio de provisión; no ofrece una reasignación forzada de historial ni modifica la autoridad canónica desde diagnóstico.
- No se han ejecutado recorridos contra un backend/GenieACS real ni mediciones de carga, precisión de firmware o piloto de 72 h. Los recorridos de navegador usan respuestas simuladas.

## Verificación reproducible

Backend:

```sh
./mvnw -Dtest='com.dscorp.wispadmin.servicehealth.*Test,GenieAcsPilotProvisionsTest,GenieAcsClientTest,OltSignalPollServiceTest,OltInventorySyncServiceTest,TrafficAnomalyServiceTest,WispAdminOntSubscriptionAdapterTest,CorrelationEngineRos7Test,MikrotikPollAdapterTest,NetDiagLlmContextServiceTest' test
python3 scripts/tests/service-health-mysql-smoke.py
node scripts/tests/wifi-telemetry-provision.test.cjs
```

La prueba MySQL crea y elimina su propio contenedor sin puertos ni red; aplica V35 (y V36 si el smoke la incluye), comprueba unicidad, completado parcial, null y actualización/cierre/reapertura de la misma evidencia. No usa credenciales ni base de producción.

Backoffice:

```sh
npm run build
npm test -- --run src/features/service-health src/services/cpeService.test.ts src/lib/cpeStatusView.test.ts src/components/cpe/CpeOperatorPanel.test.tsx src/services/subscriptionService.acs.test.ts src/components/noc/NocIncidentDetail.test.tsx src/components/subscriptions/SubscriptionTrafficPanel.test.tsx src/components/subscriptions/SubscriptionAcsModal.test.tsx src/pages/CpeManagement.test.tsx src/components/onu/OnuConfiguredDetail.test.tsx
```

Smoke de navegador: iniciar `npm exec vite preview -- --host 127.0.0.1 --port 4178 --strictPort` y ejecutar `node scripts/e2e-service-health.mjs`. Requiere Chromium de Playwright. Intercepta las APIs y bloquea tráfico externo: valida escritorio, ancho móvil, fuentes ausentes, enlace CPE, capacidades y ausencia de escrituras al abrir la vista.

Resultados locales: 137 pruebas Kotlin/JUnit/MockK/H2 de las suites seleccionadas (incluye arranque del módulo con NetDiag ausente y almacenamiento UTC sobre JDBC Lima), 56 pruebas Vitest/Testing Library, build TypeScript/Vite, SQL V35 aplicado dos veces en MySQL 8, provision ejecutado con declaraciones simuladas y smoke Playwright de escritorio/móvil con APIs interceptadas. No equivalen a validación contra equipos reales ni a ejecutar toda la suite del repositorio.

## Secuencia de despliegue y reversión

1. Respaldar MySQL. En un piloto ya creado: aplicar **V36** (`src/main/resources/db/migration/V36__wifi_sample_key_observed_at.sql`) **antes** del WAR nuevo. Borra filas `MISSING`/`observed_at` null y cambia UNIQUE a `(device_id, subscription_id, observed_at)`. Instalación nueva: V35 y luego V36. No activar Flyway sobre el historial completo.
2. Desplegar backend con banderas falsas y backoffice. Confirmar GET CPE con roles permitidos, 404 y datos parciales.
3. Definir lista piloto y clave HMAC; habilitar colectores y aplicar preset a IDs explícitos. Confirmar frescura real de parámetros y ausencia de datos privados en persistencia/logs.
4. Revisar identidad y 360 antes de habilitar correlación. Luego habilitar agrupación, sin nuevas notificaciones.
5. Habilitar acciones solo después de ensayar permisos, double-click, límites, fallos parciales e incertidumbre. Configuración permanece apagada hasta ese momento.
6. Observar al menos 72 h: carga NBI/CWMP, duración de sesiones, lag, duplicados, `unmapped`, conflictos, crecimiento de tablas, recuperación parcial y falsos positivos por regla/modelo.
7. Para revertir, deshabilitar banderas nuevas y preset. Conservar tablas y evidencia; no borrar datos ni alterar colectores anteriores.
