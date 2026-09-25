# Estado KVM4 — OMCI/TR-069 y corrección de arranque

Fecha: 2026-09-23  
Ámbito: KVM4 `2.24.66.53`, OLT Huawei MA5608T de laboratorio y ONU `HWTC9F4BE990`.  
Fuera de alcance: VPS legado `212.85.13.47`, sus credenciales, su Tomcat y ONUs de clientes.

## Configuración aplicada en KVM4

| Elemento | Estado | Evidencia/nota |
| --- | --- | --- |
| Usuario SSH OLT | Aplicado | KVM4 usa `gfbackend`, la cuenta dedicada. `oltadmin` permanece reservado al VPS legado. La contraseña solo está en el archivo gitignored del host. |
| Gateway OLT | Operativo | Healthcheck Gateway: OLT alcanzable tras cambiar el usuario dedicado. Las operaciones se hicieron por HTTP Gateway, sin SSH directo adicional a la OLT. |
| Permiso de escrituras OLT | Habilitado en KVM4 | `OLT_GATEWAY_WRITES_ENABLED=true` está requerido para autorizar ONUs y aplicar OMCI. No cambiar el valor por defecto global de producción (`false`): KVM4 debe declararlo explícitamente en su `.env`. |
| NetDiag en KVM4 | Deshabilitado temporalmente | Se dejó `NET_DIAG_ENABLED=false` como contención mientras existía el ciclo de arranque descrito abajo. Debe reactivarse únicamente después de desplegar y comprobar la corrección. |
| Tomcat | Operativo | Después de instalar el WAR y reiniciar, `GET /ispadmin/actuator/health` respondió `{"status":"UP"}`. |

## Corrección pendiente de desplegar: ciclo NetDiag → Gateway local

### Causa

`OltNetDiagTargetSyncService` implementaba `ApplicationRunner`. Al iniciar el proceso ejecutaba `sync()`; la implementación `NetDiagOltGatewayHttpClient` realiza HTTP al Gateway configurado en el mismo Tomcat (`olt.gateway.internal-base-url`).

Eso provoca un ciclo de inicio: Tomcat espera que termine el `ApplicationRunner`, mientras el runner llama por HTTP a Tomcat antes de que esté disponible para atender la petición.

### Cambio implementado localmente

Archivo: `core/src/main/kotlin/com/dscorp/wispadmin/netdiag/service/OltNetDiagTargetSyncService.kt`.

- Se retiró `ApplicationRunner`, `ApplicationArguments` y `@Order`.
- Se agregó `syncScheduled()` con `@Scheduled`.
- La primera ejecución queda diferida 60 s y se repite cada 10 min.
- `sync()` conserva su comportamiento transaccional e idempotente; solo cambia el momento en el que se invoca.

Configuración agregada en `core/src/main/resources/application-prod.properties`:

```properties
olt.gateway.sync.target-interval-ms=${OLT_GATEWAY_SYNC_TARGET_INTERVAL_MS:600000}
olt.gateway.sync.target-initial-delay-ms=${OLT_GATEWAY_SYNC_TARGET_INITIAL_DELAY_MS:60000}
```

El resultado esperado es que el healthcheck del backend alcance `UP` antes de que NetDiag haga la primera llamada al Gateway local.

## Operación OMCI realizada

ONU: `HWTC9F4BE990`  
Ubicación: GPON slot 1, puerto 6, ONT-ID 116.  
Perfil TR-069: 2.

La ONU fue autorizada y se detectó que no tenía host IP OMCI. La OLT devolvió el estado válido de fábrica:

```text
Failure: The ONT does not configure IP information
```

Se corrigió `OmciManagementV2` para reconocer únicamente ese texto como estado inicial vacío. Cualquier otro `Failure`, `% Unknown command` o error CLI sigue abortando la operación.

El Gateway aplicó y confirmó por lectura posterior:

```text
interface gpon 0/1
ont ipconfig 6 116 ip-index 0 dhcp vlan 1000 priority 2
ont tr069-server-config 6 116 profile-id 2
display ont info 6 116
display ont ipconfig 6 116
quit
```

Resultado del endpoint de gestión:

```json
{"configured":true,"address":"10.64.47.252"}
```

No se configuró WAN PPPoE ni Wi-Fi porque no se recibieron credenciales ni parámetros de una Subscription de laboratorio.

## Cambios relacionados ya presentes en el árbol de trabajo

| Área | Cambio |
| --- | --- |
| Perfil OMCI laboratorio | El perfil de línea `lab-acs` cambia de 12 a 30, que contiene gestión OMCI/TR-069 VLAN 1000 y el transporte esperado en laboratorio. |
| Reintentos v2 | `OLT_GATEWAY_V2_WRITE_MAX_RETRY_ATTEMPTS`, con valor por defecto 2, limita reintentos de efectos remotos v2 y no hereda los reintentos ilimitados del legado. |
| Persistencia Gateway/Core | Migración `V58__olt_provisioning_v2_onu_operation.sql` para que la propiedad durable de la ONU esté disponible en el schema Core que usa el flujo v2. |
| Diagnóstico OMCI | Las respuestas CLI rechazadas incluyen comando y salida saneada/acotada para trazabilidad; no contienen contraseñas. |

## Verificaciones realizadas

| Verificación | Resultado |
| --- | --- |
| Prueba focal OMCI y empaquetado del WAR anterior | Correcta: `:oltgateway:test --tests OmciManagementV2Test`, `:core:war`, `:core:tomcatLibs` y `git diff --check`. |
| Despliegue del WAR de OMCI a KVM4 | Correcto, mediante copia manual acotada del WAR. El script estándar se detuvo porque el árbol tenía cambios sin commit; no se hicieron commits forzados ni se tocaron cambios ajenos. |
| Healthcheck KVM4 posterior | Correcto: `UP`. |
| OMCI WAN gestión + perfil TR-069 | Correcto: `configured=true`, IP DHCP `10.64.47.252`. |
| Consulta GenieACS por serial | Sin dispositivo en la ventana observada. La ONU aún no envió un Inform visible o no alcanzó el ACS. |
| Build posterior al cambio de recursión | Pendiente. La ejecución fue interrumpida por el usuario antes de terminar; no debe marcarse como aprobada. |

## Pendientes para cerrar la prueba

1. Ejecutar y aprobar:

   ```bash
   ./gradlew :core:test --tests 'com.dscorp.wispadmin.netdiag.service.OltNetDiagTargetSyncServiceTest' :core:war :core:tomcatLibs -Pdjl.linux --console=plain
   ```

2. Desplegar ese WAR únicamente en KVM4 y reiniciar Tomcat.
3. Configurar en KVM4, antes del reinicio:

   ```properties
   NET_DIAG_ENABLED=true
   OLT_GATEWAY_WRITES_ENABLED=true
   ```

   No almacenar ni imprimir secretos al editar el `.env`.

4. Confirmar `UP` inmediatamente después del reinicio y comprobar que se mantiene `UP` más allá de 60 s, cuando corra `syncScheduled()`.
5. Revisar logs de KVM4: debe aparecer la sincronización NetDiag o una falla controlada del Gateway; no debe bloquear el arranque ni reiniciar Tomcat.
6. El Inform a KVM4 ya ocurrió con el perfil 20. Detalle y lo que sigue abierto: `kvm4-tr069-perfil-lab-hwtc-2026-09-23.md`.
7. Solo después de un Inform reciente y una Subscription de laboratorio autorizada: probar los scripts v2 de PPPoE y Wi-Fi. No crear ni borrar WAN de Internet sin esos datos.

## Criterios de aceptación

- KVM4 inicia y responde `UP` sin depender de una llamada HTTP al propio Tomcat durante el arranque.
- NetDiag puede estar habilitado sin degradar el arranque; su primera sincronización ocurre después de 60 s.
- Las escrituras OMCI v2 siguen disponibles en KVM4 con el usuario OLT `gfbackend`.
- La ONU conserva exactamente una WAN de gestión DHCP VLAN 1000. El perfil de laboratorio hacia KVM4 es el 20; el perfil 2 sigue en el ACS de producción.
- GenieACS de KVM4 muestra un Inform reciente antes de configurar PPPoE o Wi-Fi. El serial CWMP no es el serial GPON; ver el documento del perfil 20.
