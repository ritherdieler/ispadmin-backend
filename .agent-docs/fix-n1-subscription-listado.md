# Fix N+1 en el listado de suscripciones (GET /subscription/all)

Corrección del patrón N+1 detectado en la traza `554f0ab9c97fc7bd33a6c6f16c500f2c`
(~600 spans, todos `db: SELECT` con status OK). El origen es el listado completo de
suscripciones que consume el backoffice.

## Causa raíz

- `GET /subscription/all` ejecutaba `repository.findAll().map { it.toDto() }`.
- La entidad `Subscription` tiene 9+ asociaciones `EAGER` (`plan`, `place`, `napBox`,
  `hostDevice`, `technician`, `fiberOnu`, `ipPool`, `cpe`, `coupon`, `additionalDevices`).
  Al hidratar cada fila, Hibernate emitía un `SELECT` por asociación no traída con join.
- `Subscription.toDto()` accede a la colección `payments` (LAZY) en 5 puntos
  (`pendingInvoiceQuantity`, `totalDebt`, `getLastPaymentDate()`,
  `getSubscriptionQualification()`, `geSubscriptionAntiquity()`), generando un `SELECT`
  de `payments` por suscripción.
- Los endpoints `GET /subscription/locations` y `GET /dashboard/subscription/locations`
  repetían el mismo `findAll()` + acceso a `payments`.

## Cambios

### `repository/SubscriptionRepository.kt`

Nueva query `findAllWithCoreRelations()` que trae en una sola consulta todas las
asociaciones de la entidad mediante `LEFT JOIN FETCH` (incluyendo la única colección
`additionalDevices`, con `DISTINCT` para deduplicar). Se ordena por `s.id` para dar un
orden estable a la respuesta.

### `repository/PaymentRepository.kt`

Nuevo método derivado `findBySubscriptionIdIn(subscriptionIds)` para cargar en una sola
consulta todos los pagos de las suscripciones del listado.

### `service/SubscriptionService.kt`

- `findAllForListing()` (`@Transactional(readOnly = true)`): carga las suscripciones con
  sus relaciones, trae los pagos en batch, los agrupa por `subscription.id` y los asigna
  a cada entidad antes del mapeo. El modo `readOnly` evita que la reasignación de la
  colección `payments` (que tiene `orphanRemoval = true`) dispare flush/borrados.
- `getAllSubscriptionsForList()`: reutiliza `findAllForListing()` y mapea a
  `SubscriptionDto`.

### Controllers

- `SubscriptionController.getAllSubscriptions()` → usa `getAllSubscriptionsForList()`.
- `SubscriptionController.getSubscriptionsLocations()` → usa `findAllForListing()`.
- `DashBoardController.getSubscriptionsLocations()` → se inyecta `SubscriptionService`
  y usa `findAllForListing()`.

## Decisión de diseño

El plan original proponía una segunda query `fetchAdditionalDevices()` para la colección
`additionalDevices`. Como es la única colección `EAGER` de la entidad, al ser `EAGER`
Hibernate la carga durante la hidratación inicial: una segunda query no evita ese N+1.
Se optó por traerla en el mismo `LEFT JOIN FETCH` de `findAllWithCoreRelations()` (un
solo bag, sin `MultipleBagFetchException` ni producto cartesiano, ya que `payments` se
carga por separado). Resultado equivalente en intención (eliminar el N+1) y más simple.

## Contrato preservado (sin regresiones cross-project)

- No se modificó `SubscriptionDto` ni `Subscription.toDto()` (contrato compartido con el
  backoffice y la app Android).
- No se migró ninguna relación `EAGER` a `LAZY` de forma global (los jobs de MikroTik,
  reportes y dashboard interno siguen dependiendo del comportamiento actual).
- Android no consume `/subscription/all`; sus flujos (`find/dni`, `fastSearch`,
  `debtors`, `GET /subscription/{id}`) no cambian.
- `ispadmin-asistencias` no consume endpoints de listado de suscripciones.

## Resultado esperado

El listado pasa de ~600 `SELECT` a un puñado de queries: 1 de suscripciones con joins,
1 de pagos en batch y, según el caso, alguna consulta menor. La respuesta JSON es
idéntica en campos (`totalDebt`, `pendingInvoiceQuantity`, `qualification`,
`lastPaymentDate`, `hasFiberOnu`, etc.).

## Build

`./mvnw -q compile` finaliza correctamente (exit 0).

## Verificación pendiente (manual / runtime)

- Repetir el flujo del backoffice y confirmar en la traza < 20 spans de DB.
- Contrastar el payload de `/subscription/all` antes/después.
- Smoke test backoffice: tabla de suscripciones, estadísticas, refresco de cache tras
  registrar un pago, mapa de ubicaciones.

## Iteración 2 — N+1 residuales (mufa y user)

Traza `12bf455e07237e1069261dd53aa09009` (22 spans). Tras la iteración 1 seguían
apareciendo dos N+1 en `GET /subscription/all`, ambos por relaciones `@ManyToOne`
`EAGER` por defecto que no venían en el join fetch:

- `NapBox.mufa` (`@ManyToOne` sin fetch explícito → EAGER): la query traía `s.napBox`
  pero no `napBox.mufa`, así que por cada `NapBox` distinto Hibernate emitía un
  `SELECT ... FROM mufa WHERE id=?` (14 repeticiones/traza).
- `Payment.responsible` (`@ManyToOne` sin fetch explícito → EAGER): al hidratar cada
  pago cargado por batch se inicializaba `responsible`, emitiendo un
  `SELECT ... FROM user WHERE id=?` por responsable distinto (5 repeticiones/traza).
  `Subscription.toDto()` ni siquiera usa `responsible` en este endpoint.

### Enfoque

Sin cambiar el `FetchType` global de las entidades (evita `LazyInitializationException`
en `PaymentController` `/filtered` y pagos recientes, que serializan `PaymentDto` leyendo
`responsible?.name`). Se resuelve con join fetch dirigido, satisfaciendo la semántica
`EAGER` en una sola consulta.

### Cambios iteración 2

- `repository/SubscriptionRepository.kt`: en `findAllWithCoreRelations()` se dio alias
  `nb` a `s.napBox` y se añadió `LEFT JOIN FETCH nb.mufa` (relación ToOne, sin producto
  cartesiano).
- `repository/PaymentRepository.kt`: nueva query
  `findBySubscriptionIdInFetchResponsible(ids)` con
  `SELECT p FROM Payment p LEFT JOIN FETCH p.responsible WHERE p.subscription.id IN :ids`.
  Se conservó `findBySubscriptionIdIn` para otros usos.
- `service/SubscriptionService.kt`: `findAllForListing()` ahora usa
  `findBySubscriptionIdInFetchResponsible(...)` para cargar los pagos con su responsable
  en una sola consulta.

### Build iteración 2

`./mvnw -q -o compile` finaliza correctamente (exit 0).

### Verificación pendiente iteración 2

- Re-ejecutar `GET /subscription/all` y confirmar en la traza que desaparecen los
  `SELECT FROM mufa WHERE id=?` y `SELECT FROM user WHERE id=?` repetidos.
- Smoke test de `PaymentController` `/filtered` para confirmar que `responsibleName`
  sigue resolviéndose (no se tocó su fetch type).
