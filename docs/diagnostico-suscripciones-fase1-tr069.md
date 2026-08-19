# Diagnóstico: módulo de Suscripciones (alineación FASE 1 TR-069 / GenieACS)

**Audiencia:** equipo de diseño / arquitectura  
**Repo backend:** `wispadministrator-main`  
**Fecha:** 2026-08-19  
**Objetivo:** describir el estado actual del apartado de Suscripciones para integrar aprovisionamiento ACS **sin romper** el alta existente.

Este documento no propone implementación. Solo hallazgos.

---

## 1. Ubicación y estructura

No hay un bounded context DDD ni puertos/adaptadores hexagonales para Suscripciones. El módulo es **Spring clásico por capas**, con un Service orquestador gordo y un Strategy por tipo de instalación.

| Capa | Paquete / ubicación |
|---|---|
| API | `com.dscorp.wispadmin.wispadmin.controller.SubscriptionController` → `@RequestMapping("/subscription")` |
| Orquestación | `...service.SubscriptionService` (alta, IP, corte, reboot OLT, migración) |
| Provisión red (MikroTik/OLT) | `...service.SubscriptionProvisionService` |
| Estrategias de instalación | `...service.subscription.strategies.*` |
| Persistencia | `...repository.SubscriptionRepository` + entidad JPA `...data.model.Subscription` |
| Contrato de entrada | `...requestbody.SubscriptionRequest` |
| Contrato de salida | `...dto.SubscriptionDto` |

**Patrones ya usados (reutilizar, no reinventar):**

- **Strategy:** `InstallationStrategyFactory` elige `Fiber` / `Wireless` / `OnlyTvFiber`.
- **Orquestador transaccional:** `registerSubscription` persiste primero, luego intenta provisión de red; si MikroTik/OLT fallan, la suscripción **queda creada** y se reintenta.
- **Reintento asíncrono por scheduler:** `SubscriptionProvisionReconciliationScheduler` (cada ~5 min).

**Qué no es:** no hay UseCase de dominio, no hay agregado con invariantes encapsuladas, no hay módulo Gradle separado. `Subscription` es un **entidad JPA anémica/fat** que mezcla cliente, facturación, red y provisión.

---

## 2. Entidad principal

`Subscription` (`data.model.Subscription`) es el agregado de hecho. PK `id` (IDENTITY). No hay tabla de “cliente” aparte: nombre, DNI, teléfono, dirección y geolocalización viven en la misma fila.

### Campos de negocio / cliente
`firstName`, `lastName`, `dni` (unique), `phone`, `address`, `location`, `place`, `technician`, `clientType`, `equipmentCondition`, `facadePhotoUrl`, `clientRequestId` (idempotencia offline).

### Campos técnicos de instalación (punto natural TR-069)
| Campo | Rol |
|---|---|
| `fiberOnu: Onu` | ONU de fibra. PK de `Onu` = **serial (`sn`)**. Board/port/OLT. |
| `cpe: NetworkDevice` | Equipo de inventario (router/CPE catálogo). **No es el CPE CWMP.** |
| `hostDevice: NetworkDevice` | MikroTik / core que hospeda la cola. |
| `ip` + `ipPool` | IP de cliente (unique). |
| `plan` | Velocidad / precio. |
| `napBox` + `borneNumber` | Planta externa (solo FIBER / ONLY_TV_FIBER). |
| `installationType` | `FIBER` \| `WIRELESS` \| `ONLY_TV_FIBER` |

### Estados de ciclo de vida comercial
`ServiceStatus`: `ACTIVE` → `CUT_OFF` / `SUSPENDED` / `CANCELLED`  
Flags: `isServiceCutOff`, `isPaymentCommit`, `isReactivation`, `autoCut`, `isBimonthly`.

### Estados de provisión de red (ya existen; **no cubren ACS**)
- `MikrotikProvisionStatus`: `PENDING` \| `COMPLETE` \| `FAILED`
- `OltProvisionStatus`: `PENDING` \| `COMPLETE` \| `FAILED` \| `NA`
- `provisionAttemptCount`, `provisionNextAttemptAt`, `provisionLastError`

`isProvisioningPending()` = MikroTik u OLT en `PENDING` o `FAILED`.  
**No hay** `tr069ProvisionStatus` ni `genieacsDeviceId` en la entidad.

### Ciclo de vida del alta (resumen)
1. Validación + asignación IP.  
2. Persistencia con `serviceStatus = ACTIVE` (default).  
3. Provisión MikroTik (simple queue) y, si FIBER, autorización ONU en OLT/SmartOLT.  
4. Si falla red: estados `PENDING` + reintento. La fila comercial **no se borra**.  
5. Corte/cancelación posteriores son jobs distintos (deuda), fuera del alta.

---

## 3. Flujo de alta actual

No hay UseCase. El entrypoint es el controller y el orquestador es `SubscriptionService.registerSubscription`.

```
Android / cliente HTTP
    POST /subscription
    POST /subscription/with-facade-photo   (multipart)
        │
        ▼
SubscriptionController.newSubscription / newSubcriptionWithFacade
        │
        ▼
SubscriptionService.registerSubscription   [@Transactional]
        │
        ├─ 1. Idempotencia: findByClientRequestId
        │      si existe → SubscriptionProvisionService.reconcile() y return alreadyRegistered
        ├─ 2. subscriptionValidator.validateSubscriptionRequest
        ├─ 3. resolveIpAssignment (IP manual o última+1 en pool)
        ├─ 4. createSubscriptionEntity (toModel + ip + ipPool + orden instalación)
        ├─ 5. SubscriptionProvisionService.initializeStatuses
        ├─ 6. Si FIBER: processOnuForFiber (adjunta/actualiza Onu por SN)
        ├─ 7. Si FIBER/TV: borneManagementService (NAP)
        ├─ 8. repository.save  ← punto de no retorno comercial
        ├─ 9. InstallationStrategyFactory.getStrategy(type).processInstallation
        │      FIBER: autoriza ONU en OLT (CancelledOnuReuseService / SmartOLT)
        │             + crea /queue/simple en MikroTik (target = IP, max-limit = plan)
        ├─ 10. applyInstallationResult + save (estados PENDING/COMPLETE)
        ├─ 11. cierra InstallationOrder si viene
        └─ 12. ApplicationEventPublisher → SubscriptionRegisteredEvent(id)
                   AFTER_COMMIT → WhatsAppWelcomeRegistrationListener
```

**FIBER — estrategia:** `FiberInstallationStrategy`  
- VLAN desde `hostDevice.vlanId` (fallback `"1"`).  
- Autoriza ONU con `OnuAuthorizationRequest` (sn, olt, board, port, vlan, perfil `Generic_1`).  
- Cola MikroTik: `target = subscription.ip`.  
- **No llama a GenieACS.** No empuja `tr069-mgmt` en OLT.

Si OLT/MikroTik fallan, se captura la excepción, se persiste error y la API puede responder 200 con la suscripción ya creada (provisión pendiente).

---

## 4. Punto de extensión / acoplamiento (TR-069)

### Vínculo canónico ya existente
`Subscription.fiberOnu.sn` es la llave que el código actual usa para hablar con el ACS.

`CpeManagementService` (ya en el apartado de suscripciones) resuelve:

```
subscriptionId → Subscription → fiberOnu.sn → GenieAcsClient (NBI)
                                         ↘ OltMgrOnu (rxDbm / online OLT)
```

No usar `Subscription.cpe` (NetworkDevice de catálogo) como CPE TR-069. Es otro concepto.

### Dónde SÍ acoplar FASE 1 (recomendación de diseño, sin implementar aquí)

| Momento | Sitio | Por qué es seguro |
|---|---|---|
| Acciones de negocio (status, WiFi, reboot ACS) | `SubscriptionController` + `CpeManagementService` | Ya está. No toca el alta. |
| Tras alta FIBER, **fuera de la transacción** | Listener de `SubscriptionRegisteredEvent` (AFTER_COMMIT), igual que WhatsApp | El alta no debe fallar si el ACS está caído. |
| Reintento si el CPE aún no Informó | Extender el scheduler de provisión **o** un job ACS aparte | Hoy el scheduler solo reintenta MikroTik/OLT. |

### Dónde NO meter GenieACS
- Dentro de `registerSubscription` antes del `save` (bloquearía altas).
- Dentro de `FiberInstallationStrategy` en el mismo try que SmartOLT (mezclaría OMCI/OLT con CWMP; un fallo ACS marcaría `oltProvisionStatus` mal).
- Como reemplazo de `rebootFiberOnu` (hoy va a **SmartOLT/OLT**, no a GenieACS).

### Relación de datos técnicos

```
Cliente     → campos en Subscription (no hay entidad Customer)
Plan        → Subscription.plan (velocidad cola MikroTik; no se manda al ACS hoy)
IP          → Subscription.ip (MikroTik queue target; no se provisiona WAN por TR-069)
ONU serial  → Subscription.fiberOnu.sn  ★ vínculo ACS
OLT físico  → fiberOnu.olt_id / board / port + oltgateway OltMgrOnu
ACS device  → lookup NBI por SN (no persistido; GenieAcsClient.findDevice cada vez)
```

**Hueco de FASE 1 respecto al alta:** el registro FIBER autoriza en OLT y crea cola, pero **no** asocia `genieacsDeviceId`, **no** espera Inform, **no** aplica preset/SSID. El ACS solo se usa si alguien llama después a `/cpe-status` o `/wifi`.

---

## 5. Eventos de dominio y orquestación asíncrona

No hay bus de dominio (no Kafka, no outbox). Hay **Spring `ApplicationEventPublisher`** in-process.

| Evento | Cuándo | Consumidor | Uso |
|---|---|---|---|
| `SubscriptionRegisteredEvent(subscriptionId)` | Tras save + provisión, **en** `registerSubscription` | `WhatsAppWelcomeRegistrationListener` `@TransactionalEventListener(AFTER_COMMIT)` | WhatsApp de bienvenida |
| `SubscriptionChangedEvent(subscriptionId)` | Controller (alta, cancel, etc.) y cortes | Búsqueda / Meilisearch (`search.application`) | Reindexar ficha |

**Asíncrono ya existente (provisión, no ACS):**
- `SubscriptionProvisionReconciliationScheduler` — `fixedDelay` ~300 s, llama `reconcileDue()` para PENDING/FAILED MikroTik/OLT.
- `CompletableFuture` en generación masiva de simple queues (`createSubscriptionsSimpleQueue`).

**Hueco para ACS:** no hay listener que, al registrarse una FIBER, busque el device en GenieACS o encole un task. `SubscriptionRegisteredEvent` es el gancho natural (mismo patrón que WhatsApp: AFTER_COMMIT, fallos aislados).

---

## 6. Lo que ya existe de GenieACS (no duplicar)

Hay un **borrador operativo de FASE 1 de acciones**, desacoplado del alta:

| Pieza | Ubicación |
|---|---|
| Client NBI | `wispadmin.genieacs.GenieAcsClient` |
| Config | `genieacs.nbi.*` en `application-dev/prod/local.properties` |
| Servicio | `CpeManagementService` |
| API | `GET /subscription/{id}/cpe-status` |
| API | `PUT /subscription/{id}/wifi` |
| Reboot ONU | `PUT /subscription/reboot-fiber-onu` → **OLT/SmartOLT**, no NBI |

`getCpeStatus`: online/rx desde **oltgateway**; `lastInform` desde GenieACS (si falla NBI, no tumba el endpoint).  
`updateWifi`: `setParameterValues` + `connectionRequest` por SN.  
No hay tabla `cpe_*` ni columna `genieacs_device_id`. El device id se resuelve en cada llamada.

---

## 7. Implicaciones para el diseño de FASE 1 en el alta

1. Tratar Suscripciones como **orquestador de capas**, no como agregado DDD a reescribir.  
2. El SN de `fiberOnu` es el contrato de integración; no introducir un segundo identificador de cliente.  
3. Cualquier Inform/preset ACS debe ser **post-commit** (evento o scheduler), para no romper el alta ni la idempotencia `clientRequestId`.  
4. Distinguir tres provisiones: **MikroTik** (cola/IP), **OLT** (authorize OMCI), **ACS** (CWMP). Hoy solo las dos primeras tienen status en `Subscription`.  
5. Reutilizar `CpeManagementService` / `GenieAcsClient` para acciones; no abrir un controller paralelo fuera de `/subscription` si el producto vive en la ficha del cliente.

---

## Archivos de referencia

- `controller/SubscriptionController.kt`
- `service/SubscriptionService.kt` (`registerSubscription`)
- `service/SubscriptionProvisionService.kt`
- `service/subscription/strategies/FiberInstallationStrategy.kt`
- `service/subscription/SubscriptionRegisteredEvent.kt`
- `service/WhatsAppWelcomeRegistrationListener.kt`
- `data/model/Subscription.kt`, `Onu.kt`, `ProvisionStatus.kt`
- `service/CpeManagementService.kt`
- `genieacs/GenieAcsClient.kt`
