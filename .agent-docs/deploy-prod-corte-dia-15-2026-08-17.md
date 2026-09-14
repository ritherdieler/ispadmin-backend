# Deploy prod — corte automático día 15 — 2026-08-17

| Componente | Valor |
|------------|--------|
| Backend | `1.0.3+d3c4318` |
| Comando | `./scripts/deploy.sh --deploy` OK, Tomcat `tomcat9027` recreado |
| HTTP | `GET /ispadmin/` → 200 |
| Observability | Deploy event `1.0.3+d3c4318` registrado |

## Cambio

Scheduler de corte MikroTik: día **15** a las 00:00 `America/Lima`, o el **lunes siguiente** si el 15 es sábado/domingo (`ServiceCutSchedule` + cron diario `0 0 0 * * *`).

## Corte manual de agosto 2026

El 15 fue sábado; el lunes 17 a medianoche ya había pasado. Tras el deploy se ejecutó `PUT /subscription/cortarDeudores`.

| Dato | Valor |
|------|--------|
| HTTP | 200 (~2 min) |
| Candidatos | 173 (`autoCut` + impago) |
| Agregados a `deudores` | 160 |
| Omitidos TV cable | 13 |
| Errores | 0 |
| Log | `scheduled_task_logs` id 34 `CUT_INTERNET_SERVICE_DEBTORS` `2026-08-17 10:04:40` `SUCCESS` |
| `last_cut_off_date` | 160 filas en `2026-08-17` |

Próximo corte automático: **15 de septiembre 2026** (martes) a las 00:00 Lima.
