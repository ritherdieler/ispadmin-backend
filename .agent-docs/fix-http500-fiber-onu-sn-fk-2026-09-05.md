# HTTP 500 en alta FIBER staging (2026-09-05)

## Síntoma

Emulador Android e2e: `POST /ispadmin-staging/subscription/with-facade-photo` → HTTP 500.

## Causa

FK residual de Hibernate `FKj3wtbty2hpt2essof46euiuv0` en `subscription.fiber_onu_sn` → `onu(sn)`.

La migración `V44__subscription_fiber_onu_sn_decouple.sql` solo dropeaba el nombre fijo `fk_subscription_fiber_onu`. El constraint auto-nombrado de Hibernate quedó.

Con `onu` vacío (wipe / SoT en `olt_mgr_onu`), cualquier alta FIBER que setea `fiber_onu_sn` falla con `SQLIntegrityConstraintViolationException`.

No hace falta insertar filas en `onu` a mano: `fiber_onu_sn` es clave de negocio, no FK de inventario.

## Remedio

1. Staging: `ALTER TABLE subscription DROP FOREIGN KEY FKj3wtbty2hpt2essof46euiuv0;` (aplicado 2026-09-05).
2. Flyway `V49__drop_hibernate_fiber_onu_sn_fk.sql`: dropea **cualquier** FK de `fiber_onu_sn` → `onu` por columna (cubre prod en el próximo deploy).
3. Tests: `FiberOnuSnFkDecoupleMigrationTest`, refuerzo en `SubscriptionFetchStrategyTest`.

## Nota prod

Prod aún puede tener el mismo FK Hibernate hasta que se despliegue el WAR con V49.
