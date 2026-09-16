# Archify — cómo funciona la Vista 360

Diagrama de arquitectura (showcase) del camino de lectura, la recolección async y el panel en vivo.

| Artefacto | Ruta |
|-----------|------|
| Spec | `360-vista.architecture.json` |
| HTML | `360-vista.html` |
| Recolección prod | `../archify-360-recoleccion-prod/` |
| Recolección staging | `../archify-360-recoleccion-staging/` |

## Qué muestra

1. **Lectura:** el operador abre `/subscriptions/{id}/service-health`. El Core responde `GET /subscription/{id}/service-health` leyendo `HealthCurrent`. Sin I/O a equipos.
2. **Recolección:** ONU → ACS → Redis `cpe.inform`; OLT → Gateway → Redis `onu.optical-batch`. `HealthEvaluationService` consume, diagnostica y escribe el snapshot.
3. **En vivo:** `GET /subscription/{id}/live-readings` abre socket al MikroTik. No pasa por Redis ni por `HealthCurrent`.

## Invariante (código vigente)

1. `HealthEvidenceReader` solo consulta repos y caché.
2. Óptica 360 entra solo por `onu.optical-batch`. No hay pull HTTP.
3. La evaluación recorre `directory.allIds()`. No hay lista piloto.
4. Clientes externos hablan solo con el Core.
