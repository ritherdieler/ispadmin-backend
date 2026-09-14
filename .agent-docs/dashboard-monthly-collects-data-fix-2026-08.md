# Dashboard: corrección datos Recaudación por mes (2026-08-06)

## Problema

En Android, el gráfico **Recaudación por mes** mostró ~**631%** (segmento descuentos) en la barra de **jul 2026**. El API `GET /dashboard` convierte montos a % con `total × 100 / gross_income` ([`MonthlyCollectsResume.toDto()`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/data/model/MonthlyCollectsResume.kt)).

Origen: filas incorrectas en `monthly_collects_resume`, pobladas por el evento MySQL `GuardarResumenRecoleccionMensual` (fuera del repo).

## Filas corregidas (fase 1 — solo datos)

| id | date | Antes (resumen) | Acción |
|----|------|-----------------|--------|
| 74 | 2026-07-31 | `gross_income=380`, descuentos ~631% en app | UPDATE con ciclo billing jun30–jul31 |
| 73 | 2026-06-30 | Recaudado 0.5% / por cobrar 99.4% | UPDATE con ciclo billing may31–jun30 |
| 71 | 2026-04-30 | Duplicado de abril | DELETE (conservar id 72) |

## Valores objetivo (recalculo billing_date_datetime)

Regla alineada a [`PaymentRepository`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/PaymentRepository.kt) / [`DashBoardService`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/DashBoardService.kt).

**Junio 2026 (id 73):** `2026-05-31` ≤ billing &lt; `2026-06-30`

| gross_income | total_raised | total_discount | total_receivables |
|-------------:|-------------:|---------------:|------------------:|
| 47177.0 | 44414.0 | 1688.0 | 1010.0 |

**Julio 2026 (id 74):** `2026-06-30` ≤ billing &lt; `2026-07-31`

| gross_income | total_raised | total_discount | total_receivables |
|-------------:|-------------:|---------------:|------------------:|
| 49202.0 | 43480.5 | 1834.5 | 3887.0 |

Porcentajes esperados en app (~88% / ~4% / ~8% recaudado/descuento/por cobrar en julio).

## Script

[`docs/fix-monthly-collects-resume-2026-08.sql`](../docs/fix-monthly-collects-resume-2026-08.sql)

Ejecución: túnel [`scripts/db-tunnel.sh`](../scripts/db-tunnel.sh) → MySQL `ispadmin` → preflight → transacción → postflight → `COMMIT` si % ∈ [0,100].

## Fase 2 (implementada en backend — despliegue + SQL prod)

- **Spring:** [`MonthlyBillingCloseScheduler`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/scheduled/MonthlyBillingCloseScheduler.kt) — cron por defecto `0 50 23 * * *` (**último día** del mes, 23:50 **America/Lima**), cadena en [`MonthlyBillingCloseOrchestrator`](../src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/MonthlyBillingCloseOrchestrator.kt).
- Detalle: [cierre-mensual-spring.md](./cierre-mensual-spring.md).
- **MySQL:** desactivar los tres EVENT con [`disable-monthly-mysql-events.sql`](../docs/disable-monthly-mysql-events.sql) tras desplegar el WAR.
- Prop opcional: `DASHBOARD_MONTHLY_CLOSE_CRON` → `dashboard.monthly-close.cron`.

- Opcional futuro: calcular `monthlyCollects` en vivo en `GET /dashboard` sin tabla histórica.

## Verificación

- Postflight SQL en ids 72–74.
- `GET /dashboard` → `monthlyCollects` sin porcentajes &gt; 100 en jun/jul 2026.

**Aplicado en prod:** 2026-08-06 (COMMIT tras postflight). Antes: id 74 `gross=380`, id 73 invertido, id 71 duplicado abril. Después: id 73 ~94%/3.6%/2.1%; id 74 ~88.4%/3.7%/7.9%; un solo registro abril (72).
