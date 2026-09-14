# E2E FIBER staging — suscripción 2358 (2026-09-02)

## Resultado

| Paso | Estado |
|------|--------|
| Espresso `FiberRegisterFirstOnuE2ETest` | OK (BUILD SUCCESSFUL) |
| Suscripción | **2358**, DNI `98411198`, SN `ZTEGDC47BFFD`, IP `192.168.250.20`, host MK2 id 8 |
| Ping MK2 | **OK** 4/4 ~4 ms |
| Hard cleanup (1.er intento) | Falló: delete iba a `/ispadmin-staging/.../onus/delete` (404) y `olt_mgr_*` en `ispadmin_staging` (tabla ausente) |
| Hard cleanup (fix) | Gateway **200** delete en `/ispadmin-staging-oltgateway`; inventario en `stg_oltgateway` |

## WiFi del alta (antes del cleanup)

| Banda | SSID | Clave |
|-------|------|-------|
| 2.4 GHz | `lab-zte-e2e-24` | `LabZteWifi24!` |
| 5 GHz | `lab-zte-e2e-24 - 5G` | `LabZteWifi24!` |

## Fix en scripts

`scripts/tr069-e2e-hard-cleanup.sh` (`--env staging`):

- `GW_BASE` → `https://api.gigafiberperu.cloud/ispadmin-staging-oltgateway`
- `OLT_MYSQL_SCHEMA` → `stg_oltgateway` (`mysql_olt_q`)
- CRM sigue en `ispadmin_staging`

Test: `DeployEnvScriptTest.tr069_e2e_hard_cleanup_targets_staging_schema_when_env_staging`.

Runbook: `staging-fiber-e2e-runbook.md`.
