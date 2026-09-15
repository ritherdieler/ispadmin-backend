# Óptica SNMP push — Gateway → Redis → Core

**Doc canónico** del camino feliz tras el cutover (grill 2026-09-08). Notas de dedup/logs del Gateway: [optical-snmp-core-dedup-and-gateway-logs.md](./optical-snmp-core-dedup-and-gateway-logs.md).

Modelo paralelo a WiFi-on-Inform: [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).  
Desacople: [subsistemas-desacople-transporte.md](./subsistemas-desacople-transporte.md). ACS **no** participa.

## 1. Flujo

```text
Capas (arriba → abajo). Continua = SNMP/HTTP. Punteada = Redis Streams.

[OLT]                 [Gateway]                      [Bus]                 [Core]

MA5608T
 │ SNMP GETBULK
 │ (walk por puerto GPON)
 ▼
OltSignalPollService
 │ status_current (última foto)
 │ XADD type=onu.optical-batch ·········► Redis
 │   (un mensaje por puerto;                │
 │    solo ONUs con Rx+Tx+OLT Rx)            ▼
 │                                    HealthSnapshotConsumer
 │                                            │
 │                                            ▼
 │                                    OpticalBatchPersistService
 │                                    (samples + live 360)
 │                                            │
 │                                            │ GET series 360
 │                                            ▼
 └────────────────────────────────────── Backoffice charts
```

Clientes externos hablan **solo con Core**. Core **no** hace pull HTTP de óptica en el camino feliz.

## 2. Tipo de evento

| Decisión | Valor |
|---------|--------|
| Tipo Redis | **`onu.optical-batch`** (`PlatformEventTypes.ONU_OPTICAL_BATCH`) |
| Por qué no reusar `onu.optical` | `onu.optical` es 1 ONU / payload liviano (live hash legado). El batch lleva N ONUs, `onuExternalId`, tres potencias y `polledAt` por ítem. Tipo nuevo evita romper consumidores y Lab SSH. |

Payload (schema v1, JSON en `payloadJson`):

```json
{
  "oltId": 2,
  "slot": 1,
  "port": 6,
  "polledAt": "2026-09-08T20:00:00Z",
  "onus": [
    {
      "sn": "VSOL0031C0B6",
      "onuExternalId": "gigafiber-ma5608t_1_6_10",
      "onuRxDbm": -19.46,
      "onuTxDbm": 2.2,
      "oltRxDbm": -24.56,
      "polledAt": "2026-09-08T20:00:00Z",
      "runState": "online"
    }
  ]
}
```

## 3. Responsabilidades

| Capa | Hace | No hace |
|------|------|---------|
| Gateway | SNMP poll; `status_current`; XADD batch por puerto; XADD `onu.state` para ONUs del fused sin DDM | Persistir `olt_mgr_onu_optical_sample` del Core |
| Core service-health | Consumer Redis; samples históricos; live 360; `telemetry_source_run` `OLT_OPTICAL` por batch | Pull HTTP Gateway (cutover A) |
| ACS | — | Óptica |

### Live Redis (`health:live:{id}:onu`)

| `updateKind` | Comportamiento |
|--------------|----------------|
| `optical` **con** `runState` | Escribe `runState`, `observedAt`, `opticalObservedAt`, Rx |
| `optical` **sin** `runState` (DDM-only) | Solo `opticalObservedAt` + Rx; no toca estado previo |
| `state` | Escribe `runState` + `observedAt`; no toca Rx |

El batch fused incluye `runState` del SNMP de esa pasada (no el `status_current` posiblemente viejo). ONUs sin tres potencias (p. ej. offline) no van en el batch: Gateway publica `onu.state` con el `runState` del snapshot fused.

## 4. Reglas de inclusión

| Lectura | `status_current` | Batch Redis |
|---------|------------------|-------------|
| Tres potencias + dBm cambiaron | Actualiza + `polled_at` | Incluye ONU |
| Tres potencias + dBm iguales | Solo refresca `polled_at` | **Incluye** ONU |
| Falta alguna potencia | Descartada (sin tocar status) | **Omitida** |

Unidad de transporte: **un evento por puerto GPON** (tras aplicar las filas de ese puerto). Fallo de un puerto no bloquea los demás en el walk SNMP.

## 5. Idempotencia Core

Por ONU: **`onuExternalId` + `polledAt`** (mismo Instant → no inserta sample duplicado). Resolución: `HealthOnuPort.findByExternalId` (fallback SN). Scope lab/piloto igual que el resto de service-health.

## 6. Cutover A — pull apagado

| Flag | Default | Propiedad |
|------|---------|-----------|
| `SERVICE_HEALTH_OPTICAL_PULL_ENABLED` | **`false`** | `service.health.optical-pull-enabled` |

`HealthOltOpticalPullService` solo corre si health + optical + **opticalPullEnabled**. Con el consumer batch cableado, el pull HTTP permanece off (WiFi 360 tampoco hace poll: solo `cpe.inform`).

`OLT/collector` del 360 lee `telemetry_source_run` (`source=OLT_OPTICAL`). Lo escribe **`OpticalBatchPersistService`** al persistir cada `onu.optical-batch` (un run por puerto). No hace falta encender el pull HTTP.

Charts 360 leen `olt_mgr_onu_optical_sample` alimentado por el push. Ventanas >90 días usan roll-up diario (`olt_mgr_onu_optical_daily`): [optical-daily-rollup-retention.md](./optical-daily-rollup-retention.md).

## 7. Secretos / env (solo nombres)

Documentado en [vps-secrets-management.md](./vps-secrets-management.md): `SERVICE_HEALTH_OPTICAL_PULL_ENABLED`.

Contención SNMP (timeout 15 s, lock `olt-snmp-poll` prod↔staging, retry de puerto): [olt-snmp-optical-contention.md](./olt-snmp-optical-contention.md).

