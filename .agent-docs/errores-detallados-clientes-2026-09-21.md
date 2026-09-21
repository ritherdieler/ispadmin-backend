# Errores detallados a clientes (API + Android)

Fecha: 2026-09-21

## Problema

Al registrar una suscripción en prod con ONU ya asignada (`HWTC15F610C6` → ACTIVE #1895), el backend tenía el mensaje de negocio en el log, pero Android solo mostraba **HTTP 500**.

## Causa

1. `GlobalExceptionHandler.handleAllExceptions` devolvía body con `"error": "Error interno del servidor"` y **omitía** `ex.message`.
2. La validación de ONU (`FiberOnuSnClaimService`) lanza `IllegalStateException`, pero `SubscriptionService.handleRegistrationError` la envolvía en `RuntimeException` → caía en el catch-all 500.
3. Retrofit (return type `BaseResponse`) ante HTTP no-2xx lanza `HttpException` cuyo `message` es solo el status; el Repository no leía el body.

## Regla

Todo error expuesto a clientes (Android, backoffice, scripts) debe incluir el **mensaje de negocio** en el JSON de respuesta:

| Campo | Uso |
|-------|-----|
| `message` | Texto detallado (canónico) |
| `error` | Mismo texto (compatibilidad Android / envelopes viejos) |
| `code` | Cuando aplique (`BAD_REQUEST`, `CONFLICT`, `NOT_FOUND`, …) |
| `status` | Código HTTP numérico en el body |

Conflictos de estado de negocio (`IllegalStateException`, p. ej. ONU ocupada) → **HTTP 409**, no 500 genérico.

En Android: parsear body con `ApiErrorBodyParser` (`error` → `message` → `failureReason`) ante `HttpException` / `errorBody()`.

## Cambios

- Backend: `GlobalExceptionHandler` + rethrow de `IllegalStateException`/`IllegalArgumentException` en `handleRegistrationError`.
- Android: `ApiErrorBodyParser` + uso en registro de suscripción (y parsers de error existentes).
