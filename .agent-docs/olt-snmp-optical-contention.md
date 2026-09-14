# Contención SNMP óptica (prod ↔ staging)

**Canónico** de timeouts GETBULK, lock Redis mutuo y retry de puerto. El push hacia Core no cambia: [optical-push-gateway-core.md](./optical-push-gateway-core.md).

SNMP only. No hay fallback CLI `display ont optical-info … all` en el poller.

## 1. Por qué timeouts

Prod y staging Gateway hablan con **la misma OLT**. Dos walks GETBULK a la vez (o un walk + otro proceso) hacen que el agente MA5608T deje páginas sin respuesta. El poller por puerto aísla el fallo (`SNMP_OPTICAL_PORT_FAIL`); no reintroduce CLI.

Medición 2026-09-09: el agente pierde páginas **incluso con un solo walk serial** (~2-8%, independiente del tamaño de la petición), y la concurrencia lo empeora de forma medible. Detalle, tabla de candidatos y config recomendada (`retries=2`, multi-varbind): [olt-snmp-multivarbind-getbulk-bench-2026-09-09.md](./olt-snmp-multivarbind-getbulk-bench-2026-09-09.md).

## 2. Mitigaciones (código)

| Medida | Valor | Notas |
|--------|--------|--------|
| GETBULK multi-varbind | **1 PDU por página con N columnas** (7 óptica, 8 inventario) | `SnmpMultiColumnWalk`; cursor independiente por columna. El coste del agente es por ONT, no por varbind |
| Timeout SNMP | **20 s** (`OLT_GATEWAY_SNMP_TIMEOUT_MS`, default WAR `20000`) | Antes 15 s (y 5 s en prod viejo) |
| Retries SNMP4j | **2** (`OLT_GATEWAY_SNMP_RETRIES`) | El cambio que evita que un drop esporádico mate el walk: una página live necesitó 3 intentos (50 s) |
| maxRepetitions | **25** (default) | Óptimo medido (105 ms/ONT con 7 vb). Ver bench §1 |
| Óptica per-port | **true** (`OLT_GATEWAY_SNMP_OPTICAL_PER_PORT`) | Mismo wall-clock que full-table pero un drop cuesta un puerto, no el dataset |
| Paralelismo de puertos | **1** (`OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS`) | La ruta OMCI del MA5608T es serial: par=2/3 empeoró el wall-clock e indujo drops |
| Retry de puerto | Un GETBULK fallido se **reintenta una vez al final del mismo ciclo** | `OpticalPortWalkRunner`; el primer pase usa `opticalParallelPorts` |
| Lock Redis mutuo | Key **`olt-snmp-poll`** (no namespaced) | Prod y staging ceden el uno al otro **solo si ambos WARs tienen el código de lock** |
| Inventario bajo el mismo lock | `OltInventorySyncService` toma `olt-snmp-poll` **antes** de leer la caché fusionada | Si el poll óptico tenía el lock, el sync espera y **relee** la caché al entrar (no camina la OLT otra vez). Persist va fuera del lock. Dos `@Scheduled`, un solo mutex |
| TTL del lock | **20 min** (`OLT_GATEWAY_SNMP_POLL_LOCK_TTL_MS=1200000`) | Peor ciclo |
| Espera | Si el lock está tomado: espera en slices = `signal-interval-ms`; si el TTL vence sin release, **poll anyway** (steal) | Release en `finally` |

`poll-lock-shared=true` (default) **ignora** `gigafiber.redis.namespace` (`prod:` / `stg:`). Esa key es la excepción al aislamiento Redis: ambos WARs deben verse.

Si Redis está apagado o `OLT_GATEWAY_SNMP_POLL_LOCK_ENABLED=false`, el poller sigue (NoOp).

## 3. Carriles staging = prod

Defaults WAR (`application-prod.properties`, mismos para Gateway staging horneado `prod,oltgateway`):

| Propiedad | Default |
|-----------|---------|
| `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS` | **1** (antes 3) |
| `OLT_GATEWAY_SNMP_OPTICAL_PER_PORT` | **true** (antes false / full-table) |
| `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_COLUMNS` | **false** — **no-op** desde el multi-varbind |

Si el `.env` del VPS trae overrides viejos (`PARALLEL_PORTS=3`, `PER_PORT=false`), **la óptica no fusionada** sí los honra. La pasada fusionada (`OLT_GATEWAY_SNMP_FUSED_INVENTORY_OPTICAL=true`, default) **ignora** esas dos flags: siempre per-port serial (`parallelism=1`) y loguea WARN; **no** falla el arranque. Para la óptica no fusionada conviene quitar los overrides para que aplique la configuración medida.

El job A/B (§4) usa **un** puerto y **parallel=1**. El poller normal sigue con los carriles de arriba.

### VPS `.env` (2026-09-09, nombres; sin secretos)

Alineado en `/opt/gigafiber/.env` (compartido tomcat-staging + tomcat9027). **2026-09-09 tarde:** se comentaron `OLT_GATEWAY_SNMP_OPTICAL_PER_PORT` y `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS` (backup `/opt/gigafiber/.env.bak.snmp-fused-20260909140502`) para que apliquen los defaults del WAR. El proceso de prod no cambia hasta el próximo recreate de `tomcat9027`.

| Nombre | Antes | Ahora |
|--------|-------|--------|
| `OLT_GATEWAY_SNMP_TIMEOUT_MS` | 5000 | **15000** |
| `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS` | 1 | **3** (quitar en el próximo deploy; fused ignora este override y fuerza 1) |
| `OLT_GATEWAY_SNMP_OPTICAL_PER_PORT` | true | **false** (quitar en el próximo deploy; fused ignora este override) |
| `OLT_GATEWAY_SNMP_POLL_LOCK_ENABLED` | ausente | ausente (WAR default **true**) |

Backup remoto: `/opt/gigafiber/.env.bak.snmp-align-20260909002949`.

**Runtime comprobado tras deploy staging Gateway:**

| Contenedor | timeout | parallel | per-port | lock env | health |
|------------|---------|----------|----------|----------|--------|
| `tomcat-staging` | 15000 | 3 | false | ausente → default on | actuator **200**, `/api/olt-gateway/health` **200** |
| `tomcat9027` (prod) | **5000** (proceso viejo) | **1** | **true** | ausente | no reiniciado |

Prod no se desplegó en este turno. El `.env` nuevo solo entra en prod en el próximo recreate/restart de `tomcat9027`. Si el WAR de prod **no** incluye `OltSnmpPollLock`, staging puede esperar a la key `olt-snmp-poll`, pero prod **no** espera a staging ni al job A/B.

## 4. Job A/B maxRep 15 vs 10 (puerto 1/6) — live 2026-09-09

No cambia el default de producción (25). Midió GETBULK de Rx del puerto **1/6**, timeout 15 s, retries 1, lock Redis `olt-snmp-poll`, **sin** paralelismo de 3 carriles.

```bash
# Mac con OLT alcanzable y Redis del VPS (túnel). No imprimir la community.
export OLT_GATEWAY_SNMP_RO_COMMUNITY=…
export REDIS_HOST=127.0.0.1
export REDIS_PORT=16379   # túnel al contenedor gigafiber-redis (no publicado en el host)
# REDIS_PASSWORD=…   # si aplica
./scripts/snmp/optical-maxrep-ab.sh
```

El script corre `OpticalMaxRepAbLiveSmokeTest` (`OLT_SNMP_MAXREP_AB=true`). Logs `MAXREP_AB` / `MAXREP_AB_PAGE`.

### Resultado live (GPON 1/6, lock Redis usado)

`OpticalMaxRepAbLiveSmokeTest` PASS. Espera del lock ~17 min (staging/prod ciclo óptico); walks SNMP ~15 s. `MAXREP_AB_LOCK key=olt-snmp-poll host=127.0.0.1`.

| maxRep | Páginas OK | `pagesTimedOut` (contador) | Filas Rx | Notas |
|--------|------------|----------------------------|----------|--------|
| **15** | **5** | 1 | 74 | Páginas 1–4: 15 bindings; pág. 5: 14. Fallo final: `non-advancing response` |
| **10** | **7** | 1 | 70 | Páginas 1–7: 10 bindings. Fallo final: `non-advancing response` |

**Cero timeouts SNMP** (`request timed out` no apareció). El contador `pagesTimedOut` trata cualquier `!ok` como timeout, incluido el `non-advancing response` al cerrar el walk.

**Corrección 2026-09-09:** esos `non-advancing response` eran **falsos**. `OpticalMaxRepAbProbe` comparaba cursores como **String** (`'…23' <= '…8'`), así que el walk se cortaba al pasar de ONT 8 a ONT 23. Ya compara con `OID.compareTo` (test `walk compara cursores como OID numerico no como texto`). Las filas de arriba subestiman la cobertura del puerto.

### Recomendación

Superada por el bench multi-varbind: **maxRep 25** es el óptimo medido con 7 varbinds (105 ms/ONT, la página más pequeña que satura el throughput, así un reintento cuesta la mitad que con r50). No bajar a 15/10.

## 5. Variables (solo nombres)

Catálogo: [vps-secrets-management.md](./vps-secrets-management.md). Plantilla: `scripts/deploy.config.example`.

| Nombre | Default WAR | Rol |
|--------|-----------|-----|
| `OLT_GATEWAY_SNMP_TIMEOUT_MS` | 20000 | Timeout GET/GETBULK |
| `OLT_GATEWAY_SNMP_RETRIES` | 2 | Retries snmp4j (drops esporádicos del agente) |
| `OLT_GATEWAY_SNMP_MAX_REPETITIONS` | 25 | Repeticiones por varbind y página |
| `OLT_GATEWAY_SNMP_REQUEST_INTERVAL_MS` | 100 | Pacing entre páginas |
| `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS` | 1 | Carriles de puertos del poller (ruta OMCI serial) |
| `OLT_GATEWAY_SNMP_OPTICAL_PER_PORT` | true | Per-port (aísla el drop en un puerto) |
| `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_COLUMNS` | false | No-op con multi-varbind; solo rollback |
| `OLT_GATEWAY_SNMP_POLL_LOCK_ENABLED` | true | Lock del ciclo óptico |
| `OLT_GATEWAY_SNMP_POLL_LOCK_KEY` | `olt-snmp-poll` | Key compartida |
| `OLT_GATEWAY_SNMP_POLL_LOCK_TTL_MS` | 1200000 | TTL ~20 min |
| `OLT_GATEWAY_SNMP_POLL_LOCK_SHARED` | true | No prefix `stg:`/`prod:` |
| `OLT_GATEWAY_SNMP_FUSED_INVENTORY_OPTICAL` | true | Inventario + óptica en una pasada per-port de 13 columnas |
| `OLT_GATEWAY_SNMP_FUSED_SNAPSHOT_MAX_AGE_MS` | 900000 | Frescura del snapshot de inventario que deja la pasada fusionada |
| `OLT_GATEWAY_SNMP_INVENTORY_TIMEOUT_MS` | 5000 | Timeout solo de jobs INVENTORY/AUTOFIND |
| `OLT_GATEWAY_SNMP_INVENTORY_RETRIES` | 4 | Retries solo de jobs INVENTORY/AUTOFIND |

## 6. Pasada fusionada (2026-09-09)

El ciclo dejó de ser dos pasadas. Una sola pasada per-port de **13 columnas** (8 config + 5 DDM) sobre los **32 puertos** de la topología, con probe barato de 1 varbind para descartar los vacíos, entrega inventario y óptica juntos: **198.3 s** frente a los 407.9 s de las dos pasadas separadas (−51%). El presupuesto de contención sigue siendo la tabla `.51`; las columnas de config viajan gratis (+3.3% medido en A/B interleaved).

`fused-inventory-optical=true` **no** depende de `optical-per-port-walks` ni de `optical-parallel-ports`: `listInventoryAndOptical` fuerza serial. PDU vacío o probe inconcluso **falla el puerto** (`portsFailed++`, no se publica inventario). Un snapshot fusionado que omita SNs activos en BD (authorize después de `publish`, puerto no barrido) **no** se persiste: el sync cae al walk live.

Detalle, números y riesgo de cobertura: [olt-snmp-multivarbind-getbulk-bench-2026-09-09.md](./olt-snmp-multivarbind-getbulk-bench-2026-09-09.md) §8.
