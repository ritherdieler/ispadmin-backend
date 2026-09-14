# Reuso de ONU en suscripción CANCELLED (2026-07-13)

Reintento automático de la autorización de ONU cuando SmartOLT responde `sn_already_exists` porque la ONU quedó autorizada tras cancelar la suscripción anterior.

## Problema

Al registrar o migrar a fibra, `authorize_onu` falla con `sn_already_exists` cuando la ONU física sigue autorizada en SmartOLT tras cancelar la suscripción anterior. `cancelService` no libera la ONU en SmartOLT, y antes el error se propagaba como 500. Caso real: SN `4857544315F5CD86` en el OLT vs `HWTC15F5CD86` en BD (suscripción #1886 `CANCELLED`).

## Solución

Nuevo `CancelledOnuReuseService.authorizeWithCancelledReuse(request)` que envuelve la autorización:

1. Intenta `authorizeOnuInSmartOltWidthPostMethod`.
2. Si falla y el error es `sn_already_exists` / `already exists on this OLT`, busca una suscripción `CANCELLED` cuyo `fiberOnu.sn` coincida con el SN.
3. Si la encuentra: elimina la ONU en SmartOLT con `deleteOnuBySn(sn)` (resuelve el `unique_external_id` vía `getOnuBySn` y borra por ese id, ya que `onu/delete/{external_id}` no acepta el SN crudo), pone `fiberOnu = null` en la suscripción cancelada, guarda y reintenta la autorización **una sola vez**.
4. Si el SN no pertenece a ninguna suscripción `CANCELLED` (p. ej. está en una `ACTIVE`), relanza el error original sin liberar nada.

La coincidencia de SN admite match exacto y por sufijo de 8 caracteres para cubrir el desfase de formato entre el SN del OLT y el de BD (`4857544315F5CD86` ↔ `HWTC15F5CD86`).

## Archivos

- `service/CancelledOnuReuseService.kt` — nuevo servicio de reuso.
- `service/OnuService.kt` — `deleteOnuBySn(sn)`: resuelve el `unique_external_id` en SmartOLT y borra por ese id.
- `repository/SubscriptionRepository.kt` — `findCancelledByFiberOnuSn(sn, suffix)`.
- `service/subscription/strategies/FiberInstallationStrategy.kt` — usa el servicio en el registro de fibra.
- `service/SubscriptionService.kt` (`migrateToFiber`) — usa el servicio y el `catch` ahora preserva el mensaje de negocio.
- `service/MockOltService.kt` — simula `sn_already_exists` cuando el SN ya está autorizado, para pruebas locales.
- `test/.../service/CancelledOnuReuseServiceTest.kt` — casos: cancelada (libera + reintenta), sin coincidencia (relanza), éxito a la primera (no libera).

## Build

`bash mvnw -o test -Dtest=CancelledOnuReuseServiceTest` — OK (2026-07-13).

## Fuera de alcance

- Liberar la ONU automáticamente en `cancelService` al cancelar (mejora futura).
- Cambios en Android; el backend resuelve el caso o devuelve un error de negocio claro.
- N+1 de queries en el registro (problema de rendimiento separado).
