# Ficha: Authorization presets

| | |
|--|--|
| Ruta | `/onu_authorization_presets/listing` |
| Add | `/onu_authorization_presets/add/` |
| Apply | `get_presets_for_apply`, `authorize_with_preset` |
| Captura | Browser 2026-07-17 — 0 presets; wizard abierto sin guardar |

## How it works (UI)

1. Create Preset – conditions + ONU settings  
2. Set Conditions – when to apply  
3. Enable Auto-Auth  
4. Done – ONUs authorized automatically  

Botones: “Create Preset with Wizard” | “Create Preset”.

Filtros listado: Search | OLT | ONU type | PON | Mode (Routing/Bridging).

## Wizard modal `#presetWizardModal` (6 steps)

| Step | Contenido |
|------|-----------|
| 1 Basics | `name`*, `description` |
| 2 Conditions | `olt_id`*, `board`, `port` (rangos `1-4,6`), `pon_type` GPON/EPON, `sn_pattern` (prefijos coma), `onu_type_id` (auto-detect), `fallback_onu_type_id`*, `is_default` |
| 3 ONU settings | `mode` Routing/Bridging, `router_mode`, WAN IP pool, `ip_protocol`, IPv6 modes, PPPoE, channel_type, custom_profile + template, line_profile_maptype, service profile, SVLAN/CVLAN/VLAN/attached, tag_transform, download/upload speeds, zone/odb/port, location_name, description_pattern |
| 4 TR069 & Mgmt IP | `tr069_profile_id`, `tr069_interface`, `mgmt_ip_mode` |
| 5 WiFi | WiFi settings (paso presente; no forzado en captura) |
| 6 Review | Review & Create |

Sin condiciones específicas → opción “OLT Default Preset”.

## IspManager

Wizard equivalente; condiciones + settings JSON; apply on autofind via task authorize.
