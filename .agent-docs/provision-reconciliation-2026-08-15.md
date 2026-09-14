# Reconciliación de provisión MikroTik + OLT

Fecha: 2026-08-15  
Rama: `cursor/provision-reconciliation-ef49`

## Migración

`V23__subscription_provision_status.sql` (en prod `V21` ya era `is_bimonthly` y `V22` es `client_request_id`)

Columnas en `subscription`:

- `mikrotik_provision_status`
- `olt_provision_status`
- `provision_attempt_count`
- `provision_next_attempt_at`
- `provision_last_error`

Índice: `idx_subscription_provision_retry`

## Componentes

- `SubscriptionProvisionService` — init statuses, apply result, backoff, `reconcile` / `reconcileDue`
- `SubscriptionProvisionReconciliationScheduler` — cada 5 min
- Soft-fail OLT en `FiberInstallationStrategy`; cola MikroTik idempotente si el target ya existe
- `SubscriptionDto`: `mikrotikProvisionStatus`, `oltProvisionStatus`, `provisioningPending`

## Properties

```
subscription.provision.reconciliation.interval-ms=300000
subscription.provision.reconciliation.initial-delay-ms=60000
```

## Backoff

| `provision_attempt_count` (antes) | Delay |
|-----------------------------------|-------|
| 0 | 5 min |
| 1 | 15 min |
| ≥2 | 30 min |
| ≥12 | `FAILED` estable (sin `next_attempt_at`) |
