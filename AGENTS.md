# WispAdmin backend — instrucciones para agentes

**Plataforma:** aplican también las reglas de `gigafiber/AGENTS.md` (TDD, secretos, español).

Este repo es Spring Boot/Kotlin (WAR), no Android.

Workflows opcionales: `.cursor/skills/` (feature, TDD, endpoint, review) y `Skills/kotlin-backend/SKILL.md` (notas HTTPS/mapas, acotado).

No apliques skills de Jetpack Compose / R8 / Navigation 3 al backend. Esos docs en `Skills/` y `.skills/mobile-best-practices` son referencia para la app **IpsAdmin**, no para este código.

## Pruebas: camino más corto (obligatorio)

Antes de **deploy staging/prod** o de un e2e largo, elige el camino **más corto** que demuestre el comportamiento. Deploy no es el primer escalón de verificación.

### Orden por defecto

1. **Unit / TDD** del servicio o parser tocado.
2. **Smoke local** si la dependencia es alcanzable desde la Mac (p. ej. OLT `10.11.104.2` por SSH/VPN): live test `@Tag("live")`, script o el mismo stack CLI/HTTP en local.
3. **Staging / e2e** solo cuando el bug o la feature **depende** del WAR desplegado, overlay, Redis en VPS, Tomcat multi-WAR, o del flujo Android↔Core completo.

### Anti-patrón

Desplegar staging “para probar” un cambio que solo habla con la OLT (authorize/delete/CLI) cuando desde local ya se puede llamar al mismo `OltGatewayCommandService` / SSH. Eso **pierde tiempo** (build + upload + restart) sin ganar señal.

Ejemplo correcto: `OLT_WRITE_LIVE=true ./mvnw -Dtest=OltGatewayDeleteLiveSmokeTest test` antes de cualquier `deploy.sh --env staging`.

Detalle y ejemplos: `.agent-docs/pruebas-camino-mas-corto.md`.

### Pruebas locales Gateway / ACS / MK2 / ONU lab (obligatorio)

Cuando el usuario pida **pruebas en local** (alta FIBER, authorize/activate/delete, TR-069): seguir el runbook numerado de `.agent-docs/pruebas-local-gateway-acs-lab.md`. No improvisar otro stack.

1. **WARs locales** = Core `:8082` + Gateway `:8080`. Cliente → **solo Core**.
2. **ACS = VPS** (WAR staging `ispadmin-staging-acs` por túnel `:8091`). **No** levantar `AcsApplication` local.
3. **GenieACS = VPS** (túnel NBI `7557`).
4. **MikroTik = MK2** (`network_device` id **8**, VLAN 100). No MK1 ni `mikrotik_test`.
5. **Solo ONUs con tag GenieACS `lab`**. Canónica: `ZTEGDC47BFFD`.
6. Usuario e2e local: `scripts/local-e2e-ensure-catalog.sh` (`dscorp` / `nohacker`, ADMIN, `verified=1`, hash compatible con SHA-384 de la app). Place `9 de octubre`, NAP `NO-001`, plan FIBER, MK2 id 8. Core: `olt.service.mock.enabled=false` (si no, `/onu/unconfigured_onus` devuelve `ALCL*`).
7. Alta: `POST /subscription` en el Core (VLAN 100, NAP 42, `hostDeviceId` 8, WiFi passphrase **≥ 8**). Poll `registration-progress` hasta `tr069ProvisionStatus=COMPLETE` y entregar SSID **y** password.

Regla Cursor: `gigafiber/.cursor/rules/olt-lab-acs-vps-local.mdc`.

### Pruebas largas: notificación de fin (obligatorio)

Smoke VPS, live OLT, cleanup duro, suites Maven largas o cualquier script >~1–2 min: lanzar en background, **cerrar el turno** y continuar solo cuando Cursor notifique que el job terminó. No bloquear con `AwaitShell`/polling. Regla de plataforma: `gigafiber/AGENTS.md` → «Pruebas largas: no bloquear el turno».

## Desacople de subsistemas (obligatorio)

El **core** y todos los subsistemas (OLT Gateway, NetDiag, traffic, service-health, observability, CRM WhatsApp, …) deben permanecer **lo más desacoplados posible**.

Todos los **clientes externos** (backoffice, app Android, scripts operativos, integraciones cliente de UI) deben hablar **solo con el core**. El core es la **fachada pública** y el **orquestador** de todos los subsistemas.

Entre core ↔ subsistemas y entre subsistemas se conectan por **REST/HTTP JSON**, **WebSocket** (incl. STOMP donde ya exista) o **Redis Streams interno** (`gigafiber.events`, no expuesto a clientes). Detalle: `.agent-docs/subsistemas-desacople-transporte.md`.

No exponer a clientes externos endpoints, sockets o contratos de un subsistema por conveniencia. Si un cliente necesita datos o acciones de un subsistema, debe consumirlos a través del core.

**JDBC cruzado (prohibido en runtime):** cada WAR habla solo con **su** schema MySQL (`ispadmin*` Core, `stg_acs`/`prod_acs` ACS, `stg_oltgateway`/`prod_oltgateway` Gateway, `stg_traffic`/`prod_traffic` Traffic). Prohibido `schema.tabla` de otro WAR, `` `$catalog`.tabla ``, `acs.profiles.catalog` o un segundo datasource hacia el schema de un hermano. Entre WARs: HTTP/WS/Redis Streams. Copias one-shot viven en `scripts/sql/` (no en el arranque de la app). Test: `CrossSchemaJdbcForbiddenTest`. Detalle: `.agent-docs/subsistemas-desacople-transporte.md`.

Prohibido reacoplar por JDBC cruzado, inyección de facades/repos de otro paquete/WAR o imports de dominio ajeno. Detalle y vocabulario (core ≠ CRM): `.agent-docs/subsistemas-desacople-transporte.md`.

## Documentación obligatoria de comandos OLT

Cada vez que **ejecutes, implementes, pruebes o depures** un comando **nuevo** hacia la OLT (SSH CLI) o un endpoint del OLT Gateway (HTTP), documéntalo **en el mismo turno**, antes de cerrar la tarea.

### Dónde

`.agent-docs/olt-gateway-comandos-catalogo.md`

### Qué registrar (mínimo)

| Campo | Contenido |
|-------|-----------|
| **Comando** | Literal exacto (placeholders `{slot}`, `{port}`, `{sn}`, `{ontId}` si aplica) |
| **Descripción** | Una frase: qué hace y qué devuelve |
| **Usado en** | Clase, servicio o endpoint que lo invoca |
| **Notas** | Opcional: timeout, fallback, errores conocidos, tiempos medidos |

Ejemplo:

```markdown
| `display ont optical-info {port} all` | Tabla Rx/Tx/OLT Rx de todas las ONUs de un puerto GPON | `OltSignalPollService` | Solo dentro de `interface gpon 0/{slot}`; ~3–15 s/puerto |
```

### Cuándo documentar

- Comando **nuevo** en código o en prueba live (funciona, falla o requiere variante).
- Cambio de estrategia (p. ej. slot-all → port-all).
- Comando usado en diagnóstico que aún no esté en el catálogo.

### Si ya existe

No duplicar: actualizar la fila si cambió comportamiento, tiempos o notas. Detalle largo → enlace a `.agent-docs/olt-ma5608t-gpon-guide.md`.

### Criterio de terminado

Tarea con comandos CLI/API nuevos o modificados **no cerrada** si el catálogo no los refleja.

## Catálogo obligatorio de keys y secretos

**Ámbito:** al tocar `application*.properties`, `.env*`, `deploy.config*` o `.agent-docs/vps-secrets-management.md`.

Cada vez que **introduzcas o cambies** una variable de entorno, API key, token o secreto (backend, deploy, frontends `VITE_*`, VPS), actualiza el catálogo **en el mismo turno**, antes de cerrar la tarea.

### Qué documentar

| Campo | Contenido |
|-------|-----------|
| **Nombre** | Variable exacta (`CRM_SECRETS_MASTER_KEY`, `VITE_OBS_API_KEY`, …) |
| **Entorno** | `prod` (VPS), `dev` (local), `build` (embebido en bundle), o varios |
| **Dónde vive el valor** | p. ej. `/opt/gigafiber/.env`, `application-local.properties`, `.env.production` |
| **Enlaza propiedad** | Clave en `application-*.properties` si aplica |
| **Rotación / pareja** | p. ej. debe coincidir con `NET_DIAG_API_KEY` ↔ `VITE_NETDIAG_API_KEY` |

### Qué NO documentar en git

- **Valores reales** de secretos (tokens, passwords, master keys).
- Copias de `.env` de prod ni dumps de `grep` con `=valor`.

Placeholders permitidos: vacío, `openssl rand …`, `dev-*-key`, comentario “mismo que backend”.

### Dónde registrar (fuente de verdad)

1. **Backend / VPS / parejas cross-app:** `.agent-docs/vps-secrets-management.md` — tabla por grupo; añadir fila o variable nueva.
2. **Plantillas locales sin valor:** `scripts/deploy.config.example` (deploy Mac), comentarios en `application-dev.properties` solo si es el binding `${ENV}`.
3. **Frontends:** `.env.example` del repo + la sección **Frontends** en `vps-secrets-management.md`.
4. **Feature con muchas claves:** enlace desde el doc de feature (p. ej. CRM fase 4) al catálogo; no duplicar tablas largas.

### Cuándo actualizar

- Nueva `${VAR}` en `application-prod.properties` o `application-dev.properties`.
- Nuevo `VITE_*` consumido en código.
- Secreto nuevo en VPS o en `deploy.config.local` (solo documentar el **nombre** en example).
- Rotación: actualizar notas de rotación en el catálogo, no el valor.

### Criterio de terminado

Tarea con secretos nuevos o renombrados **no cerrada** si el nombre no aparece en el catálogo y en la plantilla `.example` correspondiente.

## E2E TR-069 — entregar WiFi al cerrar

Si un e2e de alta FIBER/TR-069 termina en **`tr069ProvisionStatus=COMPLETE`** (GPV ACS de IP de pool + SSIDs), en el **mismo mensaje de cierre** y **antes** de la limpieza dura, entregar al usuario las credenciales WiFi del `POST /subscription`:

| Banda | Campo | Qué mostrar |
|-------|--------|-------------|
| 2.4 GHz | `wifiSsid24` / `wifiPassword24` | SSID **y** contraseña |
| 5 GHz | `wifiSsid5` / `wifiPassword5` | SSID **y** contraseña |

No dar la prueba por cerrada si solo se mencionan los SSID. Usar los valores reales del alta, no un placeholder.

Runbook: `.agent-docs/tr069-e2e-validacion-modelo.md` (sección «Entregar WiFi al usuario»).
