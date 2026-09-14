# JWT ONU inventory — configured filters & catalogs

Fecha: 2026-08-27

Paridad de filtros SmartOLT Configured ONUs sobre `olt_mgr_onu` + `olt_mgr_onu_status_current`.

## Endpoints

- `GET /onu/configured` — ver query params en `OnuController` / `ConfiguredOnuFilter`
- `GET /onu/catalog` → `OnuCatalogsDto`
- `GET /onu/catalog/boards-ports`
- Gateway mirror: `GET /api/olt-gateway/onus/configured` (mismos filtros)

## Query

`OltMgrOnuRepository.findConfiguredFiltered` — EntityGraph `status`, `olt`, `zone`, `onuType`.

DTO enriquecido: `ConfiguredOnuItemDto` (zone, olt, vlan, mode, auth date, VoIP/TV flags, etc.).

## Relacionado

- Spec SmartOLT: `.agent-docs/ispmanager-re/04-screen-specs/configured-onus.md`
- Backoffice: `ispadmin-backoffice/.agent-docs/onus-configured-smartolt-parity-2026-08-27.md`
