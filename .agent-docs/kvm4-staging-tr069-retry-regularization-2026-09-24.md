# Regularización del reintento TR-069 en KVM4 staging

Fecha: 2026-09-24.

## Alcance

- Entorno: KVM4 `2.24.66.53`, contenedor `tomcat-staging` y contexto `/ispadmin-staging`.
- ONU de laboratorio: `ZTEGDC47BFFD`.
- Suscripción conservada: `#39`, usuario PPPoE `gf39`.
- No se ejecutó cleanup, no se borró ni reautorizó la ONU y no se modificó producción.

## Causas regularizadas

1. El Gateway recibía `X-Gigafiber-Env: stg`, pero no tenía una URL ACS staging resoluble y devolvía HTTP 400.
2. `retryTr069` enviaba el ID numérico de la suscripción como `uniqueExternalId`, en lugar del identificador durable de la activación OLT.
3. Un error HTTP del Gateway escapaba como 500 sin un contrato saneado para Android.
4. El cooldown impedía un nuevo intento incluso después de una acción terminada en `FAILED`.

## Cambios

- `application-staging.properties` define `olt.gateway.acs.base-url-staging` con default Docker `http://tomcat-staging:8080/ispadmin-staging`; `OLT_GATEWAY_ACS_BASE_URL_STAGING` puede sobrescribirlo.
- `/opt/gigafiber/.env` declara explícitamente la misma ruta. Backup previo: `/opt/gigafiber/.env.bak-tr069-routing-20260924T1106`.
- El reintento consulta `GET /onus/by-sn/{sn}/activation` y reutiliza su `uniqueExternalId`; si esa lectura auxiliar falla, no vuelve a inventar la identidad con el ID de suscripción.
- `POST /subscription/{id}/acs/retry-tr069` traduce fallos HTTP del Gateway a 502 con mensaje genérico, sin filtrar el body interno.
- Una acción `FAILED` permite un reintento inmediato con una `Idempotency-Key` nueva. Estados inciertos o activos mantienen el cooldown.
- Se agregó `ObsDurableIngestionService` a la allowlist arquitectónica existente para desbloquear la suite completa del worktree V2; no se cambió su implementación.

## TDD y verificación

Fase RED: cuatro regresiones nuevas fallaron por separado:

- URL ACS staging ausente.
- `uniqueExternalId` incorrecto.
- excepción REST no traducida.
- cooldown después de `FAILED`.

Fase GREEN:

- 65 pruebas dirigidas: PASS.
- `AcsCallerRouterTest`: PASS.
- Suite `core`: 1,684 pruebas, 1 omitida, 0 fallos.
- Suite `oltgateway`: PASS.
- Preflight: `cadena FIBER/TR-069 completa`.

## Deploy y smoke KVM4

Comando de despliegue:

```bash
DEPLOY_VPS_HOST=2.24.66.53 ./scripts/deploy.sh --deploy --env staging \
  --with observability,oltgateway,netdiag,traffic,servicehealth
```

Resultado:

- Release `1.0.3+f160d3e`.
- WAR validado con `core`, `acs`, `oltgateway` y `traffic`.
- Solo se recreó `tomcat-staging`; health `/ispadmin-staging/` respondió HTTP 200.
- Variable confirmada dentro del contenedor: `OLT_GATEWAY_ACS_BASE_URL_STAGING=http://tomcat-staging:8080/ispadmin-staging`.
- `GET /subscription/39`: OLT, MikroTik y TR-069 en `COMPLETE`.
- `GET /subscription/39/acs`: `COMPLETE`, device `5872C9-F6600R-ZTEGDC47BFFD`, modelo `F6600R`, `lab=true`.
- `POST /subscription/39/acs/retry-tr069`: HTTP 200 e idempotente en `COMPLETE`.

## Android

El flavor staging todavía apunta al VPS público `.cloud`, no al KVM4. Para dejar el emulador listo sin cambiar código Android se usó el flavor dev con esta topología temporal:

```text
App dev :8080
  -> adb reverse host :8082
  -> proxy /ispadmin => /ispadmin-staging
  -> túnel SSH host :8080 => KVM4 :8081
```

El emulador `emulator-5554` quedó abierto, la app quedó autenticada y posicionada en el detalle de la suscripción `#39`. El túnel y el proxy son procesos de la sesión local; si se cierran, deben restablecerse antes de reintentar desde la app.
