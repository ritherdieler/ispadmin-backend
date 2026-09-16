# Archify — política de recolección 360 (staging)

Diagrama workflow (showcase) de cómo staging decide y ejecuta la telemetría Vista 360.

| Artefacto | Ruta |
|-----------|------|
| Spec | `360-recoleccion-staging.workflow.json` |
| HTML | `360-recoleccion-staging.html` |

## Invariante (código vigente)

1. Overlay `application-staging.properties`: `gigafiber.environment.tag=stg`, `service.health.enabled/acs/optical=true`, `traffic.poll.enabled=true`, `gigafiber.scheduling.enabled=true` con `operational-jobs=false`.
2. `HealthEvaluationService` itera `directory.allIds()` → `findEvaluationIds()` (FIBER/ONLY_TV_FIBER operativas con ONU o ACS). Salta `HealthCurrent` fresco (`snapshot-fresh-seconds`). Overlay: sweep cada 5 min. No hay lista piloto.
3. `AcsSubscriptionPort.isLab()` solo marca `identity.lab`.
4. Flags de módulo (`enabled` / `acsEnabled` / `opticalEnabled` / `traffic.poll`) encienden ingest.
5. Fuentes: `cpe.inform` (Wi‑Fi, cadencia staging 180 s), óptica SNMP push → Redis, poll MikroTik.
6. Consumo: samples → `HealthEvaluationService` → `GET /subscription/{id}/service-health`.

Docs de origen: `lab-recoleccion-siempre-on-2026-09-15.md`, `staging-e2e-recoleccion-siempre-on-2026-09-15.md`.
