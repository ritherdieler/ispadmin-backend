# Dashboard payment queries restore

## Context

Commit `56889ee` changed dashboard economic metrics to use the current calendar month and `payment_date_datetime` for raised/discount totals. That diverged from `ea0625c`, where metrics were correct after the datetime migration.

## Change

Restored `PaymentRepository` and `DashBoardService` payment aggregation logic to match `ea0625c`:

- Range: previous month (`CURRENT_DATE() - INTERVAL 1 MONTH` through start of current month).
- Field: `billing_date_datetime` for gross revenue, total raised, discounts, and total to collect.
- Join: `INNER JOIN subscription` kept for gross revenue and total to collect.

Datetime columns from the migration remain unchanged.

## Build

```bash
bash mvnw compile -DskipTests
```

Result: success (2026-06-17).
