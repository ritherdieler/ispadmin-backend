# oltgateway como fuente de verdad OLT/ONU (2026-09-02)

## Decisión

`oltgateway` es el único dueño del inventario y del estado actual OLT/ONU.

Otros subsistemas **leen** vía ports y se enganchan por **SN** (clave de negocio). El historial y el CRM viven en sus dominios.

## Identificadores

| Clave | Uso |
|-------|-----|
| `sn` | Cross-subsistema (suscripción, ACS, health, netdiag) |
| `olt_mgr_onu.id` | FK interna de series temporales |
| `external_id` | Operaciones OLT (`{oltId}_{board}_{port}_{onuIndex}`) |
| `(olt_id, board, port, onu_index)` | Correlación de alarmas crudas |

## Contrato de lectura

`OltInventoryPort` (`oltgateway/port`) expone:

- `findBySn` / `findByExternalId` / `findBySlot`
- `listConfigured` / `listAutofind` / `countConfigured`
- Snapshot óptico actual (señal, temperatura, distancia, runState)

Implementación: `OltInventoryService`. `HealthOnuAdapter` delega a este port.

## Escrituras

Por defecto `olt.provider.{authorize,delete,reboot,move}=GATEWAY`.

Fallback a SmartOLT si el gateway no está disponible. Modo sombra de authorize permanece disponible forzando `SMARTOLT` + `authorize-shadow=true`.

Backfill de external IDs: `POST /api/olt-gateway/admin/onus/external-id-backfill`.

## Vínculo CRM

`subscription.fiber_onu_sn` es solo el serial (VARCHAR). Ya no hay entidad JPA `onu` ni FK a la tabla legacy `onu`.

## Reglas de acceso

- Solo el paquete `oltgateway` importa/escribe repositorios `OltMgr*`.
- `servicehealth` / `netdiag` / `wispadmin` consumen ports.
- Telemetría histórica (`OpticalSample`, incidentes) permanece en sus subsistemas.

## Migración

`V44__subscription_fiber_onu_sn_decouple.sql` elimina `fk_subscription_fiber_onu` si existe.
