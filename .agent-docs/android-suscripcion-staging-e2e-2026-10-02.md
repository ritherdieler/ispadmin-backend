# Registro Android de suscripción — staging — 2026-10-02

## Alcance y resultado

E2E ejecutado desde Android contra `ispadmin-staging`, Core en KVM4, OLT Gateway, MK2 id 8 y GenieACS. No se tocaron servicios ni datos de producción. Se usó la ONU de laboratorio `ZTEGDC47BFFD`, etiquetada `lab`.

| Corrida | Resultado | Evidencia |
|---|---|---|
| PPPoE | PASS — suscripción #115 | Android mostró éxito después de `DONE`; OLT, MK2, WAN, Wi-Fi, limpieza WAN y verificación terminaron `SUCCEEDED`; estados OLT, MikroTik y TR-069 `COMPLETE`. |
| STATIC_IP inicial | FAIL — corrida #116, corregida | La versión staging anterior rechazó STATIC_IP con `PPPOE_DYNAMIC_REQUIRED`. El WAR local ya aceptaba IP estática; se desplegó esa versión y se repitió la prueba. #116 se limpió. |
| STATIC_IP con soporte actualizado | FAIL — corrida #117, corregida | El checkpoint MK2 detectó conflicto porque RouterOS devolvió `200000000/200000000` y Core comparaba literalmente con `200M/200M`. La cola tenía la IP asignada a #117 y marcador de esa operación; se limpió #117. Se añadió comparación semántica K/M/G con regresión RED–GREEN. |
| STATIC_IP final | PASS — suscripción #118 | IP `192.168.250.10`; operación `DONE`; checkpoints OLT, ACS_CONTACT, VALIDATE, MIKROTIK, INTERNET, WIFI, WAN_CLEANUP y VERIFY `SUCCEEDED`. WAN_CLEANUP requirió 8 intentos. Android presentó el éxito después de `done=true`. |

Antes de la limpieza final de #118, el Gateway reportó la ONU `online`, Rx ONU `-23.66 dBm`, Tx `32.4 dBm`, Rx OLT `-29.21 dBm`, distancia `597 m`, VLAN 100 y perfil `GF_V100_M1000_TR069`. La consulta cacheada anterior decía `offline` con hora vieja; la consulta live fue `online`.

GenieACS identificó el dispositivo de laboratorio F6600R, recibió Inform y verificó WAN estática conectada con `192.168.250.10/24`, gateway `192.168.250.1`, DNS `8.8.8.8/8.8.4.4`, NAT y VLAN 100. Los dos SSID se verificaron. GenieACS no expuso los campos de passphrase en la caché consultada; por eso no se declara lectura independiente de las claves. El checkpoint Wi-Fi sí terminó `SUCCEEDED` y Android mostró las credenciales enviadas.

Service-health marcó `gpon` e `internet` como `UNKNOWN` por muestras OLT faltantes, aunque el endpoint live del Gateway respondió `online`; ACS figuró `FRESH`. No se generó tráfico de usuario desde un cliente Wi-Fi, por lo que la serie de tráfico en cero no es una prueba de throughput.

## Casos de borde

`PASS` indica que la prueba automatizada indicada pasó o que el escenario real quedó verificado. Las interrupciones deliberadas de equipos y servicios se omitieron según el plan.

| Área | Escenario | Estado | Evidencia |
|---|---|---|---|
| Android | Catálogo vacío o sin fallback offline | PASS | `GetRegistrationCatalogUseCaseTest`: catálogo vacío o sin `coreDevices` devuelve no disponible offline. |
| Android | ONU retirada del catálogo antes de autorizar; error al refrescar catálogo | PASS | `RegisterSubscriptionComposeViewModelTest`: selección retirada no se envía; evidencia de preautorización permite reanudar aunque falle el refresh. |
| Android | Datos de cliente/ubicación inválidos | PASS | Tests de validación del wizard y coordenadas cero impiden avanzar. |
| Android | Doble toque/reintento del envío y respuesta incierta | PASS | Tests de revisión/idempotencia; fallo de red tras submit reconcilia con el resultado del servidor. |
| Android | Cierre y reapertura con operación pendiente | PASS | Tests reabren una operación vinculada, reanudan polling y muestran resultado; timeout conserva estado de aprovisionamiento desconocido. |
| OLT/ONU | ONU ya autorizada, borrado fallido o sin autofind | PASS | `OnuActivationServiceTest` cubre no reautorizar ni iniciar ACS cuando la reversión o autofind falla. |
| OLT/ONU | Respuesta perdida, reintento y reinicio durante autorización | PASS | Tests de operación durable y executor reconcilian escrituras inciertas sin duplicar efectos y reanudan progreso persistido. |
| OLT/ONU | Serial alternativo, NAP/PON incorrecto o reserva ajena en equipo real | NO EJECUTADO | Se usó solo el serial y el NAP de laboratorio. La identidad durable y la cancelación se probaron en unit tests. |
| GenieACS | Inform tardío/ausente y contacto pendiente | PASS | Tests de espera ACS mantienen el checkpoint pendiente; el flujo real de #115 y #118 recibió Inform y completó. |
| GenieACS | Identidad ambigua | PASS | `OnboardingV2AcsContactServiceTest` no elige entre dos dispositivos que comparten sufijo de serial. |
| GenieACS | Modelo no admitido en un CPE real | NO EJECUTADO | El dispositivo real fue F6600R, modelo admitido. |
| GenieACS | Fault/rechazo de parámetros, sesión caída, verificación WAN y reintento | PASS | `OnboardingV2FaultTest` cubre SPV fault, sesión TR-069 caída, estado live y WAN estática perteneciente a la operación. |
| MikroTik | Secreto PPPoE ajeno y cola de IP ajena | PASS | Tests rechazan secretos/colas ajenas sin mutar el equipo; cancelación borra solo recursos propios. |
| MikroTik | Respuesta de escritura incierta, límites RouterOS normalizados y reintento | PASS | `MikrotikProvisioningStageHandlerTest` y `ProvisioningExecutorTest` verifican reconciliación y no duplicación. La regresión `200M` ↔ `200000000` quedó corregida. |
| MikroTik | MK2 inaccesible, perfil ausente o pool IP agotado en hardware | NO EJECUTADO | No se interrumpió el router ni se agotó el pool real. Los fallos transitorios se simularon en tests de retry policy. |
| Flujo completo | Revisión obsoleta, cancelación, limpieza parcial y reintento | PASS | Tests de control, transiciones, compensación y cleanup reintentable; no se interrumpieron equipos en staging. |
| Flujo completo | Suscripción no debe anunciarse antes de confirmación final | PASS | #115 y #118 terminaron `DONE`/`COMPLETE` antes del diálogo de éxito; el test Android conserva estado desconocido si vence el polling. |
| Flujo completo | Reinicio/reapertura y ausencia de duplicados | PASS | Tests de recuperación más dos altas reales secuenciales con limpieza entre ellas; #118 quedó ausente antes de purgar su serie de tráfico. |

## Defectos corregidos durante la prueba

1. El WAR staging no reflejaba la validación STATIC_IP ya presente en el checkout; se desplegó el WAR actualizado.
2. El verificador MK2 comparaba texto de velocidades; ahora reconoce equivalentes `K/M/G` y bits/s. La prueba reprodujo el fallo antes del cambio.
3. La respuesta de suscripción serializaba credenciales del `hostDevice`; los DTO de referencia conservan datos de identificación y omiten username/password. Test RED–GREEN y lectura post-deploy en staging: PASS.
4. El cleanup traffic inyectaba el `JdbcTemplate` primario de Core. La prueba H2 con dos bases reprodujo que borraba Core y dejaba traffic; ahora el purge usa `trafficDataSource`. El test de integración pasa.

## Limpieza y estado restante

- #11 y #115, #116, #117 y #118: los registros Core ya no existen. Los hard-cleanups de #115–#118 reportaron `COMPLETE` para MikroTik, ACS, OLT, Firebase, traffic y Core.
- El purge inicial había dejado 2 muestras de #115 y 7 de #118. Tras corregir y desplegar el datasource de traffic, se purgaron por id #115 y, para #118, por id más su IP estática asignada.
- Verificación posterior: latest vacío y serie con 0 puntos para #11 y #115–#118; no se encontró propietario activo de `192.168.250.10`.
- Core devuelve 404 para #118; la ONU no aparece como configurada y sí vuelve a aparecer en `unconfigured_onus`.
- GenieACS conserva el dispositivo compartido de laboratorio con tag `lab`; después de la limpieza hay 0 tasks y 0 faults.
- Las muestras históricas de tráfico previas a la corrección ya se purgaron. No quedan suscripción, cola de tráfico, tarea ACS ni fault de estas corridas.

## Verificación de código y despliegue

- Android: `:domain:test`, `:data:testDebugUnitTest`, `:presentation:testDevDebugUnitTest` y compilación de `presentation:compileDevDebugAndroidTestKotlin` pasaron durante la sesión.
- Backend: `./gradlew test` pasó después de los cambios; preflight staging confirmó la cadena FIBER/TR-069.
- El WAR final se desplegó en KVM4 `tomcat-staging`; Core respondió HTTP 200. Producción no se desplegó.
- El catálogo de consultas Gateway live/cacheadas usadas quedó actualizado en [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md).
