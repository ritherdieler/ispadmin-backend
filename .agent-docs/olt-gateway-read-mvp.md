# OLT Gateway Read-Only MVP

Módulo `com.dscorp.wispadmin.oltgateway` que expone una API REST sobre la OLT Huawei MA5608T vía SSH CLI.

> **Actualización 3 capas:** el gateway ya incluye persistencia `olt_mgr_*`, writes CLI (flag) y aliases SmartOLT para las 6 ops de WispAdmin. Ver [olt-gateway-3layer.md](./olt-gateway-3layer.md).

Guía CLI de referencia: [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md).

Modelo de datos ONU según SmartOLT (UI + API): [smartolt-onu-data-model.md](./smartolt-onu-data-model.md).

Propuesta de DB propia para clonar SmartOLT: [olt-manager-db-model.md](./olt-manager-db-model.md) (prefijo MySQL `olt_mgr_*`, ownership gateway).

Ingeniería inversa SmartOLT (cadencias, endpoints, flujos): [smartolt-reverse-engineering.md](./smartolt-reverse-engineering.md).

Pack RE → diseño IspManager (sitemap, contratos API, fichas, gap matrix): [ispmanager-re/00-index.md](./ispmanager-re/00-index.md).

## Endpoints

Base: `/ispadmin/api/olt-gateway`

| Método | Ruta | Auth | Respuesta |
|--------|------|------|-----------|
| GET | `/health` | pública | `{ status, oltReachable, latencyMs }` |
| GET | `/olt/info` | API key | DTO nativo (versión + boards) |
| GET | `/onus/autofind` | API key | `SmartOltUnconfiguredOnusResponseDto` |
| GET | `/onus/by-sn/{sn}` | API key | `SmartOltOnuBySnResponseDto` |
| GET | `/onus` | API key | resumen nativo slots 0/1 |
| GET | `/onus/{slot}/{port}/{ontId}` | API key | detalle nativo |
| GET | `/onus/{slot}/{port}/{ontId}/optical` | API key | RX/TX/oltRx/temp/voltaje (`OpticalInfoDto.oltRxPowerDbm`) |

Parser óptico bulk + categorías de señal: [olt-gateway-optical-signal-parser.md](./olt-gateway-optical-signal-parser.md).
| GET | `/onu/unconfigured_onus` | API key | alias SmartOLT (6 ops) |
| GET | `/onu/get_onus_details_by_sn/{sn}` | API key | alias SmartOLT |
| POST | `/onu/authorize_onu` | API key | alias SmartOLT (requiere writes) |
| POST | `/onu/move/{sn}` | API key | alias SmartOLT (requiere writes) |
| POST | `/onu/delete/{externalId}` | API key | alias SmartOLT (requiere writes) |
| POST | `/onu/reboot/{externalId}` | API key | alias SmartOLT (requiere writes) |

Header: `X-Olt-Gateway-Key: <key>` (también acepta `X-Token`)

Errores: 401 key inválida, 404 ONU no encontrada, 502 OLT inalcanzable, 504 timeout CLI.

## Swagger

Documentación OpenAPI del gateway (y de lo que se vaya anotando):

- UI: http://localhost:8080/ispadmin/swagger-ui.html
- JSON: http://localhost:8080/ispadmin/v3/api-docs
- Grupo gateway: http://localhost:8080/ispadmin/v3/api-docs/olt-gateway

Detalle: [swagger-openapi.md](./swagger-openapi.md).

### CLI by-sn (importante)

Comando real hacia la OLT:

```text
display ont info by-sn <sn>
```

- SN válido: 12–16 caracteres (`XXXXXXXXXXXX`, 14/16 chars, o `XXXX-XXXXXXXX`).
- **No** añadir `all` al final — el CLI responde `% Too many parameters`.
- Ejemplo de prueba live: `ZTEGDC47DF15`.

### CLI listado `/onus` (importante)

En MA5608T V800R015 **no existe** `display ont info summary`. El inventory live usa estrategia escalonada por slot:

```text
display ont info 0 <slot> all          # rápido en slots pequeños (slot 0)
display ont info 0 <slot> <port> all   # fallback por puerto si slot-all timeout
```

`display ont info 0 all` (frame completo) puede tardar >180 s en esta OLT y ya no se usa como único comando.

Propiedad: `olt.gateway.inventory.slot-all-probe-timeout-ms` (default 15000). Slots que caen a port-all se recuerdan en memoria del reader.

El parser (`OnuSummaryParser`) une la tabla SN con **Descriptions** del mismo output (`description` por item) y acepta slots mezclados. Ignora líneas que no son filas SN/description.

La paginación CLI (`---- More ----`) se evita con `mmi-mode enable` al preparar la sesión (verificado en MA5608T). Si aún aparece More, se avanza con espacio.

## Variables de entorno

| Variable | Uso |
|----------|-----|
| `OLT_GATEWAY_API_KEY` | API key del filtro |
| `OLT_GATEWAY_HOST` | IP OLT (default `10.11.104.2`) |
| `OLT_GATEWAY_PORT` | Puerto SSH (default `22`) |
| `OLT_GATEWAY_USER` | Usuario CLI (default `oltadmin`) |
| `OLT_GATEWAY_PASSWORD` | Password SSH (`GigaOlt2026` para `oltadmin`; no hardcodear en prod) |
| `OLT_GATEWAY_OLT_ID` | ID lógico para SmartOLT compat |
| `OLT_GATEWAY_MOCK_ENABLED` | `true` = sin SSH (fixtures); `false` = OLT real. `OLT_GATEWAY_ENABLED` se eliminó. |

Secretos solo vía env; nunca hardcodear password/api-key en commits.

Usuario OLT documentado: [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md) — sección **Usuario gateway (`oltadmin`)**.

## Profiles

**Dev** (`application-dev.properties`):

```properties
olt.gateway.mock.enabled=${OLT_GATEWAY_MOCK_ENABLED:false}
olt.gateway.username=${OLT_GATEWAY_USER:oltadmin}
olt.gateway.api-key=${OLT_GATEWAY_API_KEY:dev-olt-gateway-key}
```

**Prod**: el módulo arranca con el WAR. El SSH real exige ruta LAN/VPN a la OLT y `olt.gateway.mock.enabled=false`.

## Cómo probar con Postman

1. Arrancar backend con profile `dev`.
2. Importar [`postman/olt-gateway-read-mvp.json`](../postman/olt-gateway-read-mvp.json).
3. Variables: `baseUrl=http://localhost:8080/ispadmin`, `apiKey=dev-olt-gateway-key`.
4. Ejecutar `Health` (sin key) y `ONUs Autofind` (con key).

Para OLT real en LAN:

```bash
export OLT_GATEWAY_USER=oltadmin
export OLT_GATEWAY_PASSWORD='GigaOlt2026'
export OLT_GATEWAY_MOCK_ENABLED=false
./run-dev.sh   # o mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Arquitectura

```
controller → OltGatewayQueryFacade (real|mock)
                ↓
         parsers + SmartOltCompatMapper
                ↓
         HuaweiCliSession → OltSshClient (MINA sshd, algos legacy)
```

- Paquete aislado: cero imports de entidades `NapBox`/`Onu` de dominio.
- SmartOLT solo en `mapper/SmartOltCompatMapper`.
- `RealOltService` **no** integrado aún (MVP+1).

### Sesión SSH viva (uptime del backend)

Una sola sesión CLI Huawei permanece abierta mientras Spring está up. Cierre intencional solo en shutdown (`destroyMethod=close`).

| Aspecto | Comportamiento |
|---------|----------------|
| Lifetime | Uptime del proceso; sin cierre por idle de aplicación |
| Prepare al conectar | `enable` → `config` → `mmi-mode enable` → `quit` (sin paginación) |
| Keepalive CLI | Cada `keepalive-interval-ms` ejecuta `keepalive-command` (`display clock`) |
| Caída OLT/red | El keepalive invalida y reconecta + mismo prepare |
| `OltSshClient` | Un `SshClient` reutilizable; `close(session)` no hace `client.stop()` |
| Idle MINA | `ssh-idle-timeout-minutes=0` → sin idle close del cliente SSH |
| Health | `ping()` usa el mismo comando corto + `health-timeout-ms` (no `display version` ni 45s) |
| Latencia | Fría ~5–6 s (primer connect / post-caída); warm ~1 s en requests siguientes |

#### Paginación CLI (`---- More ----`) y `mmi-mode`

En MA5608T **no** usar `screen-length 0 temporary` (no aplica / no existe en esta familia SmartAX).

Al (re)abrir la sesión, `HuaweiCliSession.prepareSession()` ejecuta:

```text
enable
config
mmi-mode enable
quit
```

Efecto: la OLT pasa a *machine-machine interaction mode* y los `display ...` largos (p. ej. `display ont info 0 1 all`) salen **de una sola vez**, sin `---- More ----` ni enviar espacios.

| Evidencia | Detalle |
|-----------|---------|
| Fuente | [Disabling paging in Huawei](https://networkengineering.stackexchange.com/questions/42438/disabling-paging-in-huawei) (respuesta MA5608T) |
| Validación live | Tras `mmi-mode enable`, `display ont info 0 1 all` ~100 KB sin More |
| API | `GET /onus` ~758 ONUs: slot-all slot 0 + port-all slot 1. Primera ~85 s; cache port-all ~25 s |

Fallback: si aún aparece More, `HuaweiCliPromptDetector` + envío de espacio en la cola del buffer.

Código: `HuaweiCliSession.prepareSession()`, `HuaweiCliPromptDetector`. Guía OLT: [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md) — **Preparación de sesión**.

Props de sesión (`olt.gateway.session.*`):

| Propiedad | Default | Rol |
|-----------|---------|-----|
| `keepalive-enabled` | `true` | Activa scheduler de keepalive |
| `keepalive-interval-ms` | `60000` | Intervalo keepalive CLI del bus (idle); no activa heartbeat MINA |
| `keepalive-command` | `display clock` | Ping ligero (health + keepalive) |
| `health-timeout-ms` | `5000` | Timeout de health/keepalive (separado de `command-timeout-ms`) |
| `ssh-idle-timeout-minutes` | `0` | `0` = sin cierre idle del cliente MINA |
| `pool-size` | `4` | Pool SSH (MA5608T: igual a Reenter max del user exclusivo) |

Inventory paralelo: ver [olt-gateway-3layer.md](./olt-gateway-3layer.md) — **Capacidad CLI y paralelismo de inventory**.

Eager connect al `start()` del bean: la sesión existe desde el boot, no solo en la primera API.

## Campos SmartOLT mapeados (MVP)

**Autofind → `Response`:** `sn`, `board` (slot), `port`, `pon_type=gpon`, `olt_id`, `onu_type_name` (equipment).

**By SN → `Onu`:** `sn`, `board`, `port`, `onu`, `name`, `administrative_status`, `custom_template_name` (line profile), `unique_external_id={oltId}_{board}_{port}_{onu}`.

Parser by-sn soporta dos formatos CLI Huawei:
1. Tabla resumen (`F/S/P ONT-ID Description` + fila `0/1/0 5 ...`).
2. Detalle live (`F/S/P : 0/1/7`, `ONT-ID : 1`, `SN : HEX (VENDOR-XXXX)` → SN normalizado sin guion, p.ej. `ZTEGDC47DF15`).

Resto de campos SmartOLT quedan vacíos/default.

## Tests

Suite unitaria (sin OLT live):

```bash
./mvnw -Dtest='com.dscorp.wispadmin.oltgateway.**' test
```

Incluye parsers, controller, service, filtro API key, `OltSshClientTest` (reuso de `SshClient`) y `HuaweiCliSessionKeepaliveTest` (keepalive / reconnect / ping ligero).

Fixtures CLI: `src/test/resources/oltgateway/fixtures/`.

## Validación live (lectura)

Script: `scripts/olt-gateway-validate-read.sh`

```bash
# Rápido (~5s): health, olt/info, autofind, by-sn, configured, detalle, optical, status, aliases
./scripts/olt-gateway-validate-read.sh --quick

# Completo (~60s): incluye GET /onus (inventario SSH live)
./scripts/olt-gateway-validate-read.sh

# SN/posición de prueba (fixture live)
export OLT_GATEWAY_SAMPLE_SN=ZTEGDC47DF15
export OLT_GATEWAY_SAMPLE_SLOT=1 OLT_GATEWAY_SAMPLE_PORT=7 OLT_GATEWAY_SAMPLE_ONT_ID=1
```

`GET /onu/get_onus_details_by_sn/{sn}` resuelve por SN case-insensitive y, si el inventario guardó SN hex (`5A544547…`) y la consulta usa formato vendor (`ZTEGDC47DF15`), hace fallback por posición `(olt, board, port, onu_index)` sin reinsertar.

Sync/signal (lentos, no incluidos en el script de lectura):

```bash
curl -X POST -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/admin/sync/inventory
curl -X POST -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/admin/sync/signal
```

## Construcción

- Dependencia: `org.apache.sshd:sshd-core:2.12.1` (Java 8).
- Swagger: `org.springdoc:springdoc-openapi-ui:1.8.0` — ver [swagger-openapi.md](./swagger-openapi.md).
- Mock vs real: `olt.gateway.mock.enabled`. No hay `@ConditionalOnProperty(olt.gateway.enabled)`.
- Component scan: `com.dscorp.wispadmin.oltgateway` debe estar en `WispAdminApplication.scanBasePackages`.
- `PlatformAuthFilter` excluye `/api/olt-gateway/**` (auth propia vía `X-Olt-Gateway-Key`) y rutas Swagger (`/swagger-ui/**`, `/v3/api-docs/**`).

## Fuera de alcance MVP

Write (`ont confirm`, delete, reset), SNMP, multi-OLT, caché, integración en `RealOltService`.
