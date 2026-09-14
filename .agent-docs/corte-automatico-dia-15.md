# Corte automático de internet (día 15)

Corte MikroTik (lista `deudores`). No cancela la suscripción; eso lo hace el [cierre mensual](./cierre-mensual-spring.md) el último día del mes si hay ≥2 facturas impagas.

## Programación

- **Cron:** `0 0 0 * * *` (`America/Lima`) en `CutServiceMonthlyTaskScheduler.executeIfCutDay`.
- **Guard:** `ServiceCutSchedule.shouldRun(hoy)` — solo corre el día de corte del mes.

Regla:

| Día 15 | Día de corte |
|--------|----------------|
| Lunes a viernes | El 15 a las 00:00 Lima |
| Sábado | Lunes siguiente (15 + 2) a las 00:00 |
| Domingo | Lunes siguiente (15 + 1) a las 00:00 |

Ejemplo agosto 2026: el 15 fue sábado → corte el **lunes 17**. El cron anterior (día 16 lun–vie + martes 18–23) no disparó el 15/16/17.

## Criterio de deudores

Sin cambios: `autoCut = true`, ≥1 factura `paid = false`, `serviceStatus != CANCELLED`. Excluye `ONLY_TV_FIBER` en MikroTik y WhatsApp.

## Logs

`scheduled_task_logs`: `CUT_INTERNET_SERVICE_DEBTORS` (éxito) o `CUT_INTERNET_SERVICE` (error del job). Logger `CutServiceMonthlyTaskScheduler`.

## Prod 2026-08-17

Deploy `1.0.3+d3c4318` + `PUT /subscription/cortarDeudores`: 160 IPs a `deudores`, 13 omitidos TV cable, 0 errores. Detalle: [deploy-prod-corte-dia-15-2026-08-17.md](./deploy-prod-corte-dia-15-2026-08-17.md).

Próximo automático: **15-sep-2026** 00:00 Lima.

## Tests

```bash
./mvnw -Dtest=ServiceCutScheduleTest,CutServiceMonthlyTaskSchedulerTest test
```
