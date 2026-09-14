# Óptica SNMP: Gateway `polled_at` + Core push

## Flujo (cutover)

1. Gateway `OltSignalPollService` (SNMP) → `applyOpticalUpdates` por puerto → `olt_mgr_onu_status_current.polled_at`
2. Gateway XADD **`onu.optical-batch`** (un mensaje por puerto GPON) con ONUs de lectura **completa**
3. Core `OpticalBatchPersistService` inserta `OpticalSample` + actualiza live 360; idempotencia `onuExternalId + polledAt`

Doc canónico del push: [optical-push-gateway-core.md](./optical-push-gateway-core.md).

El pull HTTP `HealthOltOpticalPullService` queda **apagado** (`SERVICE_HEALTH_OPTICAL_PULL_ENABLED=false`).

## Gateway — lectura completa vs incompleta

Una fila SNMP matched requiere las **tres potencias no-null**: `rxPowerDbm`, `txPowerDbm`, `oltRxPowerDbm`.

| Caso | Persistencia Gateway | Eventos Redis | Contador |
|------|----------------------|---------------|----------|
| Tres potencias + valores cambiaron | Actualiza dBm/temp/categoría + `polled_at=now` | `onu.optical-batch` (incluido) | `onusUpdated` |
| Tres potencias + mismos valores | Solo refresca `polled_at=now` | `onu.optical-batch` (incluido) | `polledAtRefreshed` |
| Falta alguna potencia (null) | **Descarta**; no coalesce; no toca status | **Omitida del batch** | `incompleteDiscarded` + log |

Temp/distancia siguen opcionales: si vienen null en una lectura **completa**, se conservan los valores previos de status.

## Logs estables

| Log | Cuándo |
|-----|--------|
| `SNMP_OPTICAL_INCOMPLETE_DISCARD sn=… rx=… tx=… oltRx=…` | Lectura descartada por potencia null |
| `SNMP_OPTICAL_PORT_FAIL … localCliBus=true localQueueDepth=… localBusyJobType=… sshActive=n/a sshMax=n/a` | Fallo de walk de puerto (+ presión CLI **local**) |
| `SNMP_OPTICAL_SSH_PRESSURE phase=start\|end localCliBus=true localQueueDepth=… …` | Inicio/fin del signal poll SNMP |
| `SNMP_OPTICAL_POLL_SUMMARY … durationMs=… localCliBus=true localQueueDepth=… …` | Fin de ciclo scheduler (+ mismas métricas locales) |

### Diagnóstico PORT_FAIL vs presión SSH/CLI

Hipótesis: fallos SNMP ópticos (p. ej. puerto denso `1/6`) pueden correlacionar con contención de recursos OLT por sesiones SSH/CLI.

Los campos `localQueueDepth` / `localBusyJobType` vienen **solo** del `OltCliBus` de **este** Gateway (`localCliBus=true`). `sshActive`/`sshMax` son `n/a` (el cliente SSH no expone sesiones activas globales).

| Observación | Interpretación |
|-------------|----------------|
| `PORT_FAIL` con `localQueueDepth=0` y `localBusyJobType=null` (idle) | El límite/carga del CLI bus **local** (staging) **no** explica el fallo; aún pueden existir sesiones SSH externas (prod Gateway, SmartOLT, ops) hacia la misma OLT — este WAR no las ve |
| `PORT_FAIL` con cola local alta o `busyJobType` activo | Correlación posible con presión CLI **de este** Gateway; no prueba causalidad sola |
| Staging idle + PORT_FAIL en 1/6 | Descarta “solo el bus staging”; no descarta SmartOLT/prod |

No se eliminan logs `SNMP_OPTICAL_*` previos; solo se enriquecen.

## Core

Samples y gráfica vía push Redis (`onu.optical-batch`). Sin pull paralelo en el camino feliz.
