# Implementación del flujo de registro FIBER v2 con OMCI

Estado del documento: descripción del código implementado y del estado de homologación.  
Fecha de corte: 2026-09-23.  
Repositorios implicados: backend Core, OLT Gateway, ACS/GenieACS, clientes y Observability.  
Infraestructura de laboratorio: KVM4 `2.24.66.53`, OLT Huawei MA5608T y usuario OLT dedicado `gfbackend`.

Este documento describe el flujo v2 realmente implementado. No reemplaza el flujo v1 ni modifica los provisions históricos de GenieACS. Los puntos que aún necesitan prueba física están marcados como pendientes.

## 1. Objetivo funcional

Reducir el alta FIBER a una solicitud del cliente y ejecutar de manera durable, observable, reintentable y cancelable:

1. validar la Subscription y reservar su identidad;
2. preparar el usuario PPPoE en MikroTik;
3. autorizar la ONU y crear el transporte de gestión en la OLT;
4. crear por OMCI la WAN de gestión DHCP, VLAN 1000, y asociar TR-069;
5. esperar un Inform reciente y único en GenieACS;
6. crear la WAN de Internet PPPoE mediante un provision v2 nuevo;
7. configurar Wi-Fi mediante otro provision v2;
8. verificar evidencias y terminar el alta.

La ONU debe terminar con dos funciones WAN distintas:

| Función | Responsable | Configuración |
| --- | --- | --- |
| Gestión/TR-069 | OLT Gateway por OMCI | IP host 0, DHCP, VLAN 1000, prioridad 2 y perfil TR-069 del entorno |
| Internet | ACS/GenieACS por TR-069 | PPPoE, VLAN de la Subscription y credenciales del cliente |

La OLT prepara perfiles, GEM y service-ports. GenieACS crea los objetos WAN de Internet dentro de la ONU. No se usa TR-069 para crear la conectividad que TR-069 necesita para iniciar.

## 2. Arquitectura implementada

```text
Android / Backoffice
        |
        | POST /subscription (provisioningFlowVersion=2)
        v
Core: journal + worker durable + retry/cancel + outbox
        |
        +--> MikroTik: /ppp/secret
        |
        +--> OLT Gateway: autorización + service-port VLAN 1000
        |          |
        |          +--> Huawei CLI/OMCI: DHCP VLAN 1000 + perfil TR-069
        |
        +--> ACS API --> GenieACS NBI --> provisions v2 PPPoE/Wi-Fi
        |
        +--> Observability: eventos idempotentes por outbox
```

Responsabilidades:

| Componente | Responsabilidad exclusiva |
| --- | --- |
| Core | Crea la operación, conserva el orden, leases, checkpoints, snapshots cifrados, reintentos, cancelación e historial. |
| OLT Gateway | Es dueño de las escrituras OLT, serializa CLI y mantiene una reserva durable ONU ↔ operationId. |
| ACS | Identifica el CPE, guarda sus task IDs y snapshots Wi-Fi cifrados, y opera GenieACS. |
| GenieACS | Ejecuta únicamente los provisions nuevos de v2. |
| MikroTik | Autentica PPPoE; Core crea solo el secret propiedad de la operación. |
| Observability | Recibe una copia durable y deduplicada de cada transición/error. |

## 3. Entrada del flujo

El endpoint existente de alta recibe `SubscriptionRequest`. Para seleccionar v2 debe incluir:

```json
{
  "provisioningFlowVersion": 2,
  "installationType": "FIBER",
  "vlan": "<vlan-internet>",
  "wifiSsid24": "<ssid-2.4>",
  "wifiPassword24": "<clave-2.4>",
  "wifiSsid5": "<ssid-5>",
  "wifiPassword5": "<clave-5>",
  "onu": {
    "sn": "<serial>",
    "olt_id": "<olt>",
    "pon_type": "gpon",
    "board": "<slot>",
    "port": "<puerto>",
    "onu_type_name": "<tipo-onu>"
  }
}
```

Reglas de entrada implementadas:

- omitir `provisioningFlowVersion` conserva v1;
- solo se admite `null` o `2`;
- v2 requiere `PROVISIONING_V2_ENABLED=true`;
- solo admite Subscription FIBER;
- el serial se normaliza a mayúsculas y debe coincidir con la Subscription;
- la VLAN de Internet debe estar entre 1 y 4094 y no puede ser 1000;
- el perfil TR-069 debe estar configurado en el servidor;
- antes de planificar trabajo remoto se persisten la operación y el snapshot de registro cifrado.

Archivo de entrada: `ProvisioningV2RegistrationService.kt`. Integración con el alta: `SubscriptionService.registerSubscription()`.

## 4. Estado durable y modelo de ejecución

Las etapas, en orden, son:

```text
VALIDATE → MIKROTIK → OLT → OMCI → ACS_CONTACT → INTERNET → WIFI → VERIFY
```

Estados de operación:

```text
PENDING, RUNNING, WAITING, SUCCEEDED, FAILED,
CANCEL_REQUESTED, CANCELLING, CANCEL_FAILED, CANCELLED
```

Estados por etapa:

```text
PENDING, RUNNING, WAITING, SUCCEEDED, FAILED, COMPENSATED
```

Cada intento sigue este algoritmo:

1. `ProvisioningRecoveryScheduler` obtiene operaciones vencidas del entorno actual.
2. `ProvisioningJournal.claim()` adquiere un lease de 60 segundos.
3. El executor marca la etapa `RUNNING` e incrementa `attempts`.
4. El handler ejecuta primero `reconcile()`.
5. Solo si devuelve `NEEDS_APPLY`, ejecuta `apply()`.
6. `SATISFIED` guarda `SUCCEEDED`; `WAITING` difiere 5 segundos.
7. Un error se convierte en `ProvisioningFailure(code, message, retryable)` y queda en el journal.
8. Antes de toda escritura externa se comprueba el lease y que no haya cancelación solicitada.

El worker usa un hilo dedicado y recorre cada 5 segundos. Una operación que falla no bloquea las demás.

## 5. Persistencia

### Core — migración V56

| Tabla | Uso |
| --- | --- |
| `provisioning_v2_operation` | Operación serializada, revisión, estado, lease, próximo intento y entorno. |
| `provisioning_v2_event` | Historial y outbox de transiciones. |
| `provisioning_v2_resource` | Snapshots cifrados por `operationId + resourceKey`. |

Claves de recursos usadas:

```text
registration, mikrotik, olt, omci, acs-contact, internet, wifi
```

### OLT — migraciones V58/V2

`olt_provisioning_v2_onu_operation` reserva el serial para una sola operación y guarda hash de la solicitud, entorno, externalId, board, port, ONT-ID y etapa. Esto evita que un reintento adopte o borre una ONU ajena.

La tabla existe como V58 en el schema Core usado por el despliegue integrado y como V2 en el schema OLT Gateway separado.

### ACS — migración V6

`acs_onboarding_v2_task` registra cada acción (`INTERNET`, `INTERNET_COMPENSATE`, `WIFI`, `WIFI_COMPENSATE`) por operación, identidad del CPE, task ID y snapshots cifrados.

### Observability — migración V57

`obs_delivery_receipt` conserva el hash y resultado de cada entrega para deduplicar reenvíos del outbox.

## 6. Detalle de las ocho etapas

| Etapa | Reconciliación y efecto | Evidencia de éxito | Compensación |
| --- | --- | --- | --- |
| `VALIDATE` | Bloquea y valida Subscription, serial, acceso `PPPOE_DYNAMIC`, usuario/clave cifrada, perfil PPP y MikroTik activo. | Todas las precondiciones continúan válidas. | Sin efecto remoto. |
| `MIKROTIK` | Busca `/ppp/secret` por usuario. Si falta, captura baseline y crea el secret con comentario `GFv2-{environment}-{operationId}`. No modifica perfiles compartidos. | Lectura posterior coincide en nombre, perfil, servicio, estado y propietario. | Cierra sesiones activas propias y elimina únicamente el secret con el comentario esperado. |
| `OLT` | Reserva el serial, reconcilia/autoriza la ONU y asegura el service-port de gestión VLAN 1000. Conserva la VLAN Internet solicitada para el transporte de cliente. | `externalId`, board, port, ONT-ID válidos y `managementVlanReady=true`. | Borra únicamente la ONU reservada por esa operación y confirma que ya no existe antes de liberar la reserva. |
| `OMCI` | Usa la posición confirmada por OLT; asegura IP host 0 DHCP VLAN 1000 prioridad 2 y perfil TR-069. | Readback exacto de WAN de gestión y perfil; captura `omci`. | Ejecuta `undo` solo si WAN/perfil siguen siendo compatibles y pertenecen al target reservado. |
| `ACS_CONTACT` | Busca un único device por serial completo; no encola tareas. Valida modelo y firmware soportados. | Captura inmutable de `deviceId`, modelo y firmware. | Sin efecto remoto. |
| `INTERNET` | Encola `gf-onboarding-v2-pppoe`. Conserva la WAN de aprovisionamiento, borra las demás y crea Internet PPPoE o estática. | Task sin fault y la WAN propia en `Connected`. El fault del script se muestra con su código. | Encola `gf-onboarding-v2-compensate` para la WAN de Internet de esa operación, PPPoE o estática, y espera confirmación. |
| `WIFI` | ACS lee y cifra primero el baseline; luego encola `gf-onboarding-v2-wifi`. | Estado remoto coincide con SSID, clave y enabled esperados para ambas bandas. | Descifra el baseline dentro de ACS y encola `gf-onboarding-v2-compensate` en modo Wi-Fi. |
| `VERIFY` | Comprueba que existen evidencias `olt`, `omci`, `acs-contact`, `internet` y `wifi`. | Todas las etapas previas ya pasaron su propio readback. | Sin efecto remoto. |

`WAITING` no es un error: se usa mientras se espera Inform o finalización de una tarea GenieACS.

## 7. Implementación OLT/OMCI

### Autorización v2

Endpoint interno:

```text
POST /api/olt-gateway/onus/v2/authorize
POST /api/olt-gateway/onus/v2/compensate
```

La autorización:

- valida `operationId` y serial;
- calcula un fingerprint inmutable de la solicitud;
- reclama el serial antes de escribir en OLT;
- rechaza una ONU preexistente ajena a la operación;
- registra `externalId`, board, port y ONT-ID;
- asegura VLAN de gestión 1000;
- devuelve evidencia, no solo aceptación CLI.

### Gestión OMCI

Endpoints internos:

```text
POST /api/olt-gateway/onus/{sn}/omci/management
POST /api/olt-gateway/onus/{sn}/omci/management/compensate
```

Comandos Huawei implementados:

```text
interface gpon 0/{slot}
display ont info {port} {ontId}
display ont ipconfig {port} {ontId}
ont ipconfig {port} {ontId} ip-index 0 dhcp vlan 1000 priority 2
ont tr069-server-config {port} {ontId} profile-id {tr069ProfileId}
display ont info {port} {ontId}
display ont ipconfig {port} {ontId}
quit
```

Guardas implementadas:

- serial, slot, port y ONT-ID deben corresponder al mismo equipo;
- ONU debe estar `online` y `Config state: normal`;
- el perfil de línea debe exponer `TR069 management: Enable` e IP index 0;
- solo puede existir el host IP 0;
- una WAN existente debe ser DHCP, VLAN 1000 y prioridad 2;
- un perfil servidor existente debe coincidir con el solicitado;
- `Failure`, comandos desconocidos, parámetros inválidos o errores abortan;
- la única excepción es `Failure: The ONT does not configure IP information`, interpretada como estado inicial vacío;
- siempre se realiza readback después de escribir;
- el bloque `finally` sale del contexto GPON con `quit`.

La compensación verifica propiedad y compatibilidad antes de ejecutar:

```text
undo ont tr069-server-config {port} {ontId}
undo ont ipconfig {port} {ontId}
```

No elimina WAN desconocidas ni configuraciones incompatibles.

## 8. ACS y provisions GenieACS v2

API interna ACS:

```text
POST /api/acs/v1/onboarding-v2/contact
POST /api/acs/v1/onboarding-v2/internet
POST /api/acs/v1/onboarding-v2/internet/status
POST /api/acs/v1/onboarding-v2/internet/compensate
POST /api/acs/v1/onboarding-v2/internet/compensate/status
POST /api/acs/v1/onboarding-v2/wifi
POST /api/acs/v1/onboarding-v2/wifi/status
POST /api/acs/v1/onboarding-v2/wifi/compensate
POST /api/acs/v1/onboarding-v2/wifi/compensate/status
```

Provisions nuevos, separados del legado:

| ID | Función |
| --- | --- |
| `gf-onboarding-v2-pppoe` | Conserva la WAN de aprovisionamiento, borra las demás y crea la WAN de Internet PPPoE o estática de la operación. |
| `gf-onboarding-v2-wifi` | Configura las bandas 2.4 y 5 GHz después de capturar baseline. |
| `gf-onboarding-v2-compensate` | Retira PPPoE propio o restaura el baseline Wi-Fi. |

Los provisions validan `operationId`, serial esperado, modelo y firmware. No usan los scripts heredados y no purgan tareas globales del CPE.

Modelos admitidos actualmente por la compuerta: `F6600R` y `VSOLVA74`; la compatibilidad final depende de homologar sus paths y firmware reales.

La publicación está controlada por `GENIEACS_V2_PUBLISH_ENABLED`. Debe activarse de forma temporal, verificar los SHA-256 publicados y volver a `false`.

## 9. Reintento

Contrato público:

```text
POST /subscription/{subscriptionId}/provisioning/retry
```

```json
{
  "operationId": "<uuid>",
  "expectedRevision": 1
}
```

Comportamiento:

- exige la revisión vigente para evitar acciones de una pantalla desactualizada;
- solo vuelve a `PENDING` los checkpoints fallidos;
- conserva las etapas y recursos ya confirmados;
- cada etapa reconcilia el destino antes de repetir una escritura;
- no permite retry si existe un error no recuperable;
- no permite reactivar una operación en cancelación o cancelada.

## 10. Cancelación y reversión

Contrato público:

```text
POST /subscription/{subscriptionId}/provisioning/cancel
```

La solicitud persiste `CANCEL_REQUESTED`. El worker compensa únicamente etapas con `touched=true`, en este orden:

```text
VERIFY → WIFI → INTERNET → ACS_CONTACT → MIKROTIK → OMCI → OLT → VALIDATE
```

El orden conserva la gestión hasta que terminen las compensaciones ACS. Cada compensación se reconcilia y puede quedar en `CANCELLING` o `CANCEL_FAILED`. Solo cuando todas están `COMPENSATED` la operación pasa a `CANCELLED` y habilita un registro nuevo desde cero.

Una Subscription ya completada no se cancela con este endpoint; usa el flujo normal de baja.

## 11. Consulta, historial y acciones de cliente

```text
GET  /subscription/{subscriptionId}/provisioning
GET  /subscription/{subscriptionId}/provisioning?operationId={id}
GET  /subscription/{subscriptionId}/provisioning/history?operationId={id}&after={eventId}
POST /subscription/{subscriptionId}/provisioning/retry
POST /subscription/{subscriptionId}/provisioning/cancel
```

`ProvisioningProgress` entrega la operación completa y flags calculados por el servidor:

| Flag | Regla |
| --- | --- |
| `canRetry` | `FAILED` sin fallas no recuperables. |
| `canCancel` | `PENDING`, `RUNNING`, `WAITING` o `FAILED`. |
| `canRetryCancellation` | `CANCEL_FAILED`. |
| `canStartAgain` | `CANCELLED`. |

Android y backoffice deben usar estos flags; no deben deducir permisos solamente desde texto o porcentaje.

## 12. Observabilidad y trazabilidad

Cada checkpoint genera un evento durable en la misma transacción del journal. `ProvisioningOutbox` lo transforma en evento de Observability con:

```text
operationId/correlationId, subscriptionId, serial, environment,
flowVersion, revision, stage, attempt, state y errorCode
```

La entrega usa:

```text
POST {PROVISIONING_V2_OBS_BASE_URL}/observability/events
X-Obs-Delivery-Id: {environment}:{operationId}:{eventId}
```

Solo se marca entregado si el receptor responde 2xx, devuelve el mismo delivery ID y confirma un evento aceptado sin rechazos. El receptor guarda un recibo con hash; repetir el mismo payload es idempotente y cambiarlo para el mismo ID produce conflicto.

Credenciales PPPoE, contraseñas Wi-Fi, claves ACS y dumps sensibles no se incluyen en progreso, historial ni Observability.

## 13. Variables de entorno

| Variable | Uso | Valor seguro inicial |
| --- | --- | --- |
| `PROVISIONING_V2_ENABLED` | Permite seleccionar v2 en nuevas altas. | `false` |
| `PROVISIONING_V2_WORKER_ENABLED` | Registra executor y recuperación. | `false` |
| `PROVISIONING_V2_TR069_PROFILE_ID` | Perfil TR-069 fijado en cada snapshot. | `0` bloquea el alta |
| `OLT_GATEWAY_WRITES_ENABLED` | Habilita escrituras OLT en el dueño SSH. | `false`; KVM4 requiere `true` |
| `OLT_GATEWAY_V2_WRITE_MAX_RETRY_ATTEMPTS` | Reintentos máximos por efecto v2. | `2` |
| `PROVISIONING_V2_ACS_BASELINE_KEY` | AES-GCM para baseline/deseado Wi-Fi en ACS. | obligatorio antes de Wi-Fi |
| `GENIEACS_V2_PUBLISH_ENABLED` | Publicación explícita de provisions. | `false` |
| `PROVISIONING_V2_TELEMETRY_ENABLED` | Activa outbox hacia Observability. | `false` hasta configurar receptor |
| `PROVISIONING_V2_OBS_BASE_URL` | Receptor de eventos, con context path. | vacío |
| `PROVISIONING_V2_OBS_API_KEY` | Autenticación emisor/receptor. | secreto obligatorio |
| `PROVISIONING_V2_OUTBOX_DELAY_MS` | Frecuencia de entrega. | `5000` |

KVM4 debe usar `OLT_GATEWAY_USERNAME=gfbackend`. El VPS legado conserva `oltadmin`. Ninguna contraseña se documenta o versiona.

## 14. Orden de despliegue

1. Respaldar y aplicar V56, V57, V58/V2 y ACS V6 en los schemas correctos.
2. Desplegar Gateway/ACS/Core con todos los feature flags v2 en `false`.
3. Configurar secretos y perfil TR-069.
4. Activar temporalmente `GENIEACS_V2_PUBLISH_ENABLED=true`, comprobar provisions y hashes, y volver a `false`.
5. Habilitar `OLT_GATEWAY_WRITES_ENABLED=true` únicamente en el KVM4 dueño del SSH OLT.
6. Habilitar telemetría y comprobar recepción/deduplicación.
7. Habilitar `PROVISIONING_V2_WORKER_ENABLED=true`.
8. Habilitar `PROVISIONING_V2_ENABLED=true` para admitir nuevas solicitudes v2.
9. Realizar una alta de laboratorio completa antes de ampliar el rollout.

El script estándar de despliegue exige un árbol limpio. No se debe saltar esa guarda en un despliegue normal ni crear un commit que mezcle cambios ajenos.

## 15. Pruebas automatizadas

Comandos principales:

```bash
./gradlew :core:test --tests '*provisioningv2.*' --console=plain
./gradlew :oltgateway:test --tests '*OmciManagementV2Test' --tests '*OnuOmciManagementControllerTest' --tests '*OltCommandExecutorRetryTest' --console=plain
node --test scripts/genieacs/provisions/test/gf-onboarding-v2.test.js
./gradlew :core:war :core:tomcatLibs -Pdjl.linux --console=plain
git diff --check
```

Cobertura implementada:

- transiciones y orden de compensación;
- leases, recuperación, retry/cancel y revisión obsoleta;
- rollback transaccional del journal;
- cifrado y propiedad de recursos;
- outbox y deduplicación;
- validaciones de Subscription/MikroTik;
- autorización durable OLT y límites de reintento;
- OMCI idempotente, estado vacío, conflictos y fallas CLI;
- contratos HTTP Core/Gateway/ACS;
- provisions PPPoE, Wi-Fi y compensación.

## 16. Evidencia física obtenida

ONU de prueba: `HWTC9F4BE990`, slot 1, puerto 6, ONT-ID 116.

Resultado confirmado por el endpoint OMCI:

```json
{"configured":true,"address":"10.64.47.252"}
```

Se confirmó:

- autorización de ONU;
- WAN de gestión DHCP;
- VLAN 1000;
- prioridad 2;
- perfil TR-069 2;
- lectura posterior exitosa.

No se confirmó dentro de la ventana observada:

- primer Inform de esa ONU en GenieACS;
- creación real de la WAN PPPoE v2;
- configuración Wi-Fi v2;
- cancelación completa sobre hardware y alta posterior desde cero.

## 17. Pendientes de homologación y cierre

1. Terminar y desplegar la corrección del ciclo de arranque NetDiag → Gateway local; su prueba fue interrumpida y todavía no debe marcarse como aprobada.
2. Reactivar NetDiag después de desplegar esa corrección y comprobar que Tomcat llega a `UP` antes de la primera sincronización.
3. Diagnosticar el Inform ausente de `HWTC9F4BE990`: perfil TR-069 2, URL ACS, DHCP/ruta VLAN 1000 y logs CWMP.
4. Ejecutar alta completa desde ONU reseteada para cada modelo/firmware soportado.
5. Verificar que PPPoE autentica y aparece la sesión esperada en MikroTik.
6. Verificar Wi-Fi en ambas bandas y readback de credenciales/estado.
7. Forzar un fallo en cada etapa, usar Reintentar y confirmar que continúa desde el checkpoint correcto.
8. Cancelar en cada etapa y comprobar la reversión real, incluyendo una nueva alta desde cero.
9. Confirmar que todos los errores aparecen con el mismo operationId en Android, backoffice y Observability.
10. Mantener v1 sin regresiones y no modificar scripts GenieACS existentes.

No declarar el flujo homologado hasta completar los puntos 3–9 con evidencia por modelo y firmware.

## 18. Archivos principales

| Área | Archivos |
| --- | --- |
| Core/orquestación | `ProvisioningOperation.kt`, `ProvisioningExecutor.kt`, `ProvisioningJournal.kt`, `ProvisioningV2RegistrationService.kt`, `ProvisioningControlService.kt`, `ProvisioningRecoveryScheduler.kt` |
| Etapas | `*ProvisioningStageHandler.kt` en `core/.../service/provisioningv2/` |
| API pública | `ProvisioningV2Controller.kt`, `SubscriptionService.kt`, `SubscriptionRequest.kt` |
| Gateway | `OnuV2AuthorizationController.kt`, `ProvisioningV2OnuOwnershipService.kt`, `OnuOmciManagementController.kt`, `OmciManagementV2.kt` |
| ACS | `OnboardingV2AcsController.kt`, `OnboardingV2AcsContactService.kt`, `OnboardingV2TaskService.kt`, `OnboardingV2BaselineCipher.kt` |
| GenieACS | `gf-onboarding-v2-pppoe.js`, `gf-onboarding-v2-wifi.js`, `gf-onboarding-v2-compensate.js` |
| Observability | `ProvisioningOutbox.kt`, `ProvisioningTelemetryDelivery.kt`, `ObsDurableIngestionService.kt` |
| Migraciones | Core V56/V57/V58, Gateway V2 y ACS V6 |
