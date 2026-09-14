# Cleanup e2e staging: OLT vía Gateway local

Fecha: 2026-09-04.

## Problema

`tr069-e2e-hard-cleanup.sh` borraba la ONU en **SmartOLT cloud** también con `--env staging`. El alta staging autoriza por **OLT Gateway** (`ispadmin-staging-oltgateway`); el cleanup cloud no liberaba la ONT real → lab sin `unconfigured_onus` y reauthorize con unique constraint.

## Cambio

| Env | Delete OLT |
|-----|------------|
| `staging` | VPS → `http://127.0.0.1:8080/ispadmin-staging-oltgateway` con `X-Olt-Gateway-Key` (`get_onus_details_by_sn` / `onus/by-sn` → `POST …/onu/delete/{externalId}`) |
| `prod` | SmartOLT cloud (sin cambio) |

También:

- `--allow-empty --sn …` ya no sale antes del delete OLT (sigue intentando liberar la ONU).
- SSH con `SSHPASS` + `sshpass -e` (passwords con caracteres especiales).

## Prueba

`DeployEnvScriptTest.tr069_e2e_hard_cleanup_staging_deletes_onu_via_local_olt_gateway`
