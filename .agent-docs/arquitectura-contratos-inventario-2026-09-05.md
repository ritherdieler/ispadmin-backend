# Inventario estático de contratos — 2026-09-05

Snapshot de declaraciones HTTP al inicio de la auditoría. Las correcciones posteriores están en [correcciones-arquitectura-2026-09-05.md](./correcciones-arquitectura-2026-09-05.md) y el estado H1–H10 en [auditoria-arquitectura-contratos-2026-09-05.md](./auditoria-arquitectura-contratos-2026-09-05.md). **No regenerar esta tabla como si siguiera siendo el código actual.**

Delta relevante respecto de este snapshot:

- Traffic ya no hace `INSERT … SELECT` al schema core; consume `/internal/traffic/routers` por HTTP.
- `HealthOltGatewayHttpClient` / `NetDiagOltGatewayHttpClient` no importan `wispadmin`; URIs con `InternalUris`.
- El WAR OLT Gateway no importa el core; `OnuWriteRouter` se eliminó.
- Flyway: `V39` único (`onu.unique_external_id`); dieta NetDiag en `V47`.
- Service Health, NetDiag y Observability siguen embebidos; la matriz de imports `wispadmin` de abajo describe adapters in-process, no clientes HTTP.

Extraído del árbol de trabajo local, incluidos cambios sin commit. Las anotaciones son declaraciones de código: no equivalen a rutas activas verificadas en Tomcat. `RequestMapping` incluye prefijos de clase; combinarlo con las rutas de método. No se expanden perfiles ni aliases. Incluye Kotlin; no incluye clientes externos de otros repositorios.

## Cobertura por paquete

| Paquete | Controladores con mappings | Mappings HTTP de método (Get/Post/Put/Patch/Delete) | Interfaces |
|---|---:|---:|---:|
| acs | 1 | 6 | 1 |
| events | 0 | 0 | 3 |
| netdiag | 1 | 16 | 17 |
| observability | 18 | 53 | 14 |
| oltgateway | 3 | 42 | 19 |
| routeros | 0 | 0 | 2 |
| servicehealth | 1 | 13 | 25 |
| traffic | 6 | 39 | 21 |
| wispadmin | 56 | 358 | 70 |

## Dependencias entre paquetes

Son imports, no necesariamente llamadas remotas. `routeros` y `events` son infraestructura compartida.

| Origen | Destino | Imports |
|---|---|---:|
| netdiag | routeros | 16 |
| netdiag | servicehealth | 6 |
| netdiag | wispadmin | 8 |
| observability | wispadmin | 10 |
| oltgateway | events | 12 |
| servicehealth | events | 9 |
| servicehealth | wispadmin | 17 |
| traffic | events | 8 |
| traffic | routeros | 9 |
| wispadmin | events | 3 |
| wispadmin | routeros | 17 |

## Controladores y mappings

### acs · AcsCpeController

Fuente: [AcsCpeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/acs/controller/AcsCpeController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/acs/v1"` | 16 |
| GetMapping | `"/health"` | 20 |
| PostMapping | `"/cpe/provision"` | 23 |
| GetMapping | `"/cpe/{sn}/status"` | 26 |
| GetMapping | `"/cpe/{sn}/telemetry"` | 29 |
| PostMapping | `"/cpe/{sn}/reboot"` | 32 |
| PostMapping | `"/cpe/{sn}/wifi-refresh"` | 35 |

### netdiag · NetDiagController

Fuente: [NetDiagController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/controller/NetDiagController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/netdiag"` | 44 |
| GetMapping | `"/health"` | 58 |
| GetMapping | `"/incidents"` | 64 |
| GetMapping | `"/incidents/summary"` | 83 |
| GetMapping | `"/incidents/{id}"` | 90 |
| GetMapping | `value = ["/incidents/{id}/llm-context"], produces = [MediaType.TEXT_PLAIN_VALUE, "text/markdown"]` | 97 |
| GetMapping | `"/incidents/{id}/diagnostic-json"` | 107 |
| PostMapping | `"/incidents/{id}/ack"` | 114 |
| PostMapping | `"/incidents/{id}/resolve"` | 121 |
| PostMapping | `"/incidents/{id}/silence"` | 128 |
| GetMapping | `"/maintenance-windows"` | 139 |
| PostMapping | `"/maintenance-windows"` | 146 |
| DeleteMapping | `"/maintenance-windows/{id}"` | 164 |
| PostMapping | `"/alerts/ingest"` | 171 |
| PostMapping | `"/traps/ingest"` | 191 |
| PostMapping | `"/syslog/ingest"` | 198 |
| GetMapping | `"/olt/logs"` | 210 |

### observability · ObservabilityAlertController

Fuente: [ObservabilityAlertController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityAlertController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/alerts"` | 23 |
| GetMapping | `"/rules"` | 28 |
| PostMapping | `"/rules"` | 31 |
| PutMapping | `"/rules/{id}"` | 39 |
| DeleteMapping | `"/rules/{id}"` | 48 |
| GetMapping | `"/channels"` | 53 |
| PostMapping | `"/channels"` | 56 |
| PutMapping | `"/channels/{id}"` | 64 |
| DeleteMapping | `"/channels/{id}"` | 73 |
| PostMapping | `"/channels/{id}/test"` | 78 |
| GetMapping | `"/events"` | 84 |

### observability · ObservabilityDatabaseController

Fuente: [ObservabilityDatabaseController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityDatabaseController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/database"` | 16 |
| GetMapping | `"/queries"` | 22 |
| GetMapping | `"/nplusone"` | 42 |

### observability · ObservabilityEventController

Fuente: [ObservabilityEventController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityEventController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability"` | 19 |
| PostMapping | `"/events"` | 25 |

### observability · ObservabilityIssueController

Fuente: [ObservabilityIssueController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityIssueController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/issues"` | 27 |
| GetMapping | `` | 33 |
| GetMapping | `"/{id}"` | 49 |
| GetMapping | `"/{id}/latest-event"` | 55 |
| GetMapping | `"/{id}/occurrences"` | 61 |
| PatchMapping | `"/{id}/status"` | 70 |
| PostMapping | `"/{id}/ticket"` | 80 |
| PostMapping | `"/{id}/jira"` | 90 |
| DeleteMapping | `"/{id}/ticket"` | 100 |

### observability · ObservabilityJiraController

Fuente: [ObservabilityJiraController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityJiraController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/jira"` | 11 |
| GetMapping | `"/status"` | 16 |
| PostMapping | `"/test"` | 22 |

### observability · ObservabilityLlmContextController

Fuente: [ObservabilityLlmContextController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityLlmContextController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability"` | 13 |
| GetMapping | `"/issues/{id}/llm-context"` | 18 |
| GetMapping | `"/traces/{traceId}/llm-context"` | 25 |
| GetMapping | `"/sessions/{sessionId}/llm-context"` | 32 |

### observability · ObservabilityMetricController

Fuente: [ObservabilityMetricController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityMetricController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/metrics"` | 21 |
| GetMapping | `"/endpoints"` | 29 |
| GetMapping | `"/endpoints/detail"` | 40 |
| GetMapping | `"/timeseries"` | 52 |
| GetMapping | `"/web-vitals"` | 63 |
| GetMapping | `"/web-vitals/timeseries"` | 74 |
| GetMapping | `"/system"` | 87 |
| GetMapping | `"/system/latest"` | 97 |

### observability · ObservabilityReleaseController

Fuente: [ObservabilityReleaseController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityReleaseController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/releases"` | 21 |
| GetMapping | `` | 27 |
| PostMapping | `` | 36 |
| GetMapping | `"/{version}/summary"` | 42 |

### observability · ObservabilityReplayController

Fuente: [ObservabilityReplayController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityReplayController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/replays"` | 21 |
| PostMapping | `` | 27 |
| GetMapping | `"/{id}"` | 82 |

### observability · ObservabilityRumController

Fuente: [ObservabilityRumController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityRumController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability"` | 18 |
| PostMapping | `"/rum"` | 25 |

### observability · ObservabilitySessionController

Fuente: [ObservabilitySessionController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilitySessionController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/sessions"` | 17 |
| GetMapping | `` | 22 |
| GetMapping | `"/{sessionId}"` | 35 |

### observability · ObservabilitySpanController

Fuente: [ObservabilitySpanController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilitySpanController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability"` | 18 |
| PostMapping | `"/spans"` | 24 |

### observability · ObservabilityStatsController

Fuente: [ObservabilityStatsController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityStatsController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/stats"` | 14 |
| GetMapping | `"/overview"` | 19 |
| GetMapping | `"/events-timeseries"` | 29 |

### observability · ObservabilitySymbolController

Fuente: [ObservabilitySymbolController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilitySymbolController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/symbols"` | 23 |
| PostMapping | `"/sourcemaps"` | 30 |
| PostMapping | `"/proguard"` | 63 |
| GetMapping | `` | 92 |
| DeleteMapping | `"/{id}"` | 98 |

### observability · ObservabilityTraceController

Fuente: [ObservabilityTraceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityTraceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/traces"` | 19 |
| GetMapping | `` | 25 |
| GetMapping | `"/{traceId}"` | 46 |

### observability · ObservabilityTrackerController

Fuente: [ObservabilityTrackerController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityTrackerController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/tracker"` | 9 |
| GetMapping | `"/info"` | 14 |

### observability · ObservabilityTrackerWebhookController

Fuente: [ObservabilityTrackerWebhookController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityTrackerWebhookController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/tracker"` | 17 |
| PostMapping | `"/{provider}/webhook"` | 26 |

### observability · ObservabilityWorkflowController

Fuente: [ObservabilityWorkflowController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/controller/ObservabilityWorkflowController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/observability/workflows"` | 14 |
| GetMapping | `` | 19 |

### oltgateway · OltGatewayController

Fuente: [OltGatewayController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/controller/OltGatewayController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/olt-gateway"` | 65 |
| GetMapping | `"/health"` | 82 |
| GetMapping | `"/olt/info"` | 95 |
| GetMapping | `"/onus/autofind"` | 119 |
| GetMapping | `"/onus/by-sn/{sn}"` | 142 |
| GetMapping | `"/onus"` | 170 |
| GetMapping | `"/onus/configured"` | 189 |
| PostMapping | `"/admin/sync/inventory"` | 258 |
| PostMapping | `"/admin/sync/snmp-inventory"` | 292 |
| PostMapping | `"/admin/sync/signal"` | 325 |
| PostMapping | `"/admin/import/smartolt"` | 358 |
| GetMapping | `"/admin/snmp/traps/recent"` | 384 |
| GetMapping | `"/admin/sync/status"` | 425 |
| GetMapping | `"/onus/{slot}/{port}/{ontId}"` | 457 |
| GetMapping | `"/onus/{slot}/{port}/{ontId}/optical"` | 485 |
| GetMapping | `"/onus/configured/{externalId}"` | 516 |
| GetMapping | `"/onus/configured/{externalId}/status"` | 525 |
| GetMapping | `"/onus/configured/{externalId}/history"` | 534 |
| GetMapping | `"/onus/catalog"` | 546 |
| GetMapping | `"/onus/catalog/boards-ports"` | 550 |
| GetMapping | `"/descriptor"` | 557 |
| GetMapping | `"/health-onus/by-sn"` | 561 |
| GetMapping | `"/health-onus/by-external-id"` | 565 |
| GetMapping | `"/health-onus/by-position"` | 569 |
| GetMapping | `"/health-onus/by-olt/{oltId}"` | 578 |
| GetMapping | `"/olts/id-by-name"` | 582 |
| GetMapping | `"/onus/pon"` | 587 |
| GetMapping | `"/onus/pon/one"` | 595 |
| PostMapping | `"/admin/alarms/poll"` | 604 |
| PostMapping | `"/admin/alarms/parse"` | 608 |
| PostMapping | `"/admin/lab-optical/refresh"` | 613 |

### oltgateway · OnuActivationController

Fuente: [OnuActivationController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/controller/OnuActivationController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/olt-gateway"` | 24 |
| PostMapping | `"/onu/activate"` | 31 |
| GetMapping | `"/onus/by-sn/{sn}/activation"` | 36 |
| GetMapping | `"/onus/by-external-id/{externalId}/activation"` | 41 |
| PostMapping | `"/onus/{sn}/cpe/reboot"` | 46 |
| PostMapping | `"/onus/{sn}/cpe/wifi-refresh"` | 49 |
| GetMapping | `"/onus/{sn}/cpe/telemetry"` | 52 |

### oltgateway · SmartOltCompatController

Fuente: [SmartOltCompatController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/controller/SmartOltCompatController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/olt-gateway"` | 23 |
| GetMapping | `"/onu/unconfigured_onus"` | 31 |
| GetMapping | `"/onu/get_onus_details_by_sn/{sn}"` | 35 |
| PostMapping | `path = ["/onu/authorize_onu"], consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE]` | 40 |
| PostMapping | `path = ["/onu/move/{sn}"], consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE]` | 75 |
| PostMapping | `"/onu/delete/{externalId}"` | 89 |
| PostMapping | `"/onu/reboot/{externalId}"` | 94 |

### servicehealth · ServiceHealthController

Fuente: [ServiceHealthController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/controller/ServiceHealthController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| GetMapping | `"/subscription/{id}/cpe-status"` | 34 |
| GetMapping | `"/subscription/{id}/service-health"` | 51 |
| GetMapping | `"/subscription/{id}/service-health/series"` | 57 |
| GetMapping | `"/subscription/{id}/service-health/timeline"` | 79 |
| GetMapping | `"/onu/configured/{externalId}/optical-series"` | 93 |
| PostMapping | `"/subscription/{id}/service-health/reboot"` | 108 |
| PostMapping | `"/subscription/{id}/acs/wifi-refresh"` | 114 |
| PostMapping | `"/subscription/{id}/service-health/optical-refresh"` | 119 |
| PutMapping | `"/subscription/{id}/cpe-config"` | 125 |
| GetMapping | `"/subscription/{id}/service-health/actions/{actionId}"` | 131 |
| GetMapping | `"/api/netdiag/incidents/{id}/affected-subscriptions"` | 138 |
| GetMapping | `"/service-health/identity-conflicts"` | 147 |
| PostMapping | `"/service-health/identity-conflicts/{id}/resolve"` | 152 |

### traffic · BandwidthIntelligenceController

Fuente: [BandwidthIntelligenceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/BandwidthIntelligenceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/traffic/bandwidth/v1"` | 14 |
| GetMapping | `"/network"` | 16 |
| GetMapping | `"/overview"` | 17 |
| GetMapping | `"/series"` | 18 |
| GetMapping | `"/subscriptions"` | 19 |
| GetMapping | `"/subscriptions/{id}"` | 20 |
| GetMapping | `"/sources"` | 21 |
| GetMapping | `"/anomalies"` | 22 |

### traffic · NetworkTrafficAnalyticsController

Fuente: [NetworkTrafficAnalyticsController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/NetworkTrafficAnalyticsController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/traffic/network"` | 13 |
| GetMapping | `"/hourly-profile"` | 17 |
| GetMapping | `"/daily-trend"` | 21 |
| GetMapping | `"/insights"` | 25 |

### traffic · SubscriptionTrafficController

Fuente: [SubscriptionTrafficController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/SubscriptionTrafficController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/subscription"` | 20 |
| GetMapping | `"/{id}/traffic"` | 25 |
| GetMapping | `"/{id}/traffic/latest"` | 36 |
| GetMapping | `"/{id}/traffic/summary"` | 42 |
| GetMapping | `"/{id}/traffic/today"` | 50 |
| GetMapping | `"/{id}/traffic/day"` | 56 |

### traffic · SubscriptionTrafficPollController

Fuente: [SubscriptionTrafficPollController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/SubscriptionTrafficPollController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/traffic"` | 12 |
| PostMapping | `"/poll"` | 17 |
| PostMapping | `"/aggregation/catch-up"` | 20 |

### traffic · TrafficApiController

Fuente: [TrafficApiController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/TrafficApiController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/traffic/v1"` | 29 |
| GetMapping | `"/by-subscription/{id}/latest"` | 41 |
| GetMapping | `"/by-subscription/{id}/series"` | 44 |
| GetMapping | `"/by-subscription/{id}/summary"` | 52 |
| GetMapping | `"/by-subscription/{id}/today"` | 58 |
| GetMapping | `"/by-subscription/{id}/day"` | 61 |
| GetMapping | `"/by-ip/{ip}/latest"` | 67 |
| GetMapping | `"/by-ip/{ip}/series"` | 70 |
| GetMapping | `"/network"` | 78 |
| GetMapping | `"/overview"` | 87 |
| GetMapping | `"/series"` | 96 |
| GetMapping | `"/sources"` | 105 |
| GetMapping | `"/subscriptions"` | 108 |
| GetMapping | `"/subscriptions/{id}"` | 120 |
| GetMapping | `"/network/hourly-profile"` | 128 |
| GetMapping | `"/network/daily-trend"` | 131 |
| GetMapping | `"/network/insights"` | 134 |
| GetMapping | `"/anomalies"` | 137 |
| GetMapping | `"/routers/{id}/latest-run"` | 147 |
| GetMapping | `"/anomalies/changes"` | 157 |
| GetMapping | `"/config"` | 178 |
| PostMapping | `"/admin/poll"` | 181 |
| PostMapping | `"/admin/aggregation/catch-up"` | 184 |

### traffic · SubscriptionTrafficWebSocket

Fuente: [SubscriptionTrafficWebSocket](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/websocket/SubscriptionTrafficWebSocket.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| MessageMapping | `"/subscription-traffic/start"` | 54 |
| MessageMapping | `"/subscription-traffic/stop"` | 84 |

### wispadmin · AppManagementController

Fuente: [AppManagementController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AppManagementController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/management"` | 20 |
| PostMapping | `"app_force_logout"` | 24 |

### wispadmin · AppVersionController

Fuente: [AppVersionController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AppVersionController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/app"` | 11 |
| GetMapping | `"check_version"` | 15 |

### wispadmin · AssistanceTicketController

Fuente: [AssistanceTicketController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AssistanceTicketController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/assistanceTicket"` | 27 |
| GetMapping | `"/byDateRange"` | 41 |
| PutMapping | `"/assignTicketToUser"` | 58 |
| PutMapping | `"/{id}/status"` | 112 |
| GetMapping | `"/{id}/conversation"` | 158 |
| PutMapping | `"/closeAttendedTicket"` | 165 |
| PutMapping | `"/closeUnattendedTicket"` | 220 |
| GetMapping | `"/findAll"` | 276 |
| GetMapping | `"/find"` | 282 |
| PostMapping | `` | 288 |
| DeleteMapping | `"/{id}"` | 331 |
| PutMapping | `"/{id}"` | 341 |
| PutMapping | `"/{id}/reschedule"` | 367 |

### wispadmin · AttendanceController

Fuente: [AttendanceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AttendanceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/attendance"` | 10 |
| GetMapping | `"getAll"` | 16 |
| PostMapping | `"save"` | 23 |
| DeleteMapping | `"/{id}"` | 26 |
| GetMapping | `"byUser/{userId}"` | 29 |

### wispadmin · AttendanceLogController

Fuente: [AttendanceLogController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AttendanceLogController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/attendance-logs"` | 8 |
| GetMapping | `` | 11 |
| PostMapping | `` | 14 |
| DeleteMapping | `"/{id}"` | 17 |

### wispadmin · CouponController

Fuente: [CouponController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CouponController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/cupon"` | 15 |
| PostMapping | `` | 20 |
| GetMapping | `` | 24 |

### wispadmin · CrmConversationController

Fuente: [CrmConversationController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CrmConversationController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/crm/conversations"` | 31 |
| GetMapping | `` | 37 |
| GetMapping | `"/{id}"` | 63 |
| GetMapping | `"/by-phone/{phone}"` | 72 |
| PostMapping | `"/{id}/claim"` | 78 |
| PostMapping | `"/{id}/release"` | 91 |
| PostMapping | `"/{id}/transfer"` | 114 |
| PostMapping | `"/{id}/resolve"` | 138 |
| PostMapping | `"/{id}/reopen"` | 162 |
| GetMapping | `"/{id}/assignments"` | 185 |
| GetMapping | `"/{id}/notes"` | 194 |
| PostMapping | `"/{id}/notes"` | 203 |
| PostMapping | `"/{id}/pause-bot"` | 225 |
| PostMapping | `"/{id}/resume-bot"` | 238 |
| PostMapping | `"/{id}/suggest-reply"` | 251 |

### wispadmin · CrmCsatController

Fuente: [CrmCsatController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CrmCsatController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/crm/csat"` | 22 |
| GetMapping | `"/summary"` | 28 |
| GetMapping | `"/surveys"` | 37 |
| GetMapping | `"/follow-ups"` | 47 |
| PutMapping | `"/follow-ups/{id}"` | 56 |
| PostMapping | `"/follow-ups/{id}/reopen-ticket"` | 84 |

### wispadmin · CrmMetricsController

Fuente: [CrmMetricsController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CrmMetricsController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/crm/metrics"` | 14 |
| GetMapping | `"/summary"` | 19 |
| GetMapping | `"/agents"` | 28 |
| GetMapping | `"/shift-handoff"` | 37 |

### wispadmin · CrmOpenAiSettingsController

Fuente: [CrmOpenAiSettingsController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CrmOpenAiSettingsController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/crm/settings/openai"` | 22 |
| GetMapping | `` | 29 |
| PutMapping | `` | 38 |
| PostMapping | `"/test"` | 62 |

### wispadmin · CrmQuickReplyController

Fuente: [CrmQuickReplyController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CrmQuickReplyController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/crm/quick-replies"` | 22 |
| GetMapping | `` | 27 |
| PostMapping | `` | 34 |
| PutMapping | `"/{id}"` | 42 |
| DeleteMapping | `"/{id}"` | 51 |

### wispadmin · CrmTicketController

Fuente: [CrmTicketController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CrmTicketController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/crm"` | 21 |
| GetMapping | `"/conversations/{id}/tickets"` | 27 |
| PostMapping | `"/conversations/{id}/tickets"` | 32 |
| GetMapping | `"/tickets/by-phone/{phone}"` | 60 |
| GetMapping | `"/tickets/{ticketId}/conversation"` | 65 |

### wispadmin · DashBoardController

Fuente: [DashBoardController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/DashBoardController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/dashboard"` | 16 |
| GetMapping | `` | 23 |
| GetMapping | `"/v2"` | 27 |
| GetMapping | `"/assistance-tickets"` | 31 |
| GetMapping | `"/plan-analysis"` | 35 |
| GetMapping | `"/client-quality"` | 39 |
| GetMapping | `"/fixed-cost-analysis"` | 43 |
| GetMapping | `"/installation-orders"` | 47 |
| GetMapping | `"/geographic-performance"` | 51 |
| GetMapping | `"/team-performance"` | 55 |
| GetMapping | `"/network-health"` | 59 |
| GetMapping | `"/client-lifecycle"` | 63 |
| GetMapping | `"/complete-dashboard"` | 67 |
| GetMapping | `"/subscription/locations"` | 71 |
| GetMapping | `"/subscription/active-by-type"` | 115 |

### wispadmin · FaceDataController

Fuente: [FaceDataController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FaceDataController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/face-data"` | 36 |
| GetMapping | `` | 61 |
| GetMapping | `"/offline-dataset"` | 76 |
| GetMapping | `"/user/{userId}/exists"` | 108 |
| PostMapping | `` | 120 |
| PostMapping | `"/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 169 |
| PostMapping | `"/photo/enroll", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 224 |
| PostMapping | `"/photo/enroll/multi-angle", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 274 |
| PostMapping | `"/photo/check", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 365 |
| GetMapping | `"/admin/embedding-inventory"` | 397 |
| PostMapping | `"/admin/reset-embeddings"` | 417 |

### wispadmin · FaceEvidenceController

Fuente: [FaceEvidenceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FaceEvidenceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/face/evidence"` | 15 |
| PostMapping | `` | 20 |

### wispadmin · FaceVerifyController

Fuente: [FaceVerifyController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FaceVerifyController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/face"` | 22 |
| PostMapping | `"/challenge/start"` | 29 |
| PostMapping | `"/identify"` | 34 |
| PostMapping | `"/identify/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 40 |
| PostMapping | `"/verify"` | 48 |
| PostMapping | `"/verify/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 54 |
| PostMapping | `"/verify/password"` | 66 |
| PostMapping | `"/attendance/offline-sync"` | 72 |

### wispadmin · FcmController

Fuente: [FcmController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FcmController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/fcm"` | 17 |
| PostMapping | `"save-token"` | 23 |
| GetMapping | `"get-token"` | 27 |
| PostMapping | `"sendNotification/{registrationToken}"` | 31 |

### wispadmin · FilterRuleController

Fuente: [FilterRuleController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FilterRuleController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/filter-rules"` | 13 |
| GetMapping | `"/debt-cut/{deviceId}"` | 24 |
| PostMapping | `"/enable"` | 52 |
| PostMapping | `"/disable"` | 71 |
| PostMapping | `"/enable/{deviceId}/{entryId}"` | 90 |
| PostMapping | `"/disable/{deviceId}/{entryId}"` | 110 |

### wispadmin · FixedCostController

Fuente: [FixedCostController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FixedCostController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/fixed_cost"` | 13 |
| PostMapping | `"/"` | 19 |
| GetMapping | `"/"` | 27 |
| DeleteMapping | `"/{id}"` | 31 |
| PutMapping | `"/{id}"` | 38 |

### wispadmin · InstallationOrderController

Fuente: [InstallationOrderController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/InstallationOrderController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/installation-order"` | 14 |
| GetMapping | `"/{id}"` | 19 |
| PostMapping | `` | 28 |
| PutMapping | `"/{id}/assign"` | 34 |
| PutMapping | `"/{id}/schedule"` | 50 |
| PutMapping | `"/{id}/close"` | 62 |
| DeleteMapping | `"/{id}"` | 71 |
| PutMapping | `"/{id}/cancel"` | 81 |
| GetMapping | `"/all-paginated"` | 93 |
| GetMapping | `"/seller/{sellerId}"` | 102 |
| GetMapping | `"/technician/{technicianId}"` | 112 |
| PutMapping | `"/{id}/transfer"` | 122 |

### wispadmin · IpPoolController

Fuente: [IpPoolController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/IpPoolController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/ip-pool"` | 11 |
| PostMapping | `` | 17 |
| GetMapping | `` | 21 |
| GetMapping | `"get-ips"` | 25 |
| DeleteMapping | `"/{id}"` | 29 |
| PutMapping | `"/{id}/activate"` | 35 |
| PutMapping | `"/{id}/deactivate"` | 39 |

### wispadmin · LogViewerController

Fuente: [LogViewerController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/LogViewerController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/logs"` | 116 |
| GetMapping | `"/files"` | 125 |
| GetMapping | `"/{fileName}"` | 149 |
| GetMapping | `"/errors"` | 178 |
| GetMapping | `"/modules"` | 222 |
| GetMapping | `"/search"` | 230 |
| GetMapping | `"/db/errors"` | 267 |
| GetMapping | `"/db/stats"` | 303 |
| GetMapping | `"/http-failures"` | 331 |
| GetMapping | `value = ["/viewer"], produces = [MediaType.TEXT_HTML_VALUE]` | 416 |

### wispadmin · MainController

Fuente: [MainController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MainController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/"` | 9 |
| GetMapping | `` | 13 |

### wispadmin · MockOltDebugController

Fuente: [MockOltDebugController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MockOltDebugController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/debug/olt"` | 10 |
| GetMapping | `"/status"` | 18 |
| PostMapping | `"/reset"` | 46 |

### wispadmin · MufaController

Fuente: [MufaController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MufaController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/mufa"` | 14 |
| PostMapping | `` | 19 |
| GetMapping | `` | 23 |

### wispadmin · NapBoxController

Fuente: [NapBoxController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NapBoxController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/napbox"` | 10 |
| PostMapping | `` | 15 |
| GetMapping | `` | 19 |
| GetMapping | `"/near"` | 23 |

### wispadmin · NetworkDeviceConnectionController

Fuente: [NetworkDeviceConnectionController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NetworkDeviceConnectionController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/networkDevice/connection"` | 10 |
| GetMapping | `"/cloud-core-routers"` | 16 |
| GetMapping | `"/{deviceId}/interfaces"` | 20 |
| GetMapping | `"/{deviceId}/system-info"` | 28 |
| GetMapping | `"/{deviceId}/resources"` | 36 |
| GetMapping | `"/{deviceId}/info"` | 44 |

### wispadmin · NetworkDeviceController

Fuente: [NetworkDeviceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NetworkDeviceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/networkDevice"` | 16 |
| PostMapping | `` | 21 |
| GetMapping | `"genericDevices"` | 25 |
| GetMapping | `"fiber-and-wireless-devices"` | 29 |
| GetMapping | `` | 33 |
| GetMapping | `"deviceTypes"` | 37 |
| GetMapping | `"coreTypes"` | 41 |

### wispadmin · OnuSmartOltController

Fuente: [OnuSmartOltController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/OnuSmartOltController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/onu"` | 19 |
| DeleteMapping | `` | 23 |
| GetMapping | `"unconfigured_onus"` | 29 |
| PostMapping | `"authorize"` | 33 |
| PostMapping | `"configured/{externalId}/reboot"` | 39 |
| DeleteMapping | `"configured/{externalId}"` | 45 |
| GetMapping | `"getBySn"` | 51 |

### wispadmin · OutLayController

Fuente: [OutLayController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/OutLayController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/outLay"` | 13 |
| GetMapping | `` | 20 |
| PostMapping | `` | 64 |
| PutMapping | `"/{id}"` | 111 |
| DeleteMapping | `"/{id}"` | 137 |

### wispadmin · PaymentController

Fuente: [PaymentController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PaymentController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/payment"` | 22 |
| GetMapping | `"/getElectronicPayers"` | 31 |
| GetMapping | `"/{id}"` | 41 |
| PutMapping | `` | 54 |
| PutMapping | `"/batch"` | 58 |
| GetMapping | `"/filtered"` | 62 |
| GetMapping | `` | 82 |
| PutMapping | `"/{id}"` | 102 |
| DeleteMapping | `"/{id}"` | 130 |
| PostMapping | `` | 153 |
| PostMapping | `"/invoice"` | 159 |
| PostMapping | `"/validate"` | 169 |

### wispadmin · PerformanceController

Fuente: [PerformanceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PerformanceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/performance"` | 11 |
| GetMapping | `"/metrics"` | 19 |
| GetMapping | `"/summary"` | 27 |
| PostMapping | `"/clear"` | 60 |
| GetMapping | `"/method/{methodName}"` | 69 |

### wispadmin · PlaceController

Fuente: [PlaceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PlaceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/place"` | 11 |
| PostMapping | `` | 16 |
| GetMapping | `"/findByLocation"` | 20 |
| GetMapping | `` | 36 |

### wispadmin · PlanController

Fuente: [PlanController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PlanController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/plan"` | 15 |
| PostMapping | `` | 25 |
| PutMapping | `` | 29 |
| GetMapping | `` | 85 |
| GetMapping | `"/all"` | 89 |
| DeleteMapping | `"/{id}"` | 106 |
| PutMapping | `"/{id}/activate"` | 118 |
| PutMapping | `"/{id}/deactivate"` | 125 |

### wispadmin · ReportController

Fuente: [ReportController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/ReportController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/report"` | 18 |
| GetMapping | `"/canceled-current-month"` | 33 |
| GetMapping | `"/canceled-past-month"` | 43 |

### wispadmin · ScheduledTaskLogController

Fuente: [ScheduledTaskLogController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/ScheduledTaskLogController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/api/scheduled-task-logs"` | 13 |
| GetMapping | `"/recent"` | 18 |
| GetMapping | `"/by-task-type/{taskType}"` | 26 |
| GetMapping | `"/by-date-range"` | 34 |
| GetMapping | `"/task-types"` | 43 |

### wispadmin · SmartMapController

Fuente: [SmartMapController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/SmartMapController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/smart-map"` | 38 |
| GetMapping | `"/summary"` | 45 |
| GetMapping | `"/suggestions"` | 79 |
| GetMapping | `"/road-route"` | 83 |
| GetMapping | `"/road-route/alternatives"` | 103 |
| GetMapping | `"/road-route/navigation"` | 123 |
| PostMapping | `"/collection-route/recalculate"` | 147 |
| PostMapping | `"/collection-visit"` | 179 |
| GetMapping | `"/collection-visit/recent"` | 188 |
| GetMapping | `"/collection-route"` | 199 |
| GetMapping | `"/collection-sweep-route"` | 233 |
| GetMapping | `"/collection-pending"` | 275 |
| GetMapping | `"/collection-places"` | 296 |
| GetMapping | `"/coverage-check"` | 304 |

### wispadmin · SmartMapIntelligenceController

Fuente: [SmartMapIntelligenceController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/SmartMapIntelligenceController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/smart-map"` | 23 |
| GetMapping | `"/coverage-zones"` | 28 |
| PostMapping | `"/coverage-zones"` | 32 |
| PutMapping | `"/coverage-zones/{id}"` | 39 |
| DeleteMapping | `"/coverage-zones/{id}"` | 47 |
| GetMapping | `"/sales-leads"` | 56 |
| PostMapping | `"/sales-leads"` | 60 |
| PutMapping | `"/sales-leads/{id}"` | 67 |
| DeleteMapping | `"/sales-leads/{id}"` | 75 |
| GetMapping | `"/commercial-opportunities"` | 84 |
| PostMapping | `"/commercial-opportunities"` | 88 |
| PutMapping | `"/commercial-opportunities/{id}"` | 95 |
| DeleteMapping | `"/commercial-opportunities/{id}"` | 103 |

### wispadmin · SubscriptionController

Fuente: [SubscriptionController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/SubscriptionController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/subscription"` | 44 |
| GetMapping | `"/findByElectronicPayerName"` | 87 |
| PutMapping | `"/changeNapBox"` | 98 |
| PutMapping | `"/reboot-fiber-onu"` | 123 |
| GetMapping | `"/{subscriptionId}"` | 147 |
| GetMapping | `"/{subscriptionId}/registration-progress"` | 158 |
| GetMapping | `"/{subscriptionId}/acs"` | 166 |
| PostMapping | `"/{subscriptionId}/acs/refresh"` | 187 |
| PostMapping | `"/{subscriptionId}/acs/reboot"` | 196 |
| PostMapping | `"/{subscriptionId}/acs/retry-tr069"` | 205 |
| PostMapping | `"/login"` | 216 |
| PostMapping | `"/generate-simple-queues"` | 225 |
| PutMapping | `"/migration"` | 243 |
| GetMapping | `"/profile"` | 258 |
| PutMapping | `"/restore-internet-connection"` | 265 |
| PutMapping | `"/reactivate-service"` | 285 |
| GetMapping | `"/{subscriptionId}/reactivation-validation"` | 300 |
| GetMapping | `"/napbox/{napBoxId}/available-bornes"` | 328 |
| GetMapping | `"/napbox/{napBoxId}/borne-status"` | 339 |
| PutMapping | `"/update-location/v2"` | 352 |
| PutMapping | `"/payment-commitment"` | 359 |
| GetMapping | `"/apply-coupon/{code}"` | 365 |
| PutMapping | `"/update-plan"` | 372 |
| PutMapping | `"/update-subscription-data"` | 392 |
| PutMapping | `"/cortarDeudores"` | 398 |
| PutMapping | `"/cancel-subscription"` | 402 |
| GetMapping | `"/provisioning-ready"` | 423 |
| PostMapping | `` | 428 |
| PostMapping | `"/with-facade-photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 457 |
| RequestMapping | `value = ["/{subscriptionId}/facade-photo"], method = [RequestMethod.PUT], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 508 |
| PutMapping | `` | 534 |
| PutMapping | `"/profile/update"` | 552 |
| PutMapping | `"/update-location"` | 573 |
| GetMapping | `"/unpaid-auto-cut"` | 637 |
| GetMapping | `"/unpaid-auto-cut-excel"` | 671 |
| GetMapping | `"debtors-with-active-subscription-report-document"` | 722 |
| GetMapping | `"debtors-with-cancelled-subscription-report-document"` | 730 |
| GetMapping | `"debtors-with-cut-report-document"` | 738 |
| GetMapping | `"with-payment-commitment-report-document"` | 745 |
| GetMapping | `"suspended-report-document"` | 752 |
| GetMapping | `"cutoff-report-document"` | 760 |
| GetMapping | `"last-month-debtors-report-document"` | 768 |
| GetMapping | `"/all"` | 852 |
| GetMapping | `"debtors"` | 856 |
| GetMapping | `"find/dni"` | 860 |
| GetMapping | `"find/nameAndLastName"` | 865 |
| GetMapping | `"find/ip"` | 883 |
| GetMapping | `"fastSearch"` | 888 |
| PutMapping | `"update-customer-data"` | 926 |
| GetMapping | `"find/date"` | 954 |
| GetMapping | `"/locations"` | 985 |
| GetMapping | `"/logs/summary"` | 1029 |
| PostMapping | `"/generate-address-list-cancelled-subscriptions"` | 1078 |

### wispadmin · TechnicianController

Fuente: [TechnicianController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/TechnicianController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/technician"` | 11 |
| PostMapping | `` | 16 |
| GetMapping | `` | 20 |

### wispadmin · Tr069ModelProfileController

Fuente: [Tr069ModelProfileController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/Tr069ModelProfileController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/admin/tr069-profiles"` | 19 |
| GetMapping | `` | 24 |
| PostMapping | `"/preview"` | 30 |
| PostMapping | `"/import"` | 44 |
| DeleteMapping | `"/{productClass}"` | 65 |

### wispadmin · UserController

Fuente: [UserController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/UserController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"users"` | 25 |
| GetMapping | `` | 38 |
| GetMapping | `"/{id}"` | 42 |
| PostMapping | `` | 52 |
| PutMapping | `"/{id}"` | 64 |
| DeleteMapping | `"/{id}"` | 95 |
| PostMapping | `"/login"` | 106 |
| PostMapping | `"/login/face"` | 120 |
| PostMapping | `"/login/face/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 127 |
| PostMapping | `"/token/refresh"` | 134 |
| PutMapping | `"/device-token"` | 157 |

### wispadmin · WhatsAppBackofficeController

Fuente: [WhatsAppBackofficeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/WhatsAppBackofficeController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/whatsapp"` | 78 |
| GetMapping | `"/events"` | 106 |
| GetMapping | `"/templates"` | 113 |
| GetMapping | `"/message-candidates"` | 118 |
| PostMapping | `"/messages/selected"` | 125 |
| GetMapping | `"/batch/{campaignId}/status"` | 149 |
| GetMapping | `"/reminder-candidates"` | 163 |
| PostMapping | `"/reminders/selected"` | 170 |
| PostMapping | `"/messages/template"` | 184 |
| PostMapping | `"/messages/{wamid:.+}/react"` | 196 |
| PutMapping | `"/messages/{wamid:.+}"` | 221 |
| DeleteMapping | `"/messages/{wamid:.+}"` | 251 |
| GetMapping | `"/registration-status"` | 279 |
| PostMapping | `"/messages/welcome/{subscriptionId}"` | 294 |
| GetMapping | `"/logs"` | 316 |
| GetMapping | `"/logs/export", produces = ["text/csv"]` | 345 |
| GetMapping | `"/logs/payment/{paymentId}"` | 370 |
| GetMapping | `"/inbound-messages"` | 377 |
| GetMapping | `"/inbound-messages/export", produces = ["text/csv"]` | 401 |
| GetMapping | `"/inbound-messages/subscription/{subscriptionId}"` | 422 |
| GetMapping | `"/analytics/overview"` | 429 |
| GetMapping | `"/analytics/overview/series"` | 444 |
| GetMapping | `"/analytics/campaigns"` | 460 |
| GetMapping | `"/analytics/campaigns/export", produces = ["text/csv"]` | 478 |
| GetMapping | `"/analytics/campaigns/{campaignId}"` | 495 |
| GetMapping | `"/analytics/conversion"` | 509 |
| GetMapping | `"/analytics/meta/templates"` | 523 |
| GetMapping | `"/analytics/meta/conversations"` | 546 |
| GetMapping | `"/analytics/meta/pricing"` | 558 |
| GetMapping | `"/account/health"` | 570 |
| GetMapping | `"/templates/sync"` | 575 |
| PostMapping | `"/templates/sync"` | 588 |
| GetMapping | `"/service-window/{phone}"` | 593 |
| GetMapping | `"/conversations"` | 598 |
| GetMapping | `"/conversations/view-counts"` | 639 |
| GetMapping | `"/conversations/{phone}/thread"` | 654 |
| GetMapping | `"/conversations/{phone}/context"` | 682 |
| PostMapping | `"/conversations/{phone}/reply"` | 687 |
| PostMapping | `"/conversations/{phone}/media", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | 722 |
| PostMapping | `"/conversations/{phone}/template"` | 759 |
| PostMapping | `"/conversations/{phone}/outbound/{logId}/retry"` | 791 |
| GetMapping | `"/outbound-messages/{id}/media"` | 817 |
| PostMapping | `"/conversations/{phone}/resume-bot"` | 836 |
| PostMapping | `"/conversations/{phone}/mark-all-read"` | 849 |
| PostMapping | `"/conversations/{id}/mark-read"` | 863 |
| GetMapping | `"/inbound-messages/{id}/media"` | 885 |

### wispadmin · WhatsAppController

Fuente: [WhatsAppController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/WhatsAppController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/whatsapp"` | 15 |
| PostMapping | `"/test-message"` | 21 |
| PostMapping | `"/test-template-message"` | 39 |

### wispadmin · WhatsAppQuickReplyController

Fuente: [WhatsAppQuickReplyController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/WhatsAppQuickReplyController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/whatsapp/quick-replies"` | 23 |
| GetMapping | `` | 27 |
| PostMapping | `` | 35 |
| PutMapping | `"/{id}"` | 43 |
| DeleteMapping | `"/{id}"` | 52 |

### wispadmin · WhatsAppWebhookController

Fuente: [WhatsAppWebhookController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/WhatsAppWebhookController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/whatsapp/webhook"` | 17 |
| GetMapping | `produces = [MediaType.TEXT_PLAIN_VALUE]` | 25 |
| PostMapping | `consumes = [MediaType.APPLICATION_JSON_VALUE]` | 39 |

### wispadmin · CreateResource

Fuente: [CreateResource](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/CreateResource.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/izipay"` | 28 |
| RequestMapping | `path = ["/createPayment"], method = [RequestMethod.POST]` | 43 |
| RequestMapping | `path = ["/createToken"], method = [RequestMethod.POST]` | 78 |

### wispadmin · VerifyResultResource

Fuente: [VerifyResultResource](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/VerifyResultResource.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/izipay"` | 26 |
| RequestMapping | `path = ["/verifyResult"], method = [RequestMethod.POST]` | 33 |

### wispadmin · OnuFacadeController

Fuente: [OnuFacadeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/oltclient/OnuFacadeController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/onu"` | 17 |
| GetMapping | `"/configured"` | 22 |
| GetMapping | `"/configured/{externalId}"` | 26 |
| GetMapping | `"/configured/{externalId}/status"` | 30 |
| GetMapping | `"/configured/{externalId}/history"` | 34 |
| GetMapping | `"/catalog"` | 38 |
| GetMapping | `"/catalog/boards-ports"` | 42 |
| PostMapping | `"/sync/inventory"` | 46 |
| PostMapping | `"/sync/signal"` | 49 |
| PostMapping | `"/import/smartolt"` | 52 |

### wispadmin · SubscriptionSearchController

Fuente: [SubscriptionSearchController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/search/controller/SubscriptionSearchController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/subscription"` | 17 |
| GetMapping | `"/search"` | 23 |
| PostMapping | `"/search/reindex"` | 33 |

### wispadmin · BandwidthIntelligenceFacadeController

Fuente: [BandwidthIntelligenceFacadeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/BandwidthIntelligenceFacadeController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| GetMapping | `"/traffic/bandwidth/v1/network"` | 19 |
| GetMapping | `"/traffic/bandwidth/v1/overview"` | 22 |
| GetMapping | `"/traffic/bandwidth/v1/series"` | 25 |
| GetMapping | `"/traffic/bandwidth/v1/sources"` | 28 |
| GetMapping | `"/traffic/bandwidth/v1/anomalies"` | 31 |
| GetMapping | `"/traffic/bandwidth/v1/subscriptions"` | 34 |
| GetMapping | `"/traffic/bandwidth/v1/subscriptions/{id}"` | 37 |
| GetMapping | `"/traffic/network/hourly-profile"` | 41 |
| GetMapping | `"/traffic/network/daily-trend"` | 44 |
| GetMapping | `"/traffic/network/insights"` | 47 |

### wispadmin · SubscriptionTrafficFacadeController

Fuente: [SubscriptionTrafficFacadeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/SubscriptionTrafficFacadeController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/subscription"` | 16 |
| GetMapping | `"/{id}/traffic"` | 21 |
| GetMapping | `"/{id}/traffic/latest"` | 25 |
| GetMapping | `"/{id}/traffic/summary"` | 29 |
| GetMapping | `"/{id}/traffic/today"` | 33 |
| GetMapping | `"/{id}/traffic/day"` | 37 |

### wispadmin · TrafficDirectoryController

Fuente: [TrafficDirectoryController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/TrafficDirectoryController.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| RequestMapping | `"/internal/traffic"` | 8 |
| GetMapping | `"/targets"` | 12 |

### wispadmin · DeviceResourcesWebSocket

Fuente: [DeviceResourcesWebSocket](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/websocket/DeviceResourcesWebSocket.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| MessageMapping | `"/resources/start"` | 59 |
| MessageMapping | `"/resources/stop"` | 159 |

### wispadmin · InterfaceTrafficWebSocket

Fuente: [InterfaceTrafficWebSocket](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/websocket/InterfaceTrafficWebSocket.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| MessageMapping | `"/test"` | 97 |
| MessageMapping | `"/traffic/start"` | 106 |
| MessageMapping | `"/traffic/stop"` | 210 |

### wispadmin · WhatsAppPresenceWebSocket

Fuente: [WhatsAppPresenceWebSocket](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/websocket/WhatsAppPresenceWebSocket.kt)

| Anotación | Declaración | Línea |
|---|---|---:|
| MessageMapping | `"/whatsapp/presence"` | 30 |

## Interfaces declaradas

Incluye repositorios y contratos internos; su presencia no implica una API entre WARs.

| Paquete | Interfaz | Fuente |
|---|---|---|
| acs | CpeRecordRepository | [CpeRecordRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/acs/repository/CpeRecordRepository.kt:5) |
| events | HealthSnapshotCache | [LiveTelemetry.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/events/LiveTelemetry.kt:5) |
| events | LiveTelemetryPort | [LiveTelemetry.kt:30](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/events/LiveTelemetry.kt:30) |
| events | EventBusPort | [PlatformEvent.kt:45](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/events/PlatformEvent.kt:45) |
| netdiag | NetDiagTargetRepository | [NetDiagRepositories.kt:24](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:24) |
| netdiag | NetDiagProbeRunRepository | [NetDiagRepositories.kt:32](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:32) |
| netdiag | NetDiagIncidentRepository | [NetDiagRepositories.kt:42](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:42) |
| netdiag | NetDiagIncidentEventRepository | [NetDiagRepositories.kt:83](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:83) |
| netdiag | NetDiagAlertDecisionRepository | [NetDiagRepositories.kt:88](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:88) |
| netdiag | NetDiagNotificationLogRepository | [NetDiagRepositories.kt:91](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:91) |
| netdiag | NetDiagAuditLogRepository | [NetDiagRepositories.kt:94](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:94) |
| netdiag | NetDiagTrapEventRepository | [NetDiagRepositories.kt:97](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:97) |
| netdiag | NetDiagOltLogEventRepository | [NetDiagRepositories.kt:102](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:102) |
| netdiag | NetDiagMaintenanceWindowRepository | [NetDiagRepositories.kt:137](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt:137) |
| netdiag | NetDiagDeviceDirectoryPort | [NetDiagDeviceDirectoryPort.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagDeviceDirectoryPort.kt:4) |
| netdiag | NetDiagOltAlarmParserPort | [NetDiagOltAlarmParserPort.kt:16](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagOltAlarmParserPort.kt:16) |
| netdiag | NetDiagOltCliPort | [NetDiagOltCliPort.kt:7](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagOltCliPort.kt:7) |
| netdiag | NetDiagOltDescriptorPort | [NetDiagOltDescriptorPort.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagOltDescriptorPort.kt:9) |
| netdiag | NetDiagOltInventoryPort | [NetDiagOltInventoryPort.kt:10](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagOltInventoryPort.kt:10) |
| netdiag | NetDiagOntSubscriptionPort | [NetDiagOntSubscriptionPort.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagOntSubscriptionPort.kt:9) |
| netdiag | NetDiagRadiusImpactPort | [NetDiagRadiusImpactPort.kt:10](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/port/NetDiagRadiusImpactPort.kt:10) |
| observability | AlertChannelPort | [AlertChannelPort.kt:18](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/port/AlertChannelPort.kt:18) |
| observability | IssueTrackerPort | [IssueTrackerPort.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/port/IssueTrackerPort.kt:4) |
| observability | ObsAlertChannelRepository | [ObsAlertChannelRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsAlertChannelRepository.kt:5) |
| observability | ObsAlertEventRepository | [ObsAlertEventRepository.kt:11](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsAlertEventRepository.kt:11) |
| observability | ObsAlertRuleRepository | [ObsAlertRuleRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsAlertRuleRepository.kt:6) |
| observability | ObsDeployEventRepository | [ObsDeployEventRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsDeployEventRepository.kt:6) |
| observability | ObsEndpointMetricRepository | [ObsEndpointMetricRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsEndpointMetricRepository.kt:9) |
| observability | ObsEventRepository | [ObsEventRepository.kt:11](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsEventRepository.kt:11) |
| observability | ObsIssueRepository | [ObsIssueRepository.kt:11](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsIssueRepository.kt:11) |
| observability | ObsReplayRepository | [ObsReplayRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsReplayRepository.kt:8) |
| observability | ObsRumMetricRepository | [ObsRumMetricRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsRumMetricRepository.kt:9) |
| observability | ObsSpanRepository | [ObsSpanRepository.kt:10](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsSpanRepository.kt:10) |
| observability | ObsSymbolArtifactRepository | [ObsSymbolArtifactRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsSymbolArtifactRepository.kt:9) |
| observability | ObsSystemMetricRepository | [ObsSystemMetricRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/observability/repository/ObsSystemMetricRepository.kt:9) |
| oltgateway | AcsCpeClient | [AcsCpeClient.kt:25](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/client/AcsCpeClient.kt:25) |
| oltgateway | OltMgrOltRepository | [OltMgrRepositories.kt:29](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:29) |
| oltgateway | OltMgrOltModelRepository | [OltMgrRepositories.kt:35](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:35) |
| oltgateway | OltMgrZoneRepository | [OltMgrRepositories.kt:40](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:40) |
| oltgateway | OltMgrOnuTypeRepository | [OltMgrRepositories.kt:45](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:45) |
| oltgateway | OltMgrOnuRepository | [OltMgrRepositories.kt:50](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:50) |
| oltgateway | OltMgrOnuStatusCurrentRepository | [OltMgrRepositories.kt:197](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:197) |
| oltgateway | OltMgrTaskRepository | [OltMgrRepositories.kt:200](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:200) |
| oltgateway | OltMgrSyncRunRepository | [OltMgrRepositories.kt:205](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:205) |
| oltgateway | OltMgrAuditLogRepository | [OltMgrRepositories.kt:210](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:210) |
| oltgateway | OltMgrOnuServicePortRepository | [OltMgrRepositories.kt:215](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:215) |
| oltgateway | OltMgrOnuExtraVlanRepository | [OltMgrRepositories.kt:221](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:221) |
| oltgateway | OltMgrCustomTemplateRepository | [OltMgrRepositories.kt:224](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:224) |
| oltgateway | OltMgrSpeedProfileRepository | [OltMgrRepositories.kt:227](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:227) |
| oltgateway | OltMgrOltPonPortRepository | [OltMgrRepositories.kt:230](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:230) |
| oltgateway | OltMgrOltVlanRepository | [OltMgrRepositories.kt:233](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/domain/repository/OltMgrRepositories.kt:233) |
| oltgateway | OltGatewayQueryFacade | [OltGatewayQueryFacade.kt:13](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/service/OltGatewayQueryFacade.kt:13) |
| oltgateway | SmartOltCatalogClient | [SmartOltCatalogClient.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/smartolt/SmartOltCatalogClient.kt:2) |
| oltgateway | OltSnmpClient | [OltSnmpClient.kt:21](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/snmp/OltSnmpClient.kt:21) |
| routeros | MikrotikClient | [MikrotikClient.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/routeros/port/MikrotikClient.kt:2) |
| routeros | MikrotikSession | [MikrotikSession.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/routeros/port/MikrotikSession.kt:2) |
| servicehealth | HealthOnuPort | [HealthEvidencePorts.kt:18](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:18) |
| servicehealth | HealthTrafficPort | [HealthEvidencePorts.kt:54](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:54) |
| servicehealth | HealthNetDiagPort | [HealthEvidencePorts.kt:103](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:103) |
| servicehealth | HealthOltIngestPort | [HealthEvidencePorts.kt:144](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:144) |
| servicehealth | HealthLabOpticalPort | [HealthEvidencePorts.kt:156](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:156) |
| servicehealth | HealthLabScopePort | [HealthEvidencePorts.kt:160](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:160) |
| servicehealth | HealthCpePort | [HealthEvidencePorts.kt:183](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/port/HealthEvidencePorts.kt:183) |
| servicehealth | OpticalSampleRepository | [HealthRepositories.kt:13](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:13) |
| servicehealth | OnuStateEventRepository | [HealthRepositories.kt:51](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:51) |
| servicehealth | WifiCountSampleRepository | [HealthRepositories.kt:73](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:73) |
| servicehealth | WifiStationSampleRepository | [HealthRepositories.kt:136](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:136) |
| servicehealth | WifiStationHourlyRepository | [HealthRepositories.kt:161](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:161) |
| servicehealth | WifiAggregationWatermarkRepository | [HealthRepositories.kt:183](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:183) |
| servicehealth | WifiCurrentRepository | [HealthRepositories.kt:185](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:185) |
| servicehealth | ReadCapabilityProfileRepository | [HealthRepositories.kt:187](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:187) |
| servicehealth | TelemetryRunRepository | [HealthRepositories.kt:190](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:190) |
| servicehealth | IdentityLinkRepository | [HealthRepositories.kt:193](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:193) |
| servicehealth | IdentityConflictRepository | [HealthRepositories.kt:197](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:197) |
| servicehealth | HealthEventRepository | [HealthRepositories.kt:201](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:201) |
| servicehealth | HealthCurrentRepository | [HealthRepositories.kt:229](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:229) |
| servicehealth | EvidenceLinkRepository | [HealthRepositories.kt:230](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:230) |
| servicehealth | HealthCursorRepository | [HealthRepositories.kt:233](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:233) |
| servicehealth | TrafficEvidenceRepository | [HealthRepositories.kt:238](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:238) |
| servicehealth | IncidentSubscriptionRepository | [HealthRepositories.kt:241](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:241) |
| servicehealth | RemoteActionRepository | [HealthRepositories.kt:247](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/repository/HealthRepositories.kt:247) |
| traffic | TrafficDirectoryPort | [TrafficDirectoryPort.kt:13](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/port/TrafficDirectoryPort.kt:13) |
| traffic | SubscriptionTrafficSampleRepository | [SubscriptionTrafficRepositories.kt:24](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:24) |
| traffic | SubscriptionTrafficRawSummaryProjection | [SubscriptionTrafficRepositories.kt:101](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:101) |
| traffic | BandwidthNetworkBucketProjection | [SubscriptionTrafficRepositories.kt:110](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:110) |
| traffic | BandwidthNetworkDayBucketProjection | [SubscriptionTrafficRepositories.kt:121](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:121) |
| traffic | SubscriptionTrafficFiveMinuteRepository | [SubscriptionTrafficRepositories.kt:132](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:132) |
| traffic | TrafficSourceRunRepository | [SubscriptionTrafficRepositories.kt:265](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:265) |
| traffic | TrafficAnomalyEventRepository | [SubscriptionTrafficRepositories.kt:270](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:270) |
| traffic | SubscriptionTrafficHourlyRepository | [SubscriptionTrafficRepositories.kt:309](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:309) |
| traffic | SubscriptionTrafficDailyRepository | [SubscriptionTrafficRepositories.kt:433](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:433) |
| traffic | SubscriptionTrafficMonthlyRepository | [SubscriptionTrafficRepositories.kt:554](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:554) |
| traffic | SubscriptionTrafficCounterStateRepository | [SubscriptionTrafficRepositories.kt:560](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:560) |
| traffic | NetworkTrafficHourOfDayRepository | [SubscriptionTrafficRepositories.kt:564](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:564) |
| traffic | NetworkHourAggregateProjection | [SubscriptionTrafficRepositories.kt:596](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:596) |
| traffic | NetworkDayAggregateProjection | [SubscriptionTrafficRepositories.kt:602](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:602) |
| traffic | TrafficAggregationWatermarkRepository | [SubscriptionTrafficRepositories.kt:608](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:608) |
| traffic | TrafficAggregationRunRepository | [SubscriptionTrafficRepositories.kt:610](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/SubscriptionTrafficRepositories.kt:610) |
| traffic | TrafficRouterRepository | [TrafficRouterRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/TrafficRouterRepository.kt:6) |
| traffic | TrafficCounterStateRepository | [TrafficRouterRepository.kt:10](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/repository/TrafficRouterRepository.kt:10) |
| traffic | TrafficEvidenceProvider | [BandwidthIntelligenceService.kt:13](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/service/BandwidthIntelligenceService.kt:13) |
| traffic | TrafficWebSocketSessionCleanup | [TrafficWebSocketSessionCleanup.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/websocket/TrafficWebSocketSessionCleanup.kt:2) |
| wispadmin | NetworkDeviceConnection | [Extensions.kt:20](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/extensions/Extensions.kt:20) |
| wispadmin | ObservabilityReporter | [ObservabilityReporter.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/observability/ObservabilityReporter.kt:2) |
| wispadmin | AppVersionRepository | [AppVersionRepository.kt:7](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/AppVersionRepository.kt:7) |
| wispadmin | AssistanceTicketRepository | [AssistanceTicketRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/AssistanceTicketRepository.kt:9) |
| wispadmin | AttendanceRepository | [AttendanceRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/AttendanceRepository.kt:8) |
| wispadmin | CollectionVisitLogRepository | [CollectionVisitLogRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CollectionVisitLogRepository.kt:8) |
| wispadmin | CommercialOpportunityRepository | [CommercialOpportunityRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CommercialOpportunityRepository.kt:8) |
| wispadmin | CorporationCustomerRepository | [CorporationCustomerRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CorporationCustomerRepository.kt:6) |
| wispadmin | CouponRepository | [CouponRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CouponRepository.kt:5) |
| wispadmin | CoverageZoneRepository | [CoverageZoneRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CoverageZoneRepository.kt:8) |
| wispadmin | CrmAssignmentEventRepository | [CrmAssignmentEventRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CrmAssignmentEventRepository.kt:8) |
| wispadmin | CrmConversationRepository | [CrmConversationRepository.kt:14](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CrmConversationRepository.kt:14) |
| wispadmin | CrmEventLogRepository | [CrmEventLogRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CrmEventLogRepository.kt:5) |
| wispadmin | CrmIntegrationSettingRepository | [CrmIntegrationSettingRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CrmIntegrationSettingRepository.kt:6) |
| wispadmin | CrmInternalNoteRepository | [CrmInternalNoteRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CrmInternalNoteRepository.kt:8) |
| wispadmin | CrmQuickReplyRepository | [CrmQuickReplyRepository.kt:7](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CrmQuickReplyRepository.kt:7) |
| wispadmin | CsatFollowUpRepository | [CsatFollowUpRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CsatFollowUpRepository.kt:6) |
| wispadmin | CsatSurveyRepository | [CsatSurveyRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/CsatSurveyRepository.kt:9) |
| wispadmin | ErrorLogRepository | [ErrorLogRepository.kt:13](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/ErrorLogRepository.kt:13) |
| wispadmin | FaceDataRepository | [FaceDataRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/FaceDataRepository.kt:8) |
| wispadmin | FcmTokenRepository | [FcmTokenRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/FcmTokenRepository.kt:5) |
| wispadmin | FixedCostRepository | [FixedCostRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/FixedCostRepository.kt:6) |
| wispadmin | InstallationOrderRepository | [InstallationOrderRepository.kt:12](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/InstallationOrderRepository.kt:12) |
| wispadmin | IpPoolRepository | [IpPoolRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/IpPoolRepository.kt:6) |
| wispadmin | MikrotikRepository | [MikrotikRepository.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/MikrotikRepository.kt:4) |
| wispadmin | MonthlyCollectsRepository | [MonthlyCollectsRepository.kt:7](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/MonthlyCollectsRepository.kt:7) |
| wispadmin | MufaRepository | [MufaRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/MufaRepository.kt:5) |
| wispadmin | NapBoxRepository | [NapBoxRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/NapBoxRepository.kt:5) |
| wispadmin | NetworkDeviceRepository | [NetworkDeviceRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/NetworkDeviceRepository.kt:6) |
| wispadmin | OnuRepository | [OnuRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/OnuRepository.kt:8) |
| wispadmin | OutlayRepository | [OutlayRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/OutlayRepository.kt:9) |
| wispadmin | PaymentRepository | [PaymentRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/PaymentRepository.kt:8) |
| wispadmin | PlaceRepository | [PlaceRepository.kt:10](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/PlaceRepository.kt:10) |
| wispadmin | PlanRepository | [PlanRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/PlanRepository.kt:5) |
| wispadmin | SalesLeadMapRepository | [SalesLeadMapRepository.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SalesLeadMapRepository.kt:8) |
| wispadmin | ScheduledTaskLogRepository | [ScheduledTaskLogRepository.kt:11](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/ScheduledTaskLogRepository.kt:11) |
| wispadmin | SubscriptionAcsRepository | [SubscriptionAcsRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionAcsRepository.kt:5) |
| wispadmin | SubscriptionLogRepository | [SubscriptionLogRepository.kt:11](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionLogRepository.kt:11) |
| wispadmin | SubscriptionReconnectionRepository | [SubscriptionReconnectionRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionReconnectionRepository.kt:9) |
| wispadmin | ServiceHealthSubscriptionView | [SubscriptionRepository.kt:13](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionRepository.kt:13) |
| wispadmin | SubscriptionRepository | [SubscriptionRepository.kt:23](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionRepository.kt:23) |
| wispadmin | SubscriptionNameProjection | [SubscriptionRepository.kt:892](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionRepository.kt:892) |
| wispadmin | SubscriptionsStaticsRepository | [SubscriptionsStaticsRepository.kt:7](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionsStaticsRepository.kt:7) |
| wispadmin | TicketConversationLinkRepository | [TicketConversationLinkRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/TicketConversationLinkRepository.kt:5) |
| wispadmin | Tr069ModelProfileRepository | [Tr069ModelProfileRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/Tr069ModelProfileRepository.kt:5) |
| wispadmin | UserRepository | [UserRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/UserRepository.kt:6) |
| wispadmin | WhatsAppAccountEventRepository | [WhatsAppAccountEventRepository.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppAccountEventRepository.kt:6) |
| wispadmin | WhatsAppAuditLogRepository | [WhatsAppAuditLogRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppAuditLogRepository.kt:5) |
| wispadmin | WhatsAppChatStateRepository | [WhatsAppChatStateRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppChatStateRepository.kt:5) |
| wispadmin | WhatsAppInboundMessageRepository | [WhatsAppInboundMessageRepository.kt:10](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppInboundMessageRepository.kt:10) |
| wispadmin | WhatsAppMarketingOptOutRepository | [WhatsAppMarketingOptOutRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppMarketingOptOutRepository.kt:5) |
| wispadmin | WhatsAppMessageLogRepository | [WhatsAppMessageLogRepository.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppMessageLogRepository.kt:9) |
| wispadmin | WhatsAppPhoneSessionRepository | [WhatsAppPhoneSessionRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppPhoneSessionRepository.kt:5) |
| wispadmin | WhatsAppQuickReplyRepository | [WhatsAppQuickReplyRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppQuickReplyRepository.kt:5) |
| wispadmin | WhatsAppSyncedTemplateRepository | [WhatsAppSyncedTemplateRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppSyncedTemplateRepository.kt:5) |
| wispadmin | WhatsAppWebhookEventRepository | [WhatsAppWebhookEventRepository.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/WhatsAppWebhookEventRepository.kt:5) |
| wispadmin | SearchEngine | [SearchEngine.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/search/api/SearchEngine.kt:6) |
| wispadmin | SearchIndexer | [SearchIndexer.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/search/api/SearchIndexer.kt:4) |
| wispadmin | OltService | [OltService.kt:9](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/OltService.kt:9) |
| wispadmin | IAddressListManager | [IAddressListManager.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/mikrotik/IAddressListManager.kt:4) |
| wispadmin | IMikroTikService | [IMikroTikService.kt:5](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/mikrotik/IMikroTikService.kt:5) |
| wispadmin | IQueueManager | [IQueueManager.kt:6](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/mikrotik/IQueueManager.kt:6) |
| wispadmin | OnuOperationsPort | [OnuOperationsPort.kt:7](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/onu/OnuOperationsPort.kt:7) |
| wispadmin | IServiceCutManager | [IServiceCutManager.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/subscription/IServiceCutManager.kt:4) |
| wispadmin | IServiceReactivationManager | [IServiceReactivationManager.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/subscription/IServiceReactivationManager.kt:2) |
| wispadmin | IInstallationStrategy | [IInstallationStrategy.kt:8](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/subscription/strategies/IInstallationStrategy.kt:8) |
| wispadmin | ISubscriptionValidator | [ISubscriptionValidator.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/validators/ISubscriptionValidator.kt:4) |
| wispadmin | WhatsAppBotEvent | [WhatsAppConversationStateMachine.kt:24](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/whatsapp/WhatsAppConversationStateMachine.kt:24) |
| wispadmin | Tracer | [Tracer.kt:4](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/tracing/Tracer.kt:4) |
| wispadmin | WebSocketSessionCleanup | [WebSocketSessionCleanup.kt:2](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/websocket/WebSocketSessionCleanup.kt:2) |
