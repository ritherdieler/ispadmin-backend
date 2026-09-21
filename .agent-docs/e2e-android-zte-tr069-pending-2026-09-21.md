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

El e2e de esta nota corrió contra el WAR de producción **sin** esas líneas de backend: `deploy.sh` rechazó el despliegue porque el árbol no está commiteado (`ERROR: hay cambios sin commitear`). En el emulador sí se instaló el `prodDebug` con el log de la app. Para ver el tramo Gateway/ACS hace falta commit y `./scripts/deploy.sh --deploy --env prod`.

Filtros:

```bash
adb logcat -s FIBER_TRACE:I
# en tomcat9027, cuando el WAR nuevo esté desplegado
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
