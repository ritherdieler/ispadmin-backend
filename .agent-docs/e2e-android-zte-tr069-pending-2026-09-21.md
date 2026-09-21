# E2E Android ZTE `ZTEGDC47BFFD` — TR-069 se queda en PENDING

Fecha: 2026-09-21. Alta desde Espresso prod (`FiberRegisterFirstOnuE2ETest`) contra `https://api.gigafiberperu.cloud/ispadmin`. ONU de laboratorio `ZTEGDC47BFFD`.

## Resultado

El test falló a los 300 s esperando `tr069_provision_status_COMPLETE`.

Suscripción **2381**, usuario PPPoE `gf2381`:

| Capa | Estado al cierre del test |
|------|---------------------------|
| App (logcat `FIBER_TRACE`) | `step=WAITING_ACS` `mk=COMPLETE` `olt=COMPLETE` `tr069=PENDING` mensaje `Esperando aprovisionamiento TR-069.` en cada poll, desde 15:40 hasta el timeout |
| `ispadmin.subscription` | igual: TR-069 `PENDING`, `tr069_device_id=5872C9-F6600R-ZTEGDC47BFFD`, `tr069_last_error` vacío |
| `ispadmin.olt_activation_operation` | `stage=ACS_STATUS` `attempts=3` `cpeStatus=PENDING` `deviceId` ya lleno `event_published=0` |
| `prod_acs.cpe_record` | `COMPLETE` a las 15:51:59, mensaje `ONU configurada automáticamente por TR-069.` |

MikroTik y OLT terminan. La app no se entera cuando el ACS ya marcó la ONU.

## Por qué la app no avanza

1. El Gateway lanza el aprovisionamiento ACS y, tras 3 intentos, deja el journal en `ACS_STATUS` / `PENDING`.
2. `OnuActivationService.statusBySn` devuelve ese journal guardado en cuanto `deviceId` no está vacío. No vuelve a leer el estado del ACS.
3. Cada `GET /subscription/{id}/registration-progress` copia ese `PENDING` a la suscripción (`SubscriptionProvisionService.pullTr069FromGateway`).
4. Con `tr069LastError` vacío, el DTO dice `Esperando aprovisionamiento TR-069.` y el mapper lo pinta como `WAITING_ACS`.

El ACS puede pasar a `COMPLETE` minutos después. La suscripción sigue en `PENDING` porque el poll no consulta `cpe_record`.

## Reintento e2e (WAR `1.0.3+dcc919f`) — suscripción **2382**

Misma ONU, WiFi `mimiwifi` / `MimiWifi24pass`. MK y OLT `COMPLETE`. App y Core: `WAITING_ACS` / `tr069=PENDING` sin `tr069_last_error`. Journal `ACS_STATUS` `attempt_count=3` `cpeStatus=PENDING` `deviceId` lleno. `prod_acs.cpe_record` `COMPLETE` (~16:37–16:39).

### Causa del fallo ACS (confirmada en `FIBER_TRACE`)

```text
FIBER_TRACE gateway event=acs-start … attempts=3 …
FIBER_TRACE gateway event=acs-unconfirmed …
  type=ResponseStatusException
  error=400 BAD_REQUEST "ACS caller is unresolved"
```

1. `olt.gateway.acs.require-caller=true` (`application-prod.properties`).
2. `HttpAcsCpeClient` resuelve la URL ACS con `AcsCallerRouter.baseUrl(GatewayCallContext.env())`.
3. `GatewayCallContext` es `ThreadLocal` y solo lo llena `OltGatewayApiKeyFilter` con `X-Gigafiber-Env` en el hilo HTTP.
4. `OnuActivationService.runAcs` corre en `acsExecutor` (pool). Ese hilo **no** hereda el `ThreadLocal` → `env=null` → 400 `ACS caller is unresolved` **antes** de llamar a GenieACS.
5. El `catch` deja `stage=ACS_STATUS` sin mensaje. Tras conocer `deviceId`, cada poll hace `status-stored` y no relee el ACS.

También falla `OnuActivationController.telemetry` con el mismo 400 cuando el contexto de caller no está en el hilo.

### Qué hay que corregir

1. Propagar `X-Gigafiber-Env` / `GatewayCallContext` al `acsExecutor` (o fijar env `prod` en el WAR único / `require-caller=false` con URL local).
2. En `statusBySn`, si el journal está `PENDING`, releer ACS y promover `COMPLETE`.

### Qué se corrigió

`runAcs` corre en `acsExecutor`. Ese hilo ahora recibe `GatewayCallContext` del hilo que activó (o `olt.gateway.acs.default-caller`: `prod` / `stg`) antes de llamar al ACS. El recuperador ya no vuelve a encolar `provision`: un solo intento en el alta; el siguiente es el reintento manual (`POST /subscription/{id}/acs/retry-tr069`).

## Logs `FIBER_TRACE`

Prefijo único, sin contraseñas ni cuerpos PPPoE/WiFi.

| Dónde | Qué imprime |
|-------|-------------|
| App `PollRegistrationProgressUseCase` | cada poll: id, step, done, mk, olt, tr069, message |
| App `RetryTr069ProvisioningUseCase` | start / ok / fail del reintento |
| Core `SubscriptionProvisionService` | refresh y retry: id, sn, estado anterior y nuevo, deviceId, mensaje recortado |
| Gateway `OnuActivationService` | activate, status almacenado, ACS pending, intentos agotados, start/result/unconfirmed, provision-cpe |
| ACS `CpeFacadeService` | resultado de `provision` |
| ACS `NamedCpeProvisioner` | HTTP del enqueue PPPoE, IP al completar, IP/SSID si vence la espera |
| ACS `VparamProvisioner` | HTTP del SPV (sin el JSON de credenciales) |

Desplegado en prod como `1.0.3+dcc919f` (2026-09-21). El segundo e2e ya vio estos logs en `tomcat9027`.

Filtros:

```bash
adb logcat -s FIBER_TRACE:I
docker logs tomcat9027 2>&1 | grep FIBER_TRACE
```

## Cómo se corrió

```bash
# repo Android
./scripts/e2e_register_fiber_espresso.sh \
  --onu-sn ZTEGDC47BFFD \
  --cleanup-mode skip \
  --wifi-ssid mimiwifi \
  --wifi-pass MimiWifi24pass
```

WiFi enviado en el alta (la misma clave en las dos bandas):

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `mimiwifi` | `MimiWifi24pass` |
| 5 GHz | `mimiwifi - 5G` | `MimiWifi24pass` |

La suscripción 2381 quedó en prod (`--cleanup-mode skip`).

## Limpieza previa

El primer intento no llegó a Espresso: `tr069-e2e-hard-cleanup.sh` en prod borra la ONU por SmartOLT y esa API respondió `403 Invalid API key`. La ONU seguía autorizada (`gigafiber-ma5608t_1_6_117`) y `/onu/unconfigured_onus` venía vacío. Se borró por el Gateway (`POST /api/olt-gateway/onu/delete/gigafiber-ma5608t_1_6_117` → 200) y el segundo intento sí vio el autofind.

El cleanup también se negó a borrar la suscripción 2380 (`SERGIO TEST`) sin `--force`. Esa fila se limpió con `--force` antes del segundo alta.
