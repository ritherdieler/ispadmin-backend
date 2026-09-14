# SmartOLT import one-shot (corto plazo)

Fecha: 2026-08-27

## Objetivo

Hidratar `olt_mgr_*` con metadatos de negocio (zone, ONU type, VLAN, profile, splitter/ODB, WAN mode) desde SmartOLT cloud, sin depender de SNMP ni martillar la OLT con SSH.

## Endpoints

| Auth | Método | Ruta |
|------|--------|------|
| Gateway key | POST | `/api/olt-gateway/admin/import/smartolt?pageSize=100&maxPages=` |
| JWT | POST | `/onu/import/smartolt?pageSize=100&maxPages=` |

## Flujo

1. `GET system/get_zones` → `olt_mgr_zone`
2. `GET system/get_onu_types` → `olt_mgr_onu_type`
3. `GET onu/get_all_onus_details?page=&page_size=` (paginado) → upsert `olt_mgr_onu` + status

## Reglas

- **Idempotente por SN** (normalizado Huawei GPON).
- **Merge** sobre filas existentes del sync SNMP: enriquece zone/type/VLAN/profile; no mueve board/port/onuIndex.
- Filas nuevas solo en SmartOLT: insert con status importado.
- `importedFromOlt=true`, flags `syncedAfterImport` / `lastResyncFailed` desde SmartOLT.
- Splitter: `stableSplitterId(odb_name)` (hash estable; catálogo splitters sigue siendo DISTINCT en DB).

## Config

Usa credenciales existentes:

- `olt.service.base-url`
- `olt.service.api-key` (header `X-Token`)

## Medio plazo (alta FIBER)

- `OnuService.authorizeOnuInSmartOltWidthPostMethod` enruta al **OLT Gateway** (`OltManagerFacade.authorizeOnu`) cuando `olt.gateway.enabled=true`.
- Alta FIBER vía `CancelledOnuReuseService` persiste zone/type/VLAN/profile en `olt_mgr_onu`.
- Authorize API: `importedFromOlt=false`, `syncedAfterImport=true`.

## Tests

- `SmartOltImportServiceTest` (fixtures en `src/test/resources/smartolt/`)
- `OnuServiceTest` — authorize FIBER vía facade

## Post-import

1. Ejecutar import una vez en prod/staging.
2. Verificar `GET /onu/catalog` (zones, types, vlans, profiles poblados).
3. Mantener sync SNMP caliente para telemetría; no re-ejecutar import salvo migración puntual.

## Paginación SmartOLT

`onu/get_all_onus_details` devuelve `total_pages` **solo en la página 1**; en páginas siguientes el campo viene ausente (Jackson → `0`). `SmartOltImportService` guarda `knownTotalPages` de la primera respuesta y no corta el loop por `totalPages` en páginas posteriores.

## Import local (2026-08-27)

```bash
curl -X POST 'http://localhost:8080/ispadmin/api/olt-gateway/admin/import/smartolt?pageSize=100' \
  -H 'X-Olt-Gateway-Key: dev-olt-gateway-key'
```

Resultado tras fix paginación:

| Campo | Valor |
|-------|-------|
| pagesFetched | 9 |
| totalItems | 802 |
| updated | 628 |
| unchanged | 174 |
| zonesImported | 32 |
| onuTypesImported | 49 |
| durationMs | ~15s |

`GET /onu/catalog`: 32 zones, 49 onuTypes, 2 vlans, 1 profile.

## Import productivo (2026-09-01)

Se ejecutó una importación puntual en producción mediante el endpoint del OLT Gateway.

| Campo | Valor |
|-------|-------|
| zonesImported | 32 |
| onuTypesImported | 49 |
| inserted | 0 |
| updated | 803 |
| pagesFetched | 9 |
| totalItems | 803 |
| error | `null` |
| Respaldo | `/opt/gigafiber/backups/onu-catalog-before-smartolt-20260901-232438.sql.gz` |

Validación posterior: la importación recibió 49 tipos desde SmartOLT, pero el catálogo funcional se ajustó a los valores realmente usados por la columna `Type` de las ONUs. Se conservaron 13 filas usadas en `olt_mgr_onu_type` y se eliminaron 36 filas sin referencias. Las 803 ONUs activas tienen `onu_type_id` y `onu_type_name`, sin inconsistencias entre ambos campos. Se registró un audit log `smartolt_import`.

El endpoint `GET /onu/catalog` expone únicamente los tipos enlazados a ONUs activas, evitando mostrar tipos genéricos que no aparecen en la tabla. La validación en backoffice mostró 13 modelos reales y el filtro `EG8145V5` devolvió 23 ONUs.
