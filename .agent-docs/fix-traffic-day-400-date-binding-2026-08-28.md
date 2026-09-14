# Fix: 400 al elegir día en Consumo (`/traffic/day`)

**Fecha:** 2026-08-28  
**Síntoma:** En backoffice, “Elegir día” → `Request failed with status code 400`.  
**Request:** `GET /ispadmin/subscription/{id}/traffic/day?date=yyyy-MM-dd`

## Causa

`SubscriptionTrafficController.getTrafficDay` recibía `@RequestParam date: LocalDate` **sin** `@DateTimeFormat(iso = DATE)`.

Spring no convertía el ISO `yyyy-MM-dd` a `LocalDate` (fallo de binding **antes** de entrar al método):

```
MethodArgumentTypeMismatchException: Failed to convert ... to LocalDate
Parse attempt failed for value [2026-08-26]
→ HTTP 400 (cuerpo vacío; DefaultHandlerExceptionResolver)
```

Por eso no aparecía log de `ControllerLoggingAspect` para `getTrafficDay`.

El resto de endpoints con `LocalDate` en el proyecto ya usan `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`.

## Fix

```kotlin
@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
```

Test: `SubscriptionTrafficControllerTest` — `GET traffic day acepta date ISO yyyy-MM-dd`.

## Deploy

Release: `1.0.3+05421dd` (2026-08-28). WAR en Tomcat verificado con `@DateTimeFormat(iso=DATE)` en `getTrafficDay`.

Tras hard refresh del backoffice, “Elegir día” debe devolver 200.

```bash
curl -sS -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer <token>" \
  "https://<host>/ispadmin/subscription/2328/traffic/day?date=2026-08-26"
```

Esperado: `200` (puntos vacíos o con datos del día).
