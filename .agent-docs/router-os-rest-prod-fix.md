# RouterOS REST en producción (registro de pago / MikroTik)

## Problema (2026-08-03)

`PUT /payment` fallaba con HTTP 500 tras ~20 s: `MikrotikTimeoutException` en `POST /rest/ip/firewall/address-list/print`. El TLS handshake colgaba porque el cliente REST apuntaba al puerto **8728** (API clásica) en lugar de **443** (`www-ssl`), cuando `ROUTER_OS_CLIENT_ADAPTER` no estaba definido y el default era `classic`.

Suscripción de ejemplo: **1686** → host **38.224.231.4** (MK2). El VPS **sí** alcanza MK2 en 443; el fallo era de puerto/protocolo, no de ausencia de ruta.

## Cambios en código

| Área | Cambio |
|------|--------|
| `RouterOs7RestAdapter.resolveRestPort` | Puerto 8728 (placeholder en `MikrotikDeviceRef`) se mapea siempre a `router.os.client.rest.port` (443). |
| `application-prod.properties` | Default `ROUTER_OS_CLIENT_ADAPTER` → **rest**. |
| `MikrotikPaymentReactivationHandler` | Reactivación post-pago en MikroTik: errores → `error_log` (módulo PAYMENT), no revientan el registro del pago. |
| `MikrotikService` | Delega reactivación al handler. |

## Variables VPS (`/opt/gigafiber/.env`)

| Variable | Entorno | Valor recomendado |
|----------|---------|-------------------|
| `ROUTER_OS_CLIENT_ADAPTER` | prod | `rest` |
| `ROUTER_OS_REST_TRUSTSTORE_PASSWORD` | prod | Password del JKS `routeros-mk-truststore.jks` embebido en el WAR |

Tras cambiar `.env` o desplegar WAR nuevo: reiniciar contenedor `tomcat9027`.

## Verificación

```bash
curl -sk -o /dev/null -w 'mk443:%{http_code} t=%{time_total}s\n' --max-time 5 https://38.224.231.4/
openssl s_client -connect 38.224.231.4:443 -servername 38.224.231.4 </dev/null 2>&1 | tail -2
```

Registrar un pago de prueba en Android; el PUT debe responder en &lt; 2 s con 200.

## Pruebas automatizadas

- `RouterOs7RestAdapterPortResolutionTest`
- `MikrotikPaymentReactivationHandlerTest`
- `ApplicationProdNetDiagPropertiesFileTest` (default adapter rest)

Construcción verificada: `./gradlew test --tests "com.dscorp.wispadmin.routeros.RouterOs7RestAdapterPortResolutionTest" --tests "com.dscorp.wispadmin.wispadmin.service.MikrotikPaymentReactivationHandlerTest" --tests "com.dscorp.wispadmin.netdiag.config.ApplicationProdNetDiagPropertiesFileTest"`
