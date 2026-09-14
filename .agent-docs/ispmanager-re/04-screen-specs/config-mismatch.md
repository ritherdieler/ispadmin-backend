# Ficha: Config comparison (mismatch)

| | |
|--|--|
| Ruta | `/config_comparison` |
| Título | Configuration Mismatch (Database vs OLT) |
| Captura | Browser 2026-07-17 — auto-sync Disabled; sin scan ejecutado |

## Copy UI

Detect and fix mismatches between Database and OLT. Causas: OLT restart sin save running-config, o cambios directos en OLT.

## Daily Auto Configuration Check

Status observado: **Disabled**.

- Daily automatic scan & compare
- Auto-fix: Database config → OLT config
- What’s being synced: ONU Admin-state Enabled/Disabled; ONU CATV Enabled/Disabled
- Select OLT(s) + Save

“Auto-sync is disabled for all OLTs.”

## Manual Check

- Select OLT (`2 - HAWEI`)
- “Scan & Compare” (disabled hasta seleccionar)
- “No scan results yet”

**RE:** no ejecutar Scan ni activar Auto-fix (puede escribir en OLT).

## Resultado esperado

Filas mismatch: field, db_value, olt_value, fixed.  
Tipos: admin_state, catv, vlan, missing_on_olt, twin_offline…

## IspManager

Job diario + scan manual; auto-fix solo con confirmación explícita.
