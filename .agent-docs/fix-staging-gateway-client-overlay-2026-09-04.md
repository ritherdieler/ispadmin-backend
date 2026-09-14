# Fix staging: overlay Gateway client y security del WAR

Fecha: 2026-09-04.

El e2e Espresso de alta FIBER falló esperando `olt=COMPLETE`. La suscripción **2361** quedó `olt=PENDING`, MikroTik `COMPLETE`. Causa: el Core no llamó al Gateway.

## Qué falló

1. **Overlay ignorado.** `scripts/subsystems.sh` concatenaba `subsystem-enabled.properties` al final de `application-staging.properties`. Spring Boot (Config Data) conserva la **primera** clave del mismo documento, así que `olt.gateway.client-enabled=false` ganaba al `true` del overlay. `FiberInstallationStrategy` caía al SmartOLT cloud (`POST authorize_onu` → 400).
2. **Spring Security por defecto en el WAR Gateway.** Traffic y ACS tienen `permitAll` + CSRF off; el Gateway no. Health sin key → 401; POST activate sin key → 403 CSRF. Aunque el cliente HTTP se habilitara, Core (`X-Olt-Gateway-Key`) seguiría bloqueado por el filtro de seguridad por defecto.
3. **Timeout HTTP Core→Gateway.** `OltGatewayClientConfig` tenía read timeout 30 s. Autorizar ONU por SSH suele superar eso. Quedó en 180 s, alineado a `olt.gateway.command-timeout-ms`.
4. **Writes OLT en el WAR gateway staging.** `olt.gateway.writes.enabled` venía de prod (`OLT_GATEWAY_WRITES_ENABLED`, default `false`). Sin writes, `authorizeOnu` lanza `OltWritesDisabledException`. El perfil `oltgateway-staging-war` hornea `true`.

## Qué cambió

- Overlay se copia a `application-subsystem.properties` y el WAR staging activa el perfil `prod,staging,subsystem` (el último perfil pisa prod/staging). `spring.config.import` no gana a `application-prod.properties` en Boot 2.7.
- `OltGatewaySecurityConfig` `@Profile("oltgateway")`: CSRF off, stateless, `permitAll`. El gate real sigue siendo `X-Olt-Gateway-Key`.
- Core RestTemplate read timeout 180 s.
- Poll e2e: `GET /subscription` es 405; usar `find/dni`.

## E2E

Tras redeploy: cleanup de la 2361, repetir `e2e_register_fiber_staging_espresso.sh`.

Seguimiento 2026-09-04 (activate path): `.agent-docs/fix-staging-gateway-activate-path-2026-09-04.md` (writes disabled + client-enabled + ACS URL).
