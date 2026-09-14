# E2E staging FIBER vía Gateway — 2026-09-02

## Resultado

`E2E_FIBER_STAGING_ESPRESSO_OK` contra `ispadmin-staging` con OLT Gateway writes.

| Campo | Valor |
|-------|--------|
| Suscripción | **2354** |
| DNI | `98374712` |
| SN | `ZTEGDC47BFFD` |
| IP | `192.168.250.20` (pool staging) |
| Host MK | id 8 |
| Geo | `-11.2156, -77.4107` (NO-001 / polígono `9 de octubre`) |
| WiFi 2.4 | `lab-zte-e2e-24` / `LabZteWifi24!` |
| WiFi 5 | `lab-zte-e2e-24 - 5G` / `LabZteWifi24!` |

## Geo (causa del fallo previo)

Confirmar el mapa **sin** aplicar el fixture dejaba el default de cámara (`-11.23416,-77.37872` → San Jerónimo / `SJ -*`), fuera del flujo lab. Documentado en:

- `IpsAdmin-android app/.agent-docs/e2e-fiber-geo-polygon-invariante-2026-09-02.md`
- `ispadmin-backend/.agent-docs/staging-fiber-e2e-runbook.md` § Geo e2e

Fix app: `resolveManualMapSelectionTarget` prioriza coordenada buscada; `cameraPositionState.move` síncrono tras Buscar.

## Comando

```bash
cd "IpsAdmin-android app"
E2E_ONU_SN=ZTEGDC47BFFD E2E_WIFI_SSID=lab-zte-e2e-24 E2E_WIFI_PASS='LabZteWifi24!' \
  GEO_LAT=-11.2156 GEO_LON=-77.4107 E2E_NAP_CODE=NO-001 \
  ./scripts/e2e_register_fiber_staging_espresso.sh
```
