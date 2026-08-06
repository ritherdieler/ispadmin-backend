# Deploy prod: cierre mensual Spring (2026-08-06)

| Componente | Valor |
|------------|--------|
| Release | `1.0.3+8da511b` (commits `a75b70f`, `8da511b`) |
| Comando | `./scripts/deploy.sh --deploy` |
| Tomcat | `tomcat9027` recreado |
| HTTP | `GET /ispadmin/` → 200 |
| Observability | Deploy event registrado |

## MySQL EVENTs (post-deploy)

Ejecutado en `mysql8033` / BD `ispadmin`:

| EVENT | STATUS |
|-------|--------|
| `GuardarResumenRecoleccionMensual` | DISABLED |
| `GuardarResumenSuscripciones` | DISABLED |
| `EjecutarRegistroPago` | DISABLED |

Cierre mensual: **Spring** `MonthlyBillingCloseScheduler` — cron `0 50 23 * * *` `America/Lima`, guard último día del mes.

## Pendiente operativo

- Primer cierre automático: fin de mes 23:50 Lima; revisar `scheduled_task_logs` (`MONTHLY_BILLING_CLOSE`).
- `git push origin develop` si aún no está en remoto.
