# Ficha: Catálogos (Zones, ODBs, ONU types, Speeds)

| Ruta UI | API |
|---------|-----|
| `/locations/listing` | `GET system/get_zones` |
| `/odbs/listing` | `GET system/get_odbs/{zone_id}` |
| `/onu_types/listing` | `GET system/get_onu_types` |
| `/speed_profiles` | `GET system/get_speed_profiles` |

## Samples live

- Zones: Zone 1…N (`id`,`name`, imported_*).
- ODBs zone 1: `[]` en esta instancia.
- ONU types (~49): capability Bridging/Routing, port counts, catv, allow_custom_profiles.
- Speeds (~32): name, speed (kbps-like), direction upload/download, type internet.

Writes Postman: `add_zone`, `add_odb`, `add_onu_type` (no ejecutar en RE salvo staging).
