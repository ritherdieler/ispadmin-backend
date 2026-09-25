# Runbook de implementación — Alta FIBER v2 con OMCI y TR-069

> La descripción consolidada de la implementación real, sus contratos, estados, despliegue y pendientes de homologación está en `implementacion-alta-fiber-v2-omci.md`. Este runbook conserva el historial de decisiones y el plan detallado de trabajo.

Estado: motor, worker de ocho etapas, contratos y compensaciones desplegados en staging KVM4. La homologación física por modelo/firmware sigue pendiente; v2 está habilitado únicamente en `tomcat-staging` de KVM4.
Fecha de actualización: 2026-09-23. Destinatario: Cursor / equipo de implementación.

## Avance de integración — 2026-09-23

- Core expone consulta de la última operación por suscripción, historial paginado y acciones retry/cancel, aisladas por entorno. `POST /subscription` acepta explícitamente `provisioningFlowVersion=2`, conserva v1 si se omite y, mientras `PROVISIONING_V2_ENABLED=false`, no permite seleccionar v2. Al seleccionarlo captura un snapshot cifrado antes de planificar trabajo. El worker completo se registra únicamente con `PROVISIONING_V2_WORKER_ENABLED=true`.
- Outbox HTTP hacia Observability con confirmación explícita de entrega y recibo persistente de deduplicación. Scheduler dedicado, deshabilitado por defecto. Migración adicional `V57__obs_delivery_receipt.sql`; no aplicada fuera de pruebas. Variables documentadas en el catálogo maestro y `scripts/provisioning-v2.env.example`.
- Journal y snapshots reciben el gestor transaccional JPA del Core. Se reprodujo con H2/Hibernate que usar un gestor JDBC independiente permitía confirmar el alta cuando el caller atrapaba un error del journal. La prueba ahora exige rollback global y pasa con el gestor compartido. Esto prepara la atomicidad; todavía no conecta el POST de alta.
- Backoffice: panel dentro de `SubscriptionAcsModal`, progreso, historial, reintento y cancelación confirmada; comandos usan la revisión del servidor. Build y pruebas del modal verificados en la iteración anterior. Falta enlazar nueva alta tras cancelación y Observability.
- Android: contratos de dominio, repositorio Retrofit, ViewModel UDF y pantalla de progreso accesible desde el detalle FIBER. Cancelación confirmada, reintento y navegación a nuevo registro según permiso del servidor. Se corrigió con RED/GREEN el bloqueo de botones al salir durante una acción y volver. Falta integrar la entrada desde el alta en curso y el historial completo.
- Cancelar adelanta una operación diferida para iniciar compensación. MikroTik se compensa antes de retirar gestión OMCI; todavía falta implementar esas compensaciones remotas.
- Worker recuperable: `ProvisioningRecoveryScheduler` recorre únicamente operaciones vencidas/due del entorno y aísla una falla por operación. Se registra con las ocho etapas cuando `PROVISIONING_V2_WORKER_ENABLED=true`.
- Etapas reales ya incorporadas: `VALIDATE` comprueba identidad ONU, PPPoE dinámico, usuario/credencial, perfil y MikroTik; `MIKROTIK` captura un baseline cifrado antes de crear el secret con comentario `GFv2-{entorno}-{operación}`, hace lectura posterior, rechaza secrets ajenos y en cancelación elimina únicamente su secret/sesiones. No modifica perfiles PPP compartidos.
- `ProvisioningStageContext` entrega lease y almacenamiento cifrado a los adaptadores. `captureInitial` conserva datos de alta antes de que un worker pueda reclamar la operación, y es inmutable.
- Gateway: `POST /api/olt-gateway/onus/v2/authorize` reserva durablemente la identidad de la ONU antes de autorizarla y preparar únicamente el service-port de gestión VLAN 1000; nunca invoca ACS/CPE. Reintentos del mismo `operationId` reconcilian la reserva; `POST /api/olt-gateway/onus/v2/compensate` borra solo la ONU reservada y libera la identidad después de confirmar el borrado. La migración Gateway `V2__provisioning_v2_onu_operation.sql` está aplicada en KVM4 staging.
- OLT y OMCI: Core tiene adaptadores para las etapas `OLT` y `OMCI`, que fijan el target físico, la VLAN de Internet y el perfil TR-069 en snapshots cifrados. La autorización conserva la VLAN de Internet para el transporte PPPoE y añade por separado el service-port de gestión 1000; rechaza usar 1000 como VLAN de Internet. Gateway ejecuta la gestión en una sola sesión SSH serializada y su compensación solo emite los `undo` compatibles con DHCP/VLAN 1000/perfil esperado. La escritura v2 usa `OLT_GATEWAY_V2_WRITE_MAX_RETRY_ATTEMPTS` (por defecto 2), por lo que no hereda el retry SSH ilimitado del legado. Los adaptadores están registrados en el worker v2.
- ACS: `POST /api/acs/v1/onboarding-v2/contact` es una compuerta de solo lectura. Exige un único device cuyo serial completo coincida, modelo soportado (`F6600R` o `VSOLVA74`) y firmware conocido. `WAITING` no es error: el worker se difiere hasta recibir Inform. Core captura `deviceId/model/firmware` y abortará si cambian antes de los scripts. No toca colas ni publica provisions existentes.
- ACS Internet: la migración ACS `V6__onboarding_v2_task.sql` crea un journal aislado por `operationId + acción`. `POST /api/acs/v1/onboarding-v2/internet` guarda primero solo identidad de ONU/device/model/firmware y luego encola `gf-onboarding-v2-pppoe`; contraseña y usuario pasan como payload sensible hacia NBI y no se persisten ni se escriben en logs. Un `INTENT` sin task ID se reintenta con el mismo `operationId`, por lo que depende de la idempotencia del script y nunca purga tareas/faults ajenos. El worker la invoca después de que `ACS_CONTACT` confirme identidad. El controlador de Internet/Wi-Fi se registra con la misma condición que el datasource ACS (`acs.datasource.url` no vacío y subsistema ACS activo). No depende de `@ConditionalOnBean`: esa condición se evaluaba antes de existir el servicio y staging respondía 404 en `/onboarding-v2/internet`.
- ACS Wi‑Fi: el baseline (SSID, clave y estado de cada banda) se lee únicamente dentro de ACS antes de encolar la mutación, se cifra con `PROVISIONING_V2_ACS_BASELINE_KEY` en la fila ACS y solo se descifra localmente al encolar `gf-onboarding-v2-compensate`. Ningún endpoint devuelve ese baseline ni las claves a Core. Si la lectura o clave de cifrado falta, falla antes de modificar Wi‑Fi.
- Worker: `PROVISIONING_V2_WORKER_ENABLED=false` protege el executor de ocho etapas y el scheduler recuperable. Cuando se habilita junto con selección v2, realiza `VALIDATE → MIKROTIK → OLT → OMCI → ACS_CONTACT → INTERNET → WIFI → VERIFY`; cada WAN solo pasa tras lectura de estado. Compensación corre en orden inverso y deja `WAITING` cuando GenieACS aún no confirma la eliminación/restauración.
- Publicación: `acs:processResources` empaqueta los tres scripts v2. `GENIEACS_V2_PUBLISH_ENABLED=false` por defecto; al habilitarse una vez, el publisher carga exactamente esos IDs y registra SHA-256. No modifica el bootstrap heredado ni publica por defecto.

Verificaciones locales de esta continuación:

```bash
# Backend/Gateway: journal, scheduler, validate, MikroTik, OMCI y contratos internos.
./gradlew :core:test --tests '*provisioningv2.*' --tests '*OltGatewayHttpClientTest' :oltgateway:test --tests '*OmciManagementV2Test' --tests '*OnuOmciManagementControllerTest' --tests '*OltCommandExecutorRetryTest' --console=plain
# Android: compilación y pruebas de pantalla/estado/navegación/contrato HTTP.
./gradlew :presentation:compileDevDebugKotlin :presentation:testDevDebugUnitTest --tests '*Provisioning*Test' :data:testDebugUnitTest --tests '*ProvisioningRepositoryTest' --console=plain
```

No confundir estos resultados con homologación: después de esta verificación local se desplegó KVM4 staging y se publicaron los scripts v2; no se han realizado cambios sobre una ONU v2 porque la ONU de prueba sigue sin detectarse. Mantener la homologación separada de estas pruebas de código.

## Estado de implementación — 2026-09-22

Implementado y probado localmente:

- `core/.../service/provisioningv2/`: transiciones de etapas, retry/cancel, worker de un paso por ejecución, journal SQL con leases, checkpoints/outbox atómicos, control por entorno/suscripción y snapshots cifrados e inmutables.
- Migración aditiva `V56__provisioning_v2_journal.sql`, sin aplicar en staging/prod.
- `ProvisioningV2Controller`: contratos GET de progreso y POST retry/cancel, condicionales a la existencia del servicio v2. No se registró un runtime incompleto ni se activó selección v2 en el alta.
- `OmciManagementV2`: componente CLI con validación de identidad/ubicación, perfil compatible, contexto GPON, aplicación idempotente y lectura posterior. Solo pruebas simuladas; no conectado aún al endpoint Gateway ni al worker.
- Nuevos scripts `gf-onboarding-v2-pppoe`, `gf-onboarding-v2-wifi`, `gf-onboarding-v2-compensate`: identidad/modelo/firmware y propiedad por nombre de operación. En cada ONU la primera WAN queda como aprovisionamiento; cualquier otra se borra y se crea la de Internet en PPPoE o IP estática. El fallo del script llega al cliente con su código. Restauración WiFi con snapshot.

Pendientes obligatorios para terminar (no confundir las pruebas unitarias con el alta implementada):

1. Homologación controlada de los tres scripts con los firmware reales y verificación de los paths reportados por GenieACS. El código implementa `INTERNET`, `WIFI` y `VERIFY`; el paso Wi‑Fi se bloquea de forma segura si GenieACS no permite leer baseline completo.
2. Despliegue coordinado de migraciones Core/Gateway/ACS, publicación explícita de scripts con hash y habilitación gradual de `PROVISIONING_V2_ENABLED`/`PROVISIONING_V2_WORKER_ENABLED`. La selección sigue bloqueada por defecto.
3. Compensaciones reales de MikroTik/OLT/Core; recursos y reservas de negocio, efectos secundarios irreversibles y captura fiable de credenciales WiFi previas. Verificar eliminación/restauración del contenedor WAN VSOL creado por la operación: el script actual compensa PPP, no elimina WCD.2.
4. Publicación inmutable de scripts/manifiesto y correlación de tareas ACS con observaciones frescas. Homologar creación por filtro Name y asignación de instancias en firmware real.
5. Completar trazas por intento y enlaces a Observability desde los clientes; verificar rollback/deduplicación del receptor con base real de pruebas. Ya existen consumidor HTTP, recibo durable e historial consultable (ver avance del 23).
6. Completar integración de los clientes con el alta en curso, nueva alta tras cancelación y actualización del historial. Panel backoffice y pantalla Android implementados; no constituyen todavía un flujo operativo de extremo a extremo.
7. Prueba extremo a extremo y homologación: el usuario confirmó que VSOL `HWTC9F4BF950` ya está reseteada; conectará ZTE después de terminar con VSOL. No se abrió SSH ni se modificó la VSOL durante este trabajo de código.

Pruebas ejecutadas con resultado satisfactorio:

```bash
./gradlew :core:test --tests '*provisioningv2.*' :oltgateway:test --tests '*OmciManagementV2Test' --tests '*OltGatewayCommandServiceTest' --tests '*OnuActivationServiceTest' --tests '*ActivationRecoveryTest' --tests '*ActivationDurabilityTest' --tests '*ActivationIdentityRegressionTest' --console=plain
node --test scripts/genieacs/provisions/test/gf-onboarding-v2.test.js scripts/genieacs/provisions/test/gf-pppoe-wan2-poc.test.js scripts/genieacs/provisions/test/gf-wifi-ssid-poc.test.js
```

Se observaron fallos RED de comportamiento antes de implementar transiciones, journal, worker, snapshots, servicio de control y scripts. Los contratos del controlador se verificaron después de escribirlo; no afirmar TDD completo de ese componente. Node: 25 pruebas, incluyendo 8 nuevas de v2. No hubo deploy, publicación NBI ni prueba real de alta.

Referencia de API GenieACS para filtros alias, creación declarativa e idempotencia: [documentación oficial de provisions](https://docs.genieacs.com/en/stable/provisions.html). Que el servidor soporte filtros no demuestra homologación de los objetos del firmware.

## 1. Objetivo y requisitos acordados

El técnico entrega una ONU reseteada de fábrica, conecta la fibra y realiza una sola alta. El backend configura gestión, Internet y WiFi; permite reanudar errores y cancelar con reversión verificable.

| WAN | Configuración | Responsable |
|---|---|---|
| Gestión | DHCP, VLAN 1000; transporte de TR-069 hacia GenieACS | OLT/Gateway mediante OMCI |
| Internet | PPPoE, credenciales del cliente, VLAN de servicio | Nuevo provision GenieACS mediante TR-069 |

OLT prepara los GEM y service-ports de ambas VLAN. Esto habilita transporte, no sustituye la creación de la conexión PPPoE dentro de la ONU. Primera/segunda WAN describen funciones: los índices y objetos internos difieren por modelo.

Obligatorio:

- No modificar ni sobrescribir scripts GenieACS existentes. Crear scripts nuevos para v2.
- Mantener comportamiento v1: Gateway es compartido entre staging y producción.
- Core es la única fachada pública para Android, backoffice e integraciones.
- Reintentar continúa desde la primera etapa pendiente/fallida, reconciliando efectos inciertos.
- Cancelar registro revierte efectos de esa operación y permite nueva alta desde cero cuando se verifica la limpieza.
- Todos los errores y recuperaciones son visibles en clientes y Observability.
- No ejecutar factory reset automático: lo realiza el técnico.
- Primera homologación: ZTE F6600R y VSOLVA74 por firmware, no compatibilidad genérica por marca.

## 2. Reglas de trabajo para Cursor

Leer `../AGENTS.md`, `AGENTS.md` del backend y las instrucciones específicas de cada repo afectado. Aplicar TDD para lógica y contratos. No cambiar stack, ni introducir acceso JDBC cruzado entre schemas. Mantener secretos fuera de logs/documentación.

Repos involucrados: `ispadmin-backend`, `IpsAdmin-android app`, `ispadmin-backoffice` e `ispadmin-observability-web`. No implementar solo backend y declarar terminado: incluye botones, progreso y trazabilidad visibles.

Este runbook autoriza el diseño de implementación, no presupone despliegues ni cambios sobre ONUs de clientes. Ejecutar pruebas reales sobre laboratorio autorizado. No crear commits/push salvo petición.

## 3. Código y evidencia de partida

Buscar estas clases por nombre y revisar sus tests antes de editar:

| Componente | Hallazgo en la inspección |
|---|---|
| `FiberInstallationStrategy` | Gateway puede iniciar ACS antes de que Core termine MikroTik. |
| `OnuActivationService` | Autoriza y despacha ACS; puede borrar/reautorizar una ONU existente. |
| `OltGatewayCommandService` | Genera autorización/service-ports; falta integrar DHCP y servidor TR-069 en el alta. |
| `SmartOltAuthorizeProfileResolver` | Selección de gestión ligada a VLAN 100 y propiedades labAcs. |
| `JdbcActivationJournal` | Journal durable existente, indexado por serial; operación inspeccionada sin entorno explícito. |
| `NamedCpeProvisioner` | Esperas bloqueantes, listado repetido, purga de cola y error compartido mutable. |
| `GenieAcsNamedProvisionBootstrap` | Publica scripts por nombre al arrancar. |
| `RegistrationProgressDto` | Contrato actual a extender de manera compatible. |
| `OltCliBus`, `OltCliSessionPool` | Infraestructura SSH a reutilizar. |
| `WifiInformNotifyService`, `CpeInformIngestService` | Notificaciones de Inform existentes. |

Documentos de referencia en `.agent-docs/`: `onu-zte-genieacs-omci.md`, `olt-gateway-comandos-catalogo.md`, `pruebas-local-gateway-acs-lab.md`, `genieacs-scripts-cleancode.md`, `subsistemas-desacople-transporte.md` y `vps-secrets-management.md`.

Evidencia manual VSOL HWTC9F4BF950: OLT mostró online, config normal, gestión DHCP VLAN 1000 con IP y TR-069 Enable/perfil 2. También mostró Match state mismatch. No se verificó Inform posterior en esa ejecución. Investigar el mismatch y completar prueba extremo a extremo antes de homologar. ONT-ID 39 y perfiles 30/13/2 son datos del laboratorio, no constantes universales.

## 4. Arquitectura y aislamiento v1/v2

- Core orquesta suscripción, preparación MikroTik y dependencias. Gateway administra OLT/OMCI. ACS administra tareas GenieACS.
- Selección explícita `flowVersion=2` por Core; solicitudes sin versión conservan v1. Habilitación por entorno y política OLT/modelo/firmware.
- Entorno derivado de identidad autenticada; comprobar coherencia del header, nunca confiar únicamente en un campo del cliente.
- Persistir entorno, versión, operación y revisión en trabajos y eventos. Recuperación nunca usa el entorno predeterminado del worker.
- Añadir almacenamiento v2 separado del journal v1; no convertir operaciones activas anteriores.
- Exclusión física por OLT/ONU entre versiones y entornos. La separación lógica de journals no permite dos escritores concurrentes sobre la misma ONU.
- Cambios en infraestructura compartida deben ser aditivos y tener tests de regresión v1. Staging no puede intervenir ONUs asignadas a producción.
- Publicar scripts nuevos mediante paso de despliegue controlado y hash. No ampliar el bootstrap existente para sobrescribir scripts v2 en cada arranque.
- Una operación fija los identificadores/hash de sus scripts. Revisiones incompatibles requieren nombres nuevos; staging no reemplaza un script usado por producción.

## 5. Persistencia y etapas

Persistir operación, etapas, intentos, recursos/compensaciones y outbox. Cada módulo escribe únicamente su schema y coordina por contratos existentes HTTP/eventos.

Datos mínimos: operationId, subscriptionId, environment, flowVersion, revision, serial normalizado, oltId/ubicación, etapa/estado, intentos, plazos, próxima ejecución, lease y token de propietario, error saneado, contexto de traza, recursos afectados y task IDs GenieACS. Cifrar credenciales y snapshots necesarios para restauración.

Guardar intención antes de cada escritura externa y evidencia después. Un efecto externo no es una transacción SQL: si falta respuesta, dejar resultado incierto y reconciliar leyendo el sistema destino.

Etapas:

1. VALIDATE: identidad, propiedad, compatibilidad y política de recursos.
2. MIKROTIK_READY: secret y perfil PPPoE preparados.
3. OLT_READY: autorización, perfiles, GEM y service-ports.
4. OMCI_READY: DHCP gestión y asociación TR-069 aplicados.
5. ACS_CONTACT: gestión verificada e Inform reciente correlacionado.
6. INTERNET_READY: WAN PPPoE aplicada y verificada.
7. WIFI_READY: WiFi aplicado y verificado.
8. VERIFY: dos WAN funcionales, gestión conservada y persistencia OLT confirmada.

MIKROTIK_READY y preparación OLT pueden avanzar en paralelo. INTERNET_READY exige MikroTik, gestión y contacto ACS. Estados de etapa: PENDING, RUNNING, WAITING, SUCCEEDED, FAILED. No mantener hilos, sesiones SSH o transacciones SQL durante esperas de DHCP/Inform/CWMP.

## 6. Implementación OMCI

Resolver serial/posición/ONT-ID; nunca fijar coordenadas del ensayo. Asegurar autorización compatible y transporte sin borrar/reautorizar en cada reintento.

Política homologada: VLAN gestión, GEM, IP-index, prioridad, perfil de línea, servicio y servidor TR-069 por OLT/modelo/firmware. Preparar perfiles compartidos fuera del alta; no mutarlos en cada registro.

Comandos de referencia de la OLT probada, dentro del contexto indicado:

```text
interface gpon 0/{slot}
ont ipconfig {port} {ontId} ip-index 0 dhcp vlan 1000 priority 2
ont tr069-server-config {port} {ontId} profile-id {tr069ProfileId}
display ont info {port} {ontId}
display ont ipconfig {port} {ontId}
quit
display service-port port 0/{slot}/{port} ont {ontId}
```

Autorización, GEM/mapeos y service-ports deben existir previamente. Reutilizar planificador y bus de comandos; validar cada respuesta, incluida transición de contexto. Un prompt no significa comando exitoso.

Verificar IP DHCP válida, VLAN, estado de servicio y configuración. Después exigir Inform reciente identificado; una fila vieja de GenieACS no prueba conectividad actual. Registrar guardado OLT y confirmar finalización; agrupar guardados compatibles por OLT sin perder qué operaciones quedaron persistidas.

## 7. Nuevos provisions GenieACS

Crear `gf-onboarding-v2-pppoe`, `gf-onboarding-v2-wifi` y `gf-onboarding-v2-compensate` como artefactos nuevos, con tests y manifiesto de publicación.

- Layout por modelo; no copiar supuestos de índices sin prueba desde factory reset + OMCI.
- Proteger WAN de gestión contra borrados y cambios de VLAN/servicio.
- La primera WAN es la de aprovisionamiento y no se borra. Cualquier otra WAN se elimina y se crea una sola de Internet, PPPoE o IP estática.
- PPPoE configura VLAN, credenciales y parámetros necesarios según modelo. WiFi queda separado para reintentar sin reconstruir WAN.
- Tareas registradas por operación/revisión; consultar o reintentar el task ID existente. No purgar todas las tareas del dispositivo.
- Fallo del Connection Request no implica que la tarea no se haya encolado.
- Verificar datos recientes y estado conectado, no solo HTTP 200/202 o prefijo de IP. Correlacionar PPPoE esperado y sesión MikroTik donde sea necesario para evitar falso éxito.
- Usar Inform y consultas específicas por identidad, con reconciliación acotada; no listar continuamente todos los dispositivos.

## 8. Reintentar

Contrato Core propuesto: `POST /subscription/{id}/provisioning/retry`, cuerpo con `operationId` y `expectedRevision`. Retorna estado/aceptación de operación sin esperar el aprovisionamiento completo.

- Continúa misma operación con nuevo intento desde primera etapa pendiente/fallida.
- Reconciliar escrituras inciertas antes de repetir; conservar recursos y etapas válidas.
- Doble clic y llamadas concurrentes devuelven ejecución activa, sin trabajo duplicado.
- Una corrección explícita crea nueva revisión e invalida solo etapas afectadas. Revisión obsoleta: conflicto sin mutaciones.
- Si hubo reset físico, invalidar checkpoints que ya no coincidan con el equipo y reconstruir sus dependientes.
- Operación terminada: devolver resultado. Durante cancelación o tras cancelada: no reanudar alta.
- Reintentos automáticos acotados con backoff/jitter comparten el mismo ejecutor que el botón. Plazos/configuración deben distinguir espera normal, error transitorio e intervención requerida.

## 9. Cancelar registro

Contrato Core propuesto: `POST /subscription/{id}/provisioning/cancel`, cuerpo con `operationId` y `expectedRevision`. Estados: CANCEL_REQUESTED, CANCELLING, CANCEL_FAILED, CANCELLED.

Disponible para altas en curso/fallidas. Una suscripción ya completada usa el flujo de baja existente. Solicitud repetida es idempotente; CANCEL_FAILED permite Reintentar cancelación.

Primero persistir intención de cancelar, detener nuevas etapas y adquirir exclusión. Si hay comando/tarea en vuelo, esperar/reconciliar su efecto antes de compensar. Eventos atrasados no reactivan ni completan la operación.

Orden de compensación:

1. Retirar tareas pendientes propias. Reconciliar las CWMP en ejecución.
2. Conservar gestión; restaurar WiFi y retirar/restaurar PPPoE de la operación.
3. Retirar/restaurar recursos MikroTik propios.
4. Deshacer asociaciones TR-069, IP host, service-ports y autorización creados por la operación, respetando recursos preexistentes.
5. Liberar reservas/asociaciones del registro en Core y marcar CANCELLED solo tras verificación.

Checkpoint por compensación; continuar acciones independientes seguras y conservar dependencias para las pendientes. No retirar gestión antes de terminar acciones TR-069.

Capturar estado anterior antes de modificar, cifrando valores sensibles. Si no se puede garantizar restauración para un modelo (por ejemplo contraseña WiFi no legible), no prometer reversión: es un bloqueo de homologación que requiere resolver antes de habilitarlo.

No eliminar perfiles compartidos, scripts o recursos ajenos. No usar factory reset automático como compensación. ONU inaccesible: dejar CANCEL_FAILED con pendientes y acción técnica; nunca declarar limpieza ficticia.

Nuevo registro habilitado solo cuando limpieza esté verificada. Crea nueva operación y conserva historial de la cancelada. Revisar efectos colaterales del registro existente (reservas, adjuntos, notificaciones, facturación): diferir efectos irreversibles hasta éxito y compensar únicamente los recursos propios reversibles. Un mensaje ya enviado no puede considerarse reversible.

## 10. Clientes y Observability

Ampliar `registration-progress` de forma aditiva: operación, revisión, etapa detallada, intento, error estructurado, acciones permitidas, próxima ejecución y progreso de cancelación. Mantener campos/valores previos compatibles para clientes v1.

Android/backoffice muestran avance, causa comprensible y referencia; botones Reintentar, Cancelar registro (confirmación), Reintentar cancelación y Nuevo registro según estado. Persistencia backend permite cerrar/reabrir cliente sin perder trabajo.

Cada error contiene código estable, mensaje de usuario y causa técnica saneada, entorno/versión, operación/revisión/intento, suscripción/serial/OLT, etapa, hora y recuperabilidad. Incluir task ID, script y fault CWMP cuando aplique. No depender del status HTTP: fallos CLI/CWMP con HTTP 200 también son errores.

Usar operationId estable y trazas correlacionadas por intento. Propagar por HTTP, eventos y workers. Publicar mediante outbox durable con deduplicación; caída de Observability no pierde diagnóstico ni revierte altas exitosas. Errores no se muestrean fuera del historial.

Backoffice enlaza suscripción/historial con Observability. Buscar por operación, suscripción, serial, entorno y versión; mostrar fallos y recuperación. No registrar contraseñas PPPoE/WiFi, credenciales ACS ni dumps que las contengan.

## 11. Concurrencia y recuperación

Usar SQL durable y eventos existentes, no otro broker. Serializar escrituras por OLT, con leases y fencing entre workers; OLT distintas pueden procesarse en paralelo. Preservar límites VTY y evitar SSH paralelo fuera del bus.

Tras timeout/reinicio, el nuevo propietario reconcilia antes de repetir. Un worker que perdió lease no puede confirmar etapas ni enviar nuevas escrituras. Reconciliar periódicamente trabajos pendientes y entrega outbox; consultas por lotes acotados.

La versión de cada operación queda fijada. Desactivar v2 frena nuevas altas pero mantiene capacidad de concluir/reintentar/cancelar operaciones v2 existentes.

## 12. Pruebas y aceptación

- RED/GREEN/REFACTOR por módulo; contratos y regresión v1.
- Staging no cambia scripts, tareas ni operaciones prod; seguridad de entorno y mismo serial concurrente.
- Fallo antes/después de cada escritura, timeout tras éxito real, reinicio y recuperación sin duplicados.
- Doble clic/reenvíos; lease vencido y worker antiguo; revisión obsoleta.
- DHCP ausente, ACS caído, Inform atrasado/duplicado/perdido, serial ambiguo y caché antigua.
- Credenciales PPPoE fallidas; WiFi fallido con WAN listas; protección gestión.
- Cancelar en cada etapa, durante CLI/CWMP y con eventos tardíos; reintentar cada compensación.
- Reset físico entre intentos; ONU inaccesible durante cancelación.
- Error visible en clientes y Observability con misma operación; caída/reentrega de observabilidad sin secretos.
- Regresión scripts existentes sin modificación; tests de nuevos scripts por modelo.

Homologación real para ambos modelos: técnico resetea -> una sola alta -> dos WAN verificadas, Inform reciente, PPPoE y WiFi -> alta parcial/cancelación -> limpieza verificada -> nueva alta exitosa.

No declarar éxito por aceptación CLI, HTTP 202 ni caché antigua. Resolver mismatch VSOL y documentar firmware/perfiles probados.

## 13. Entrega y despliegue

1. Contratos, migraciones aditivas y executor v2 desactivado.
2. Tests unitarios/integración de etapas y compensaciones.
3. Nuevos scripts y manifiesto de hashes, con publicación controlada.
4. Clientes y Observability, pruebas de fallos visibles.
5. Smoke local conforme al runbook existente y staging laboratorio.
6. Homologación completa y carga con equipos simulados; piloto acotado real.
7. Habilitación gradual por entorno/OLT/modelo; producción continúa v1 hasta habilitación explícita.

Medir duración por etapa, cola SSH, sesiones, tiempo a Inform, tasa de éxito/reintento y cancelaciones incompletas. Definir objetivos de tiempo a partir del piloto, no prometer latencias sin evidencia.

Actualizar catálogo de comandos OLT y variables nuevas sin valores secretos. Entregar archivos cambiados, pruebas ejecutadas/resultados, firmware homologado, limitaciones y evidencia de compatibilidad v1. No cerrar implementación si faltan botones, visibilidad de errores o cancelación verificada.
