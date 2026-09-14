# Bypass temporal de cupo de bornes (2026-06-30)

- Permite registrar o reactivar suscripciones de fibra aunque la NAP tenga los 16 bornes activos ocupados.
- Archivos: `BorneManagementProperties.kt`, `BorneManagementConfiguration.kt`, `BorneManagementService.kt`, `SubscriptionRepository.kt`, `application.properties`.
- Config: `subscription.borne.capacity-check-enabled` (default `true`, dev en `false`).
- Registro con bypass: si la NAP está llena o hay conflicto `uk_napbox_borne`, `borneNumber` queda `null`.
- Reactivación con bypass: conserva el borne original del cliente.
- La disponibilidad de bornes para asignación usa `uk_napbox_borne` (todas las filas), no solo `ACTIVE`.
- Build: `bash mvnw compile test -Dtest=BorneManagementServiceTest` — OK (2026-06-30).

## Activar bypass

```properties
subscription.borne.capacity-check-enabled=false
```

Reiniciar el backend. Revertir a `true` cuando ya no aplique.

## Causa del error uk_napbox_borne

MySQL tiene índice único `(napbox_id, borne_number)` en todas las filas. Suscripciones canceladas conservan el borne y bloquean reutilización. La app ahora evita asignar esos bornes; con bypass activo registra sin borne en lugar de fallar.
