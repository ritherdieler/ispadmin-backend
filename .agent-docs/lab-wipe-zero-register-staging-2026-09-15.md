# Wipe lab staging — registros en 0 (2026-09-15)

Reset de suscripciones de laboratorio en staging para validar el alta desde Android con el sistema vacío.

## Alcance

Solo lab. No se tocó `ispadmin` (prod) ni samples `192.168.30.x` / `192.168.25.x` en `stg_traffic`.

| Pieza | Qué se borró |
|-------|----------------|
| Core `ispadmin_staging` | Filas `subscription` #5 (ya caída), #6 PPPoE `gf6` VSOL, #7 STATIC_IP `192.168.250.16`. Hijos + health (`identity_link`, óptica, Wi‑Fi) de ids 1–7. |
| Traffic `stg_traffic` | Samples con `subscription_id` 1–7, `client_ip` `192.168.250.%` o `pppoe:gf%`. |
| Gateway `stg_oltgateway` | Journal + filas `olt_mgr_onu` de `ZTEGDC47BFFD` / `VSOL0031C0B6` (incl. `#del#`). Delete API: ambas ONU ya no autorizadas. |
| MK2 id 8 | Colas `[stg]` VLAN `192.168.250.x`. Secrets `gf6` y residuales `gf*` del mismo router de lab. |
| ACS / GenieACS | Tasks/faults del device VSOL. **Dispositivos y tag `lab` se conservan.** |

## Estado post-wipe

| Check | Resultado |
|-------|-----------|
| `GET /subscription/all` | `0` |
| `GET /onu/unconfigured_onus` | `ZTEGDC47BFFD` y `VSOL0031C0B6` libres |
| Catálogo | 24 places, 11 planes (intactos) |

## Script

`tr069-e2e-hard-cleanup.sh` ya no borra `onu` / `olt_mgr_onu` en el schema Core (no existen ahí). Rutea inventario a `OLT_GATEWAY_MYSQL_SCHEMA`, samples a `TRAFFIC_MYSQL_SCHEMA`, y quita secret/sesión PPPoE en MK. El body REST de MikroTik se decodifica con `errors='replace'`.

Prueba: `DeployEnvScriptTest.tr069_e2e_hard_cleanup_does_not_delete_onu_tables_from_core_schema`.

## E2E Android

Desde `IpsAdmin-android app`, emulator `emulator-5554`, `--cleanup-mode skip` para dejar el alta:

```bash
E2E_ONU_SN=ZTEGDC47BFFD ./scripts/e2e_register_fiber_staging_espresso.sh --cleanup-mode skip
```

Primer intento: Espresso timeout en éxito. El POST síncrono superó el `readTimeout` de 90 s de la app; Core sí dejó `#8` COMPLETE. Se limpió `#8` + delete Gateway de `ZTEGDC47BFFD` (ONU otra vez en autofind). Reintento con timeout HTTP de 4 min en Android.
