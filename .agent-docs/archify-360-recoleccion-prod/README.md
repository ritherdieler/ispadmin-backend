# Archify — política de recolección 360 (producción)

Espejo del diagrama staging, con el cableado real de prod tras `remove-collection-gate-2026-09-15.md`.

| Artefacto | Ruta |
|-----------|------|
| Spec | `360-recoleccion-prod.workflow.json` |
| HTML | `360-recoleccion-prod.html` |
| Staging (pares) | `../archify-360-recoleccion-staging/` |

## Diferencias vs staging

| | Staging | Producción |
|---|---------|------------|
| Tag | `stg` horneado | vacío (prohibido setear tag) |
| Flags | `application-staging.properties` | `/opt/gigafiber/.env` → `SERVICE_HEALTH_*` |
| WAR | `:8081` `/ispadmin-staging` | `:8080` `/ispadmin` |
| Wi‑Fi target | 180 s | 1800 s |
| Evaluación | `directory.allIds()` | igual (sin lista piloto) |
| Lab | metadata | candado writes + Inform 60 s (no gate) |

## Invariante (código vigente)

1. Defaults en `application.properties`: todo `service.health.*=false` hasta que el `.env` active.
2. `HealthEvaluationService` itera `directory.allIds()`. No hay lista piloto.
3. Fuentes: `cpe.inform`, óptica SNMP push → Redis (`namespace=prod`), poll MikroTik.
4. Consumo: `HealthEvaluationService` → `GET /subscription/{id}/service-health`.
