# RouterOS client port (`com.dscorp.wispadmin.routeros`)

**Fecha:** 2026-07-26  
**Fase:** 0 (NetDiag)  
**Rama:** `feature/netdiag`

## Objetivo

Puerto de transporte desacoplado de `me.legrange` y de la entidad JPA `NetworkDevice`, para que `netdiag` y (más adelante) `wispadmin` hablen con MikroTik vía la misma abstracción.

```text
MikrotikClient.withSession(device) { session ->
  session.print("/system/identity")
}
```

## Componentes

| Pieza | Rol |
|-------|-----|
| `MikrotikClient` / `MikrotikSession` / `MikrotikDeviceRef` | Contrato sin fugas de librería |
| `MikrotikException` (sealed) | Taxonomía tipada: unreachable / auth / timeout / command |
| `LegrangeClassicAdapter` + `LegrangeClassicSession` | API TCP 8728 (`me.legrange`), pool por `device.id` |
| `RouterOs7RestAdapter` + `RouterOs7RestSession` | REST HTTPS (OkHttp + Basic auth), cliente HTTP compartido |
| `RouterOsRestPathMapper` | `/system/resource` → `/rest/system/resource/print` |
| `RouterOsClientProperties` / `RouterOsClientConfig` | Wiring Spring (`@ConditionalOnProperty`) |

Registro en scan: `WispAdminApplication` incluye `com.dscorp.wispadmin.routeros`.

## Properties (`application-dev.properties`)

```properties
router.os.client.adapter=classic
router.os.client.rest.port=443
router.os.client.rest.verify-ssl=true
router.os.client.rest.trust-store=classpath:routeros-mk-truststore.jks
router.os.client.rest.timeout-ms=10000
router.os.client.classic.port=8728
router.os.client.classic.timeout-ms=10000
```

- `adapter=classic` (default) → bean `LegrangeClassicAdapter`.
- `adapter=rest` → bean `RouterOs7RestAdapter`.
- Si `adapter=rest` y `verify-ssl=false`, `RouterOsClientConfig` emite **WARN** en arranque (solo lab).

## Truststore TLS (REST)

Los certificados autofirmados de MK1/MK2 **no** deben forzar `verify-ssl=false` en entornos reales. Procedimiento:

1. Exportar el certificado del router (navegador o `openssl s_client -connect HOST:443 -showcerts`).
2. Crear truststore JKS:

```bash
keytool -importcert \
  -alias mk1 \
  -file mk1.crt \
  -keystore src/main/resources/routeros-mk-truststore.jks \
  -storepass changeit \
  -noprompt
```

3. Ajustar `router.os.client.rest.trust-store-password` (env / secret manager; no hardcodear en prod).
4. Mantener `router.os.client.rest.verify-ssl=true`.

El archivo `routeros-mk-truststore.jks` **no se versiona** hasta que el equipo decida un truststore compartido de lab (sin claves privadas). Mientras tanto, REST en local puede usar un profile lab con `verify-ssl=false` (queda el WARN).

## Ciclo de vida

| Adapter | Sesión |
|---------|--------|
| REST | Sin estado persistente; reutiliza `OkHttpClient` (pool HTTP). |
| Classic | `ConcurrentHashMap<deviceId, ApiConnection>` + lock por dispositivo (mismo patrón que `MikroTikConnectionService`). |

## Tests

### Unitarios (CI / default)

```bash
./mvnw test -Dtest=MikrotikClientContractTest,LegrangeClassicAdapterUnitTest,RouterOs7RestAdapterUnitTest,RouterOsRestPathMapperTest,RouterOsClientConfigTest
```

`MikrotikClientContractTest` es abstracta (no se ejecuta sola). Surefire excluye el grupo `live-mk1` por defecto.

### Live MK1 (opt-in)

```bash
export ROUTEROS_MK1_HOST=38.224.231.2
export ROUTEROS_MK1_USER=...
export ROUTEROS_MK1_PASSWORD=...
# opcionales:
# export ROUTEROS_MK1_CLASSIC_PORT=8728
# export ROUTEROS_MK1_REST_PORT=443
# export ROUTEROS_MK1_VERIFY_SSL=false   # solo si aún no hay truststore

./mvnw test -Plive-mk1 -Dtest=LegrangeClassicAdapterTest,RouterOs7RestAdapterTest
```

Clases live: `@Tag("live-mk1")`. Perfil Maven `live-mk1` pone `groups=live-mk1` y limpia `excludedGroups`.

**Nunca** commitear secretos ni pegar passwords en esta doc.

## Validación Fase 0 (2026-07-26)

| Check | Resultado |
|-------|-----------|
| Unitarios (19 tests: path mapper, classic unit, REST unit, config) | **PASS** |
| Live classic 8728 desde workstation | **BLOCKED** — puertos 8728/443 no alcanzables desde la IP local (allowlist API MK; acceso esperado desde VPS `212.85.13.47` o red `192.168.0.0/16`) |
| Live REST 443 | **BLOCKED** — sin `ROUTEROS_MK1_USER` / `ROUTEROS_MK1_PASSWORD` en el entorno del agente; además `www` estaba `disabled=yes` en backup pre-upgrade MK1 y no hay evidencia de `www-ssl` habilitado |
| Credenciales | Solo vía env `ROUTEROS_MK1_*` |

### Bloqueadores REST MK1

1. Habilitar `www-ssl` en MK1 (y certificado) si se quiere validar REST.
2. Importar cert a `routeros-mk-truststore.jks` con `verify-ssl=true`.
3. Ejecutar `-Plive-mk1` desde host allowlisteado (VPS) con env vars.
4. Confirmar allowlist / firewall para el origen del test.

### Classic

Validar desde VPS con las mismas env vars cuando haya credenciales disponibles. El adapter y los contract tests live ya están listos.

## Follow-ups (fuera de Fase 0)

- Fase R1–R2: `MikroTikConnectionService` / interfaces wispadmin delegan en `MikrotikClient` (classic).
- No implementar netdiag ni backoffice en esta fase.
