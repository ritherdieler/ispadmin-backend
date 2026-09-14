# Cierre mensual en Spring (2026-08)

## Resumen

Un solo job Spring reemplaza tres eventos MySQL:

| MySQL EVENT | Servicio Kotlin |
|-------------|-----------------|
| `GuardarResumenSuscripciones` | `MonthlySubscriptionSnapshotService` |
| `GuardarResumenRecoleccionMensual` | `MonthlyCollectsSnapshotService` |
| `EjecutarRegistroPago` | `MonthlyMassBillingService` |

## Programación

- **Cron:** `0 50 23 * * *` (`America/Lima`), propiedad `dashboard.monthly-close.cron` / env `DASHBOARD_MONTHLY_CLOSE_CRON`.
- **Guard:** solo corre si hoy es el **último día del mes** (`MonthlyBillingCloseOrchestrator.runMonthlyCloseIfLastDayOfMonth`).
- **Mes cerrado:** `YearMonth` del día de ejecución (p. ej. 31-jul → julio 2026).

## Orden y resiliencia

1. Snapshot suscripciones → `monthly_subscription_resume`
2. Snapshot recaudación → `monthly_collects_resume` (partición 100 % como dashboard)
3. Facturación masiva (equivalente a `register_payment` en Kotlin, sin `CALL` al SP)

Los pasos 1 y 2 son *best effort*: si fallan, se registra error en BD y **sigue** el flujo. La masiva **siempre** se intenta al final.

## Logs en BD

Tabla `scheduled_task_logs`:

- Por paso fallido: `logTaskError` con `MONTHLY_CLOSE_SUBSCRIPTION_SNAPSHOT`, `MONTHLY_CLOSE_COLLECTS_SNAPSHOT` o `MONTHLY_CLOSE_MASS_BILLING`.
- Resumen del cierre: `MONTHLY_BILLING_CLOSE` con `detailedResult` JSON (`MonthlyBillingCloseResultDto`).

Estados agregados: `SUCCESS`, `PARTIAL_SUCCESS` (snapshots fallaron, masiva OK), `FAILED` (masiva falló).

API: `GET /api/scheduled-task-logs/by-task-type/MONTHLY_BILLING_CLOSE`

## Deploy prod

1. Desplegar WAR con `MonthlyBillingCloseScheduler`.
2. Ejecutar [`docs/disable-monthly-mysql-events.sql`](../docs/disable-monthly-mysql-events.sql).
3. Verificar EVENTs `DISABLED` y primera ejecución en fin de mes (logs + filas en tablas históricas + facturas `billing_date_datetime` = último día del mes).

## Verificación local

```bash
# Unitarios (MockK, sin BD)
./mvnw -Dtest=MonthlySubscriptionSnapshotServiceTest,MonthlyCollectsSnapshotServiceTest,MonthlyMassBillingServiceTest,MonthlyBillingCloseOrchestratorTest,ScheduledTaskLogServiceMonthlyCloseTest test

# Integración Spring + MySQL (perfiles dev + local; tag local-db)
./mvnw -Dtest=MonthlyBillingCloseLocalIntegrationTest test
```

La integración lee `ispadmin_dev` (perfiles `dev` + `local`). Cubre persistencia en tablas históricas, masiva real (~800+ suscripciones), logs en `scheduled_task_logs`, idempotencia (2.ª ejecución solo `skippedDuplicate`) y factura individual en mes 2099 con rollback.

**Bug corregido en masiva:** no usar `subscriptionRepository.save()` sobre la entidad `data class` con colección `payments` (provocaba `StackOverflowError`); updates vía `applyMassBillingInvoiceSubscriptionState` / `applyMassBillingCancellation` y procesamiento por ID.

- [`MonthlyBillingCloseOrchestrator`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/MonthlyBillingCloseOrchestrator.kt)
- [`MonthlyBillingCloseScheduler`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/scheduled/MonthlyBillingCloseScheduler.kt)
- Tests: `MonthlyBillingCloseOrchestratorTest`, `MonthlySubscriptionSnapshotServiceTest`, `MonthlyMassBillingServiceTest`, `MonthlyCollectsSnapshotServiceTest`

## Relacionado

- Corrección datos dashboard: [dashboard-monthly-collects-data-fix-2026-08.md](./dashboard-monthly-collects-data-fix-2026-08.md)
