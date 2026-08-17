# Handoff: corte / cancelación automática — por qué no corrió el 15 de agosto 2026

Documento para un desarrollador que no estuvo en el diagnóstico.  
Alcance: solo análisis del código y scripts del repo. No se modificó producción ni se reactivaron eventos MySQL.

**Repo:** `wispadministrator-main`  
**Fecha del incidente:** sábado 15 de agosto 2026  
**Zona horaria de negocio:** `America/Lima`

---

## 1. Problema reportado

Se esperaba que el 15 de agosto se ejecutara el corte/cancelación automática y que los clientes con **1 factura impaga** quedaran cancelados. No ocurrió.

Hay que separar dos procesos distintos. En el código no son lo mismo:

| Proceso | Efecto | ¿Corre el día 15? |
|---|---|---|
| **Cancelación de suscripción** (sucesor de `register_payment`) | Pasa `serviceStatus` a `CANCELLED` | No. Solo último día del mes. |
| **Corte de internet** (MikroTik, lista `deudores`) | Bloquea IP; no cancela la suscripción | No. Día 16 hábil, o martes siguiente si el 16 cae en fin de semana. |

El `due_date` de la factura (día 15) **no dispara ningún job**.

---

## 2. Causa exacta (clientes con 1 factura impaga)

Tres condiciones se cumplieron a la vez:

1. **Umbral de cancelación = 2 facturas impagas, no 1.**  
   Quien tiene `COUNT(payment WHERE paid = false) == 1` **nunca** entra a cancelación.

2. **La cancelación no corre el 15.**  
   Corre en el cierre mensual (último día del mes, 23:50 Lima). El 15 de agosto no es fin de mes.

3. **El Event Scheduler de MySQL que llamaba a `register_payment()` está apagado** desde el deploy del 2026-08-06. El reemplazo es Spring, con la misma regla `pending >= 2`.

Además, el 15/08/2026 fue **sábado** y el 16/08/2026 **domingo**. El corte MikroTik del día 16 solo corre lun–vie, así que ese mes queda para el **martes 18 de agosto**.

---

## 3. Qué pasó con `register_payment`

### 3.1 No está en Flyway

No hay `CREATE PROCEDURE register_payment` ni `CREATE EVENT` en `src/main/resources/db/migration`.

El procedimiento vive (o vivía) solo en MySQL de producción, fuera del versionado. En el código Kotlin el sucesor reutiliza el tipo de log histórico:

- `SubscriptionActionType.CANCELED_BY_STORED_PROCEDURE`
- `responsibleId = "store_procedure"`

### 3.2 Evento MySQL desactivado a propósito

Deploy prod 2026-08-06 (`local-docs/deploy-prod-cierre-mensual-2026-08-06.md`):

| EVENT MySQL | STATUS post-deploy |
|---|---|
| `GuardarResumenRecoleccionMensual` | DISABLED |
| `GuardarResumenSuscripciones` | DISABLED |
| `EjecutarRegistroPago` | DISABLED |

Script que lo apaga (no lo crea ni lo re-habilita):

- `docs/disable-monthly-mysql-events.sql`

`EjecutarRegistroPago` era el evento que invocaba `register_payment()`. **No hay Event Scheduler activo en los scripts del proyecto para este flujo.**

---

## 4. Flujo actual (Spring)

```
MonthlyBillingCloseScheduler          CutServiceMonthlyTaskScheduler
cron: 0 50 23 * * *  America/Lima     cron: 0 0 0 16 * MON-FRI
+ guard: solo si hoy es último día    + fallback: 0 0 0 18-23 * TUE
        del mes                         si el 16 cayó sáb/dom
              │                                    │
              ▼                                    ▼
MonthlyBillingCloseOrchestrator       SubscriptionService.cutInternetService()
              │                                    │
              ▼                                    ▼
MonthlyMassBillingService             ServiceCutManagerService
  pending >= 2 → CANCELLED            1+ unpaid + autoCut=true
  pending < 2  → genera factura       → address-list MikroTik "deudores"
  NO usa due_date                     NO usa due_date
                                      NO cambia serviceStatus a CANCELLED
```

`@EnableScheduling` está en `WispAdminApplication`.

---

## 5. Lógica de cancelación (sucesor de `register_payment`)

Archivo: `src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/MonthlyMassBillingService.kt`

```kotlin
val pending = paymentRepository.findPendingPaymentsBySubscriptionId(subscriptionId)
if (pending >= 2) {
    cancelForUnpaidInvoices(...)   // CANCELLED + log CANCELED_BY_STORED_PROCEDURE
    return
}
// pending 0 o 1: intenta crear factura del mes cerrado
```

Conteo de impagas (`PaymentRepository.findPendingPaymentsBySubscriptionId`):

```kotlin
SELECT COUNT(p) FROM Payment p
WHERE p.subscription.id = :subscriptionId AND p.paid = false
```

- **Sí usa:** `paid = 0/false`.
- **No usa:** `due_date`, ni “¿ya venció?”, ni día 15.

`due_date` sí se **escribe** al crear la factura:

```kotlin
fun dueDate(yearMonth: YearMonth): LocalDateTime = yearMonth.atDay(15).atStartOfDay()
```

Ejemplo: cierre de julio → `billingDate = 2026-07-31`, `dueDate = 2026-07-15`. Es metadato de la factura, no trigger de job.

Test de referencia: `MonthlyMassBillingServiceTest.processSubscription cancels when two or more unpaid invoices`. Con `pending = 1` no cancela.

---

## 6. Lógica de corte MikroTik (otro job)

Archivo: `CutServiceMonthlyTaskScheduler.kt`

- `executeOn16thIfWeekday`: `0 0 0 16 * MON-FRI` (sin `zone` explícito; el default del proceso JVM).
- `executeOnTuesdayAfterWeekend16th`: `0 0 0 18-23 * TUE`, y solo si el día 16 de ese mes fue sábado o domingo.

Query de deudores (`SubscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments`):

```sql
-- equivalente JPQL
SELECT DISTINCT s FROM Subscription s
INNER JOIN s.payments p
WHERE p.paid = false
  AND s.autoCut = true
  AND s.serviceStatus != 'CANCELLED'
GROUP BY s.id
```

Umbral efectivo: **≥ 1 factura impaga**. Tampoco filtra por `due_date`.

Hay un método `findSubscriptionsWithMoreThanXUnpaidBills(numberOfPayments)` (`HAVING COUNT >= :n`) que **no usa** el corte actual.

Efecto: agrega IP a address-list `deudores` y marca `isServiceCutOff = true`. No cancela la suscripción.

---

## 7. Calendario agosto 2026 (Lima)

| Fecha | Día | ¿Qué debería correr según código? |
|---|---|---|
| 15 | Sábado | Nada. `due_date` no dispara jobs. |
| 16 | Domingo | Corte MikroTik **no** corre (cron lun–vie). |
| 18 | Martes | Corte MikroTik **sí**, por el fallback 18–23 si el 16 fue fin de semana. |
| 31 | Lunes 23:50 | Cierre mensual: snapshots + facturación masiva. Cancelación solo si `pending >= 2`. |

---

## 8. Archivos a leer

### Cancelación / facturación masiva
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/MonthlyMassBillingService.kt`
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/MonthlyBillingCloseOrchestrator.kt`
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/scheduled/MonthlyBillingCloseScheduler.kt`
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/PaymentRepository.kt` (`findPendingPaymentsBySubscriptionId`)
- `src/test/kotlin/com/dscorp/wispadmin/wispadmin/service/MonthlyMassBillingServiceTest.kt`

### Corte MikroTik
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/scheduled/CutServiceMonthlyTaskScheduler.kt`
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/subscription/ServiceCutManagerService.kt`
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionRepository.kt`

### MySQL events (históricos, desactivados)
- `docs/disable-monthly-mysql-events.sql`
- `docs/disable-monthly-collects-mysql-event.sql`
- `local-docs/deploy-prod-cierre-mensual-2026-08-06.md`

### Entidad
- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/data/model/Payment.kt` (`paid`, `dueDate`, `billingDateDatetime`)

---

## 9. Cómo verificar en prod (solo lectura)

```sql
-- Eventos mensuales: deben seguir DISABLED si el deploy de agosto se aplicó
SELECT EVENT_NAME, STATUS, LAST_EXECUTED, EVENT_DEFINITION
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = 'ispadmin'
  AND EVENT_NAME IN (
    'EjecutarRegistroPago',
    'GuardarResumenRecoleccionMensual',
    'GuardarResumenSuscripciones'
  );

-- ¿Sigue existiendo el SP fuera de Flyway?
SHOW PROCEDURE STATUS WHERE Db = 'ispadmin' AND Name LIKE '%register_payment%';
SHOW CREATE PROCEDURE register_payment;  -- fallará si no existe
```

Logs Spring a revisar:

- `scheduled_task_logs` con tipos `MONTHLY_BILLING_CLOSE` / `CUT_INTERNET_SERVICE`
- Logger `CutServiceMonthlyTaskScheduler` y `MonthlyBillingCloseScheduler`

---

## 10. Si se pide un cambio de negocio (aún no implementar)

Alinear con producto antes de tocar código. Opciones típicas:

1. Cancelar o cortar el **día 15** usando `due_date` vencido.
2. Bajar el umbral de cancelación de `>= 2` a `>= 1`.
3. Unificar corte MikroTik y cancelación (hoy son jobs distintos, umbrales distintos, calendarios distintos).
4. Poner `zone = "America/Lima"` explícito en `CutServiceMonthlyTaskScheduler` (hoy el cron del 16 no declara zona).

Hasta que producto defina eso, el comportamiento actual es el especificado en código: **1 impaga no cancela; nada corre el 15; MySQL event apagado.**
