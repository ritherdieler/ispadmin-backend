# RouterOS client port (`com.dscorp.wispadmin.routeros`)

**Fecha:** 2026-07-26  
**Fase:** 0 → R4–R5 (NetDiag)  
**Rama:** `feature/netdiag`

## Objetivo

Puerto de transporte desacoplado de `me.legrange` y de la entidad JPA `NetworkDevice`, para que `netdiag` y `wispadmin` hablen con MikroTik vía la misma abstracción.

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
| `RouterOs7RestAdapter` + `RouterOs7RestSession` | REST HTTPS (OkHttp + Basic auth); único transporte MikroTik (sin legrange ni fallback) |
| `RouterOsRestPathMapper` | `/system/resource` → `/rest/system/resource/print` |
| `RouterOsClientProperties` / `RouterOsClientConfig` | Bean `@Primary` `RouterOs7RestAdapter`; netdiag usa bean nombrado `netDiagMikrotikClient` (también REST) |

Registro en scan: `WispAdminApplication` incluye `com.dscorp.wispadmin.routeros`.

## Properties (`application-dev.properties`)

```properties
router.os.client.adapter=rest
router.os.client.rest.port=443
router.os.client.rest.verify-ssl=true
router.os.client.rest.trust-store=classpath:routeros-mk-truststore.jks
router.os.client.rest.timeout-ms=10000
router.os.client.classic.port=8728
router.os.client.classic.timeout-ms=10000
```

- `adapter=classic` (**default**, `matchIfMissing=true`) → bean `@Primary` `LegrangeClassicAdapter` (wispadmin).
- `adapter=rest` → bean `@Primary` `RouterOs7RestAdapter` (wispadmin).
- Si `adapter=rest` y `verify-ssl=false`, `RouterOsClientConfig` emite **WARN** en arranque (solo lab).
- `netdiag` **siempre** usa REST vía bean nombrado `netDiagMikrotikClient`, independiente de `router.os.client.adapter`.

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

El archivo `routeros-mk-truststore.jks` en `src/main/resources` contiene la CA lab `netdiag-ca` de MK1 (autofirmada). Password dev: `changeit` (`router.os.client.rest.trust-store-password`). Regenerar tras rotar CA:

```bash
export ROUTEROS_MK1_USER=... ROUTEROS_MK1_PASSWORD=...
scripts/mk1-export-truststore.sh
```

En prod usar secret manager para `ROUTEROS_TRUSTSTORE_PASSWORD`, no `changeit`.

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
# export ROUTEROS_MK1_SKIP_REST=true     # omitir RouterOs7RestAdapterTest si www-ssl no tiene cert

./mvnw test -Plive-mk1 -Dtest=LegrangeClassicAdapterTest,RouterOsRestClassicFallbackAdapterLiveTest,RouterOs7RestAdapterTest
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

## Fase R1–R2 — Refactor wispadmin (2026-07-26)

**Commit:** `refactor(wispadmin): route MikroTik access through MikrotikClient port`

### Cambios

| Pieza | Cambio |
|-------|--------|
| `MikrotikSession.execute(command)` | Comandos raw RouterOS (compatibilidad wispadmin cortes/queues) |
| `MikrotikClient.closeSession` / `isSessionActive` / `activeSessionDeviceIds` | Ciclo de vida expuesto (pool classic; no-op en REST) |
| `MikroTikConnectionService` | Delega en `MikrotikClient` (classic); mock local intacto |
| `NetworkDeviceConnection.executeCommand` | Abre `MikrotikSession` vía `MikrotikClientAccessor` (sin `ApiConnection.connect`) |
| `IMikroTikService` / `IQueueManager` | Parámetros `MikrotikSession` (sin fuga `me.legrange`) |
| `MikrotikDeviceRefMapper` | `NetworkDevice` → `MikrotikDeviceRef` |

`me.legrange` queda solo en `routeros/adapter/*` (+ tests unitarios del adapter).

REST: `execute(raw)` lanza `MikrotikCommandException` (wispadmin R1–R2 usa classic).

### Tests

```bash
./mvnw test -Dtest=MikroTikConnectionServiceTest,LegrangeClassicAdapterUnitTest,RouterOs7RestAdapterUnitTest,ServiceCutManagerServiceTest,FiberInstallationStrategyTest,NetworkDeviceControllerTest,NetworkDeviceConnectionControllerTest
```

### Follow-ups (Fase 1)

- Scaffold `netdiag` + polls/alerts; no reabrir ApiConnection en wispadmin.
- Fase R4–R5: migrar comandos raw → `print`/`add`/`set`/`remove` y `adapter=rest`.

## Fase R4 — Switch REST para wispadmin (2026-07-26)

**Commit:** `feat(routeros): enable REST adapter switch for wispadmin with classic default`

### Default seguro

| Consumidor | Adapter efectivo | Cómo |
|------------|------------------|------|
| wispadmin (`MikroTikConnectionService`, cortes/queues) | **classic** por defecto | `router.os.client.adapter` ausente o `classic`; bean `@Primary` |
| netdiag (`MikrotikPollAdapter`, etc.) | **REST siempre** | bean `netDiagMikrotikClient` = `RouterOs7RestAdapter` |

No hace falta cambiar código para apuntar wispadmin a REST: solo la property.

### Cómo habilitar REST en prod (tras validación live)

1. Completar checklist live de la sección siguiente (www-ssl, truststore, `-Plive-mk1`).
2. Migrar usos wispadmin de `MikrotikSession.execute(raw)` a `print` / `add` / `set` / `remove` / `call` (REST no soporta raw execute).
3. En el host/profile de prod:

```properties
router.os.client.adapter=rest
router.os.client.rest.port=443
router.os.client.rest.verify-ssl=true
router.os.client.rest.trust-store=classpath:routeros-mk-truststore.jks
router.os.client.rest.trust-store-password=${ROUTEROS_TRUSTSTORE_PASSWORD}
router.os.client.rest.timeout-ms=10000
```

4. Restart; wispadmin inyecta `RouterOs7RestAdapter` como `@Primary`. netdiag sigue en su bean REST propio.
5. Rollback inmediato: `router.os.client.adapter=rest` (o quitar la property).

**No** activar `adapter=rest` en prod mientras existan caminos classic-only (`session.execute`) o sin validación live MK1/MK2.

### Tests R4

```bash
./mvnw test -Dtest=RouterOsClientConfigTest,NetDiagMikrotikClientWiringTest,LegrangeClassicAdapterDeprecationTest,LegrangeDependencyBoundaryTest
```

- Wiring classic / missing / rest.
- netdiag REST aunque wispadmin sea classic.
- `@Primary` evita `NoUniqueBeanDefinition` con netdiag habilitado.
- Boundary: `import me.legrange` solo bajo `routeros/adapter/`.

### Deprecación

`LegrangeClassicAdapter` está `@Deprecated` con nota de migración a `adapter=rest`. La dependencia classic usa **`com.github.GideonLeGrange:mikrotik-java:f34e6c49`** (parche RouterOS 7.18+ `!empty`; upstream PR #90) hasta cumplir R5 y eliminar classic por completo.

### Classic API y RouterOS 7.18+

Desde RouterOS 7.18, una query API sin filas responde `!empty` en lugar de `!done` directo. `me.legrange:mikrotik:3.0.7` no lo interpreta y provoca **timeout** en comandos `print where ...` vacíos (p. ej. colas inexistentes, address-list vacía). El fork JitPack anterior incluye el fix de una línea en `ApiConnectionImpl$Processor`.

## Fase R5 — Checklist para eliminar `me.legrange:mikrotik`

No quitar la dependencia hasta cumplir **todos** los ítems:

| # | Criterio | Estado |
|---|----------|--------|
| 1 | `www-ssl` habilitado en MK1 (y MK2 si aplica) con certificado usable | Pendiente (bloqueado en workstation) |
| 2 | Truststore JKS importado; `verify-ssl=true` en el profile que valide REST | Pendiente |
| 3 | Contract tests live verdes: `./mvnw test -Plive-mk1 -Dtest=LegrangeClassicAdapterTest,RouterOs7RestAdapterTest` desde host allowlisteado (VPS) | Pendiente |
| 4 | Todos los caminos wispadmin usan `print`/`add`/`set`/`remove`/`call` (cero `MikrotikSession.execute` de producción) | **OK** (2026-08-01): dominio wispadmin migrado; `execute()` solo en adapter classic (`LegrangeClassicSession`) y REST sigue rechazando raw |
| 5 | Suite unitaria wispadmin mikrotik/cortes/queues verde con `router.os.client.adapter=rest` | **OK** unit (`MikroTikServiceRestTest`, `MikroTikConnectionServiceTest`, cortes/instalación); live MK1 pendiente |
| 6 | `LegrangeDependencyBoundaryTest` sigue verde (hoy: legrange solo en `routeros/adapter/`) | OK como gate parcial |
| 7 | Smoke prod/staging con `adapter=rest` estable (cortes, queues, system-info) | Pendiente |
| 8 | Entonces: borrar `LegrangeClassicAdapter` / session / factory / mapper legrange, quitar `me.legrange:mikrotik` del `pom.xml`, dejar solo `adapter=rest` (o default rest) | No iniciar hasta 1–7 |

Gate documental: si live REST no está validado, **no** force-delete legrange.

## Fase R5 — Migración wispadmin a REST nativo (2026-08-01)

Sin traductor CLI: cortes, colas, address-list, filter-rules, ip-pool, websockets de recursos y `NetworkDeviceConnectionService` usan `MikrotikSession.print` / `add` / `set` / `remove`.

| Módulo | API REST |
|--------|----------|
| `MikroTikService` | address-list, filter, queue simple |
| `QueueManagerService` | queue simple |
| `MikroTikConnectionService` | `printOnDevice`, `setOnDevice`; monitoreo WS por path |
| `MikrotikService` (pagos) | address-list remove |
| `PlanController`, `SubscriptionService`, instalación fiber/wireless | queue simple |
| `IpPoolService` | `/ip/address` |

`RouterOs7RestAdapter.resolvePort`: ref con puerto classic (8728) → HTTPS 443 cuando `adapter=rest`.

Tests:

```bash
./mvnw test -Dtest=MikroTikServiceRestTest,MikroTikConnectionServiceTest,RouterOs7RestAdapterUnitTest,FiberInstallationStrategyTest,ServiceCutManagerServiceTest,NetworkDeviceConnectionControllerTest
```

Activar REST en dev tras live MK1:

```properties
router.os.client.adapter=rest
mikrotik.connection.mock.enabled=false
```
