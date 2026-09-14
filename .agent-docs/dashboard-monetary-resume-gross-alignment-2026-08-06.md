# Dashboard resumen monetario alineado con ingreso bruto (2026-08-06)

## Problema
El pie chart Android sumaba `totalDiscount + totalRaised + totalToCollect` (p. ej. 51 027) mientras el ingreso bruto era 49 877. Desde `f8ebf93` el backend mezclaba ciclo de facturación (bruto/pendiente) con `payment_date` en mes calendario (recaudado/descuento).

## Cambios

### Backend (`ispadmin-backend`)
- `PaymentRepository.getTotalRaisedBetween` / `getTotalDiscountsBetween`: filtro por `billing_date_datetime` (cohorte del ciclo).
- `DashBoardService` (`createDashBoard` y `createDashBoardV2`): las cuatro métricas monetarias usan el mismo `billingCycleStart` / `billingCycleEnd` (día anterior al 1 del mes → día anterior al 1 del mes siguiente, `America/Lima`).
- `grossRevenue` en BD: `SUM(amount_to_pay)` en ese rango (`getGrossRevenueBetween`).
- Pruebas: `DashBoardServicePaymentPeriodTest`, `DashboardMonetaryResumeTest`.

### Android (`IpsAdmin-android app`) — mínimo
- `ChartComponents.kt` (`PieChartContainer`): el total bajo el pie muestra `economicResume.grossRevenue` del API (calculado en servidor), etiqueta `total_gross`.
- Sin suma local de descuento + recaudado + pendiente para el total.
- `GET /dashboard` → `createDashBoard()`.

## Verificación local
```bash
cd ispadmin-backend && ./mvnw test -Dtest=DashBoardServicePaymentPeriodTest,DashboardMonetaryResumeTest
cd "IpsAdmin-android app" && ./gradlew compileDevDebugSources
```

## Despliegue
1. Desplegar **backend** primero (`scripts/deploy.sh` o flujo habitual).
2. Publicar app Android si cambió el pie (solo visualización de `grossRevenue`).
3. En prod: `totalRaised + totalDiscount + totalToCollect ≈ grossRevenue` para el ciclo actual.

## Construcción / QA (2026-08-06)
- Backend tests: OK (`DashBoardServicePaymentPeriodTest`, `DashboardMonetaryResumeTest`).
- Android: `compileDevDebugSources` OK.
