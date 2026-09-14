# Fix: GatewayOnuActivationClient ausente en Core staging (2026-09-04)

## Síntoma

Alta FIBER Espresso (`subscriptionId=2364`) terminó con:

```text
FIBER OLT Gateway client unavailable; SmartOLT fallback
```

`olt=COMPLETE` vía SmartOLT; `tr069=PENDING` hasta timeout e2e (5 min).

## Evidencia

- Props runtime: `olt.gateway.client-enabled=true` (application / staging / subsystem).
- `OnuFacadeController` sí respondía (`GET /onu/configured`, `/onu/catalog`) → `OltGatewayHttpClient` **sí** estaba registrado.
- `ObjectProvider<GatewayOnuActivationClient>.ifAvailable` era `null`.

## Causa

`GatewayOnuActivationClient` era `@Component` + `@ConditionalOnBean(OltGatewayHttpClient::class)`.
`@ConditionalOnBean` sobre un bean escaneado se evalúa antes de que el `@Bean` de `OltGatewayHttpClient` en `OltGatewayClientConfig` quede registrado → el componente se omite de forma permanente.

## Fix

- `GatewayOnuActivationClient` pasa a clase plain.
- Se registra como `@Bean` en `OltGatewayClientConfig` con el mismo `@ConditionalOnProperty(olt.gateway.client-enabled=true)` que el HTTP client.
- Test: `GatewayOnuActivationClientConfigTest`.

## Verificación (2026-09-04 ~17:02)

1. Redeploy staging `--with oltgateway,traffic,acs` OK.
2. Alta e2e `subscriptionId=2366` sn=`ZTEGDC47BFFD`:
   - Log: `FIBER OLT via Gateway activate` → `oltStatus=COMPLETE cpeStatus=PENDING`
   - Access: `POST /ispadmin-staging-oltgateway/api/olt-gateway/onu/activate` **200**
   - Access: `POST /ispadmin-staging-acs/api/acs/v1/cpe/provision` **200**
3. Espresso falló: timeout 300s en `tr069_provision_status_COMPLETE` (Core quedó en `WAITING_ACS` / `tr069=PENDING`).
4. Siguiente: cierre async ACS → Core (`CPE_PROVISIONING` / Redis) no está marcando `tr069=COMPLETE`.
