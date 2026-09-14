# GETBULK multi-varbind en MA5608T — medición 2026-09-09

Bench live sobre OLT real `10.11.104.2` (2 slots, **22 puertos con ONTs, 817 ONTs**). Relacionado: [olt-snmp-optical-contention.md](./olt-snmp-optical-contention.md), [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md).

Harness: `scripts/snmp/snmp_probe_lib.py` + `scripts/snmp/probe-b-bench.py`. Community solo por env (`OLT_GATEWAY_SNMP_RO_COMMUNITY`), nunca en logs.

Reproducir (el mapa de puertos es prerequisito; escribe `/tmp/probe-ports.json`):

```bash
export OLT_GATEWAY_SNMP_RO_COMMUNITY=...   # nunca commitear el valor
python3 scripts/snmp/probe-a-boundary.py                       # mapa de puertos, ~3 s
python3 scripts/snmp/probe-b-bench.py perport_multi7_retry     # brazo ganador
```

Brazos disponibles: `lossrate full_multi7_retry perport_multi7_retry full_multi5 full_multi7 perport_multi7 perport_multi5 perport_1vb maxrep_sweep timeout_sweep parallel_ports inv_full_multi inv_perport_multi unified_perport unified_perport_par`. Brazos largos: lanzar en background y leer el `RESULT` en `/tmp/probe-b-SUMMARY.txt`.

## 1. El coste es por ONT, no por varbind

Mismo cursor, `maxRepetitions=25`, medianas de 3-12 muestras:

| nVarbinds | Valores por respuesta | Latencia mediana |
|---|---|---|
| 1 | 25 | 4423 ms |
| 2 | 50 | 4991 ms |
| 5 | 125 | 5138 ms |
| 7 | 175 | **2625 ms** |
| 13 | 325 | **3929 ms** |

Pedir 13 columnas cuesta **lo mismo o menos** que pedir 1. El agente hace **una lectura DDM/OMCI por ONT** y sirve todas las columnas desde ella. Nunca se observó `tooBig` (13 vb × 25 reps = 325 bindings entran con fragmentación UDP normal).

Coste por ONT según `maxRep` (7 vb): r5 → 396 ms, r10 → 154 ms, r25 → **105 ms**, r50 → 107 ms. **`maxRepetitions=25` es el óptimo**: satura el throughput con la página más pequeña, así un reintento cuesta la mitad que con r50.

## 2. Solo la tabla DDM (51) cuesta

Las tablas de config (43 `hwGponOntInfo`, 46 `hwGponOntStatus`) son casi gratis: 8 columnas × 817 filas full-table en **3.5 s** (mediana de página 91 ms). Todo el presupuesto del ciclo está en la tabla `51` (óptica).

> **Corrección (§7, misma tarde):** ese 3.5 s no es reproducible. A las 16:53 el mismo brazo dio **134 s** (mediana de página 833 ms, máx 41.6 s). Las tablas 43/46 también degradan y también pierden páginas; tomar 3.5 s como best case, no como presupuesto.

## 3. Causa raíz de los "fallos multi-varbind"

No era el scope ni el tamaño de la petición. El agente **descarta respuestas GETBULK de forma esporádica** en las tablas ONT (sin PDU de error: simplemente no contesta), con tasa medida ~2-8% e **independiente del tamaño**: una petición de 10 bindings se pierde igual que una de 175.

Descartado con evidencia:

- **Cruce de frontera de ifIndex/puerto**: cursor en el último ONT del puerto vs media tabla → latencia estadísticamente igual (~2.2 s vs ~2.2 s).
- **Degradación acumulada**: mismo cursor tras carga sostenida 3.2 s; tras 90 s de reposo 4.2 s; tras 120 s 5.5 s. El reposo no recupera nada.
- **Página 1 vs siguientes**: la página 1 desde la raíz es la más barata de la tabla (los primeros ONTs de `s0p0`, ~2.2 s) frente a ~5.4 s de mediana en profundidad. Eso explica la ilusión de "responde la 1ª y no las siguientes".

El fallo real era del harness: `retries=1` + `timeout=15 s` frente a un drop esporádico. En el walk full-table que **sí** completó, una página necesitó **3 intentos (50 s)**; con `retries=1` (2 intentos) ese walk habría muerto.

Bug adicional del harness: comparar OIDs como **string** (`'…23' <= '…8'`) producía `non_advancing` falso y truncaba el walk a 180 filas. Corregido con tuplas de int en `snmp_probe_lib.oid_tuple` y en `snmp-multivarbind-full-walk-bench.sh`. **Mismo bug sigue en `OpticalMaxRepAbProbe.kt:70`** (`last <= cursor` sobre `String`); el walker de producción `Snmp4jOltSnmpClient.walkColumn` usa `OID.compareTo` y está correcto.

## 4. Candidatos medidos (cobertura completa 817 ONTs)

| Brazo | Columnas | Wall-clock | Errores |
|---|---|---|---|
| Hoy: 5 DDM secuencial, 1 vb, full-table | 5 | **774 s** | 0 |
| Hoy: inventario 8 walks 1 vb | 8 | ~48 s (extrapolado de 2 medidos) | — |
| Full-table 7 vb, r25, t20, retries=2 | 7 | **255 s** | 1 página con 2 drops, recuperada (50 s) |
| **Per-port 7 vb, r25, t20, retries=2** | 7 | **273 s** | **ninguno**; página máx 12.4 s |
| Inventario full-table 8 vb, r25 | 8 | **3.5 s** (best case; 134 s en ventana mala, §7) | ninguno |

## 5. El paralelismo empeora

6 puertos más densos (394 ONTs), 7 vb, r25, retries=2:

| Paralelismo | Wall-clock | Página máx |
|---|---|---|
| 1 | **87.5 s** | 7.5 s |
| 2 | 91.6 s | 26.2 s |
| 3 | 118.0 s | 44.8 s |

La ruta DDM/OMCI del MA5608T es estrictamente serial: la concurrencia solo añade encolado e **induce drops**. **No subir `max_concurrent_snmp_walks` por encima de 1** y dejar `optical-parallel-ports=1`.

## 6. Configuración recomendada

| Property | Hoy | Recomendado |
|---|---|---|
| `olt.gateway.snmp.max-repetitions` | 25 | **25** (sin cambio) |
| `olt.gateway.snmp.timeout-ms` | 15000 | **20000** |
| `olt.gateway.snmp.retries` | 1 | **2** ← el cambio que hace la diferencia |
| `olt.gateway.snmp.optical-per-port-walks` | false | **true** |
| `olt.gateway.snmp.optical-parallel-ports` | 3 | **1** |
| `olt.gateway.snmp.optical-parallel-columns` | true | **false** (lo reemplaza el multi-varbind) |
| `olt.gateway.snmp.request-interval-ms` | 100 | 100–200 |
| `max_concurrent_snmp_walks` (modelo MA5608T) | 1 | **1** (sin cambio) |

Ciclo unificado estimado con el ganador: **~277 s** (óptica per-port 7 vb 273 s + inventario full-table 8 vb 3.5 s) frente a ~786-834 s hoy. Medido tras implementar: **407.9 s** — la óptica clavó los 273 s, el inventario costó 137 s en vez de 3.5 s (§7).

## 7. Implementación (2026-09-09)

### Walker multi-varbind

`SnmpMultiColumnWalk` (nuevo, `oltgateway/snmp/`): una `PDU.GETBULK` por página con **N varbinds** (`nonRepeaters=0`, `maxRepetitions` de properties), **un cursor por columna**, `OID.compareTo` para el avance y `betweenPages` para el pacing `request-interval-ms`.

Una columna se desactiva cuando su binding sale del subárbol (`!oid.startsWith(root)`), llega `endOfMibView`/`noSuchObject` (`variable.isException`) o el OID es null. El walk termina cuando no queda ninguna activa. Las respuestas **parciales** (menos repeticiones de las pedidas) no son error: cada columna sigue desde el último OID que sí recibió.

**Reparto de bindings: posicional (`index % nVarbinds`) validado por prefijo**, no enrutado por prefijo. RFC 3416 conserva el orden de request y rellena los varbinds agotados con `endOfMibView`, así que la posición es fiable; el prefijo es la guarda. Enrutar *solo* por prefijo corrompe la columna vecina: cuando `51.1.4` (Rx) se agota, el agente devuelve en ese hueco el primer OID de `51.1.5` (Tx), que sí cuelga de la raíz de Tx y estaría muy por detrás de su cursor → falso `non-advancing`. Test: `columna agotada que desborda al subarbol vecino no contamina a la vecina`.

Guarda extra contra bucle infinito: si una página no aporta ninguna fila y quedan columnas activas → `IOException` (`no progress`).

### Cliente

`Snmp4jOltSnmpClient` pasa de 15 walks de 1 columna a **2 walks multi-varbind**:

| Path | Antes | Ahora |
|---|---|---|
| `listConfiguredOnus()` | 8 walks × 1 vb | **1 walk × 8 vb** full-table |
| `listOptical(ports)` (default) | 7 walks × 1 vb por puerto | **1 walk × 7 vb** por puerto, scoped a `{col}.{ifIndex}` |
| `listOptical(null)` (fallback) | 7 walks × 1 vb, pool de 7 threads | **1 walk × 7 vb** full-table |
| `listAutofind()` | 1 walk × 1 vb | mismo walker con 1 columna |

Se fue el pool de columnas (`Executors.newFixedThreadPool(7)`) y los `safeDoubleColumn/safeIntColumn/safeStringColumn`: con un único walk ya no hay columnas que fallen por separado. Contratos intactos: `SnmpOntOptical`, `ParsedOnuSummary`, `SnmpOpticalMerger`, `lastOpticalWalkPortsFailed/Attempted`; `OltInventorySyncService.persistSnapshot` y `OltSignalPollService.applyOpticalUpdatesByPort` sin tocar.

Seam de test: parámetro opcional `pageSender: SnmpGetBulkPageSender?` (default `null` → sesión SNMP real). Permite verificar en unit test que las 8/7 columnas viajan en **una** página y que el decode llega hasta `ParsedOnuSummary`/`SnmpOntOptical`.

### Properties aplicadas

`OltGatewayProperties` + `application-dev.properties` + `application-prod.properties`:

| Property | Antes | Ahora |
|---|---|---|
| `olt.gateway.snmp.timeout-ms` | 15000 | **20000** |
| `olt.gateway.snmp.retries` | 1 | **2** |
| `olt.gateway.snmp.optical-per-port-walks` | false | **true** |
| `olt.gateway.snmp.optical-parallel-ports` | 3 | **1** |
| `olt.gateway.snmp.optical-parallel-columns` | true | **false** (no-op; se conserva para rollback de overrides) |
| `olt.gateway.snmp.max-repetitions` | 25 | 25 |
| `olt.gateway.snmp.request-interval-ms` | 100 | 100 |
| `max_concurrent_snmp_walks` (MA5608T) | 1 | 1 |

Sin variables de entorno nuevas; nombres y defaults actualizados en [vps-secrets-management.md](./vps-secrets-management.md) y `scripts/deploy.config.example`. **Ojo con el VPS**: `/opt/gigafiber/.env` tiene `PARALLEL_PORTS=3` y `PER_PORT=false` explícitos; hay que quitarlos o el override gana sobre el WAR.

### Inventario bajo el mismo lock

`OltInventorySyncService` recibe el mismo `OltSnmpPollLocker` que `OltSignalPollService` y envuelve **solo** su walk SNMP (`pollLock().withLock { client.listConfiguredOnus() }`), no el persist. Así inventario y óptica dejan de competir por el bus (`snmp_bus_timeout`) sin retener el lock durante escrituras a MySQL.

**Se mantienen los dos `@Scheduled`** (inventario 10 min, óptica 5 min) en vez de unificarlos en un tick:

- El lock ya serializa las dos pasadas; unificar no añade exclusión.
- Los `POST /admin/sync/inventory` y `/admin/sync/signal` manuales entran por el servicio, no por el scheduler: poner el lock en el servicio cubre los dos caminos, un scheduler unificado no.
- Cada pasada conserva su `AtomicBoolean`, su `skippedReason` y su fila en `olt_mgr_sync_run`. Un tick único tendría que inventar un resultado combinado y rompería telemetría existente.
- Cadencias distintas siguen siendo útiles: el inventario cuesta 3.5 s y detecta ONTs nuevas; la óptica cuesta ~273 s.

Coste: el tick de inventario puede esperar hasta ~5 min a que termine la óptica (espera en slices de `signal-interval-ms`, con steal al vencer el TTL de 20 min). Es preferible a competir por el bus.

### Verificación live (16:44–16:55 UTC, misma OLT)

`Snmp4jOltSnmpClientMultiVarbindLiveSmokeTest` (nuevo, `@Tag("live")`; requiere `OLT_SNMP_LIVE=true OLT_SNMP_OPTICAL_LIVE=true` + community por env). PASS con la config horneada (t20 / r2 / per-port / par=1 / r25):

```text
LIVE SNMP MULTIVB RESULT host=10.11.104.2 inventoryRows=817 inventoryMs=137171
  ports=22 opticalRows=817 withRx=734 opticalMs=270706 totalMs=407877 portsFailed=0
```

| Pasada | Esperado (§4) | Medido | Veredicto |
|---|---|---|---|
| Óptica per-port 7 vb, 22 puertos | 273 s, 0 errores | **270.7 s, 0 puertos fallidos**, 817 filas (734 con Rx) | Clava la predicción |
| Inventario full-table 8 vb | 3.5 s | **137.2 s**, 817 filas × 8 columnas | 40x sobre lo estimado |
| Ciclo total | ~277 s | **407.9 s** | ~2x mejor que los 786-834 s de antes, no 3x |

El desvío del inventario **no** es del walker. Re-corriendo el mismo brazo del harness python inmediatamente después (16:53): `durationMs=133969 pages=34 pageMsMedian=833 pageMsMax=41628` — 134 s, paridad con los 137 s de Kotlin. Es decir, el **3.5 s de §2 era una ventana buena** (`pageMsMedian=91`); en esta ventana las tablas 43/46 respondían con mediana de página 833 ms y una página de 41.6 s (drop + reintentos). El agente degrada las tablas de config igual que la DDM, solo que con menos amplitud.

Pendiente (no implementado, requiere su propia medición): un `timeout-ms` más corto **solo** para la pasada de inventario. Con páginas de ~100-800 ms, esperar 20 s × 2 reintentos por drop es lo que se come el presupuesto; bajar a ~5 s recuperaría el drop 4x más rápido sin afectar a la óptica, que sí necesita los 20 s.

### Bug del probe corregido

`OpticalMaxRepAbProbe` comparaba `last <= cursor` como **String** (mismo bug que el harness bash): al pasar de ONT `.8` a `.23` reportaba `non_advancing` falso y truncaba el walk. Ahora usa `OID.compareTo`. Esto invalida los `non-advancing response` de [olt-snmp-optical-contention.md](./olt-snmp-optical-contention.md) §4.

## 8. Pasada fusionada inventario + óptica (2026-09-09, tarde)

Pregunta: si el coste es por ONT, ¿se puede pedir inventario y óptica en el **mismo** GETBULK per-port y que el inventario salga gratis?

Harness nuevo: `scripts/snmp/probe-c-unified.py` (brazos `dedup pdu_limit page_latency unified32 unified22 empty10 unified32_t6 optical22_ref inv_timeout_sweep empty_probe ab_interleaved`). Logs `/tmp/probe-c-run{1..4}.log`, resumen `/tmp/probe-c-SUMMARY.txt`.

### 8.1 Son 13 columnas únicas, no 15

`ONT_RANGING` y `ONT_MATCH_STATUS` (`.46.1.20` y `.46.1.5`) están en los dos sets: los pide el inventario y los pide la óptica. La unión real es **13 columnas**: 8 de inventario (`.43.1.3 sn`, `.43.1.9 lineProf`, `.43.1.10 srvProf`, `.43.1.9/.43.1.4 description`, `.46.1.15 runState`, `.46.1.5 matchState`, `.46.1.20 ranging`, `.46.1.23 lastDown`) + 5 ópticas exclusivas (`.51.1.4 rx`, `.51.1.5 tx`, `.51.1.6 oltRx`, `.51.1.1 temp`, `.51.1.3 bias`).

Cabe de sobra en el PDU. Barrido nVarbinds × maxRep, **ningún `tooBig` en ninguna combinación**:

| nVarbinds | maxRep | Bindings/página | Latencia mediana (n=4) |
|---|---|---|---|
| 7 | 25 | 175 | 6592 ms |
| 13 | 20 | 260 | 5698 ms |
| 13 | 25 | **325** | 6576 ms |
| 15 | 20 | 300 | 6385 ms |
| 15 | 25 | **375** | 5510 ms |

375 bindings/página pasan sin error. El límite no es el PDU.

### 8.2 La tesis "coste por ONT" se sostiene

Misma raíz, `maxRep=25`, n=12 por brazo (`page_latency`):

| nVarbinds | Tabla | Latencia mediana | ms por fila ONT |
|---|---|---|---|
| 7 | `.51` óptica | 5503 ms | 220 |
| 8 | `.43`/`.46` config | **583 ms** | 23 |
| 13 | fusión | 6459 ms | 258 |
| 15 | fusión + duplicados | 6723 ms | 269 |

Las tablas de config son ~10x más baratas que la DDM: quien manda es `.51`. Añadir las 6 columnas de config a la página óptica encarece la página un ~17% en medianas sueltas, pero eso mezcla ventanas. El A/B **interleaved** (mismos 3 puertos, 60 ONTs, 3 rondas alternando 13vb/7vb) aísla la deriva:

| Brazo | ms mediana (3 rondas) | ms/ONT |
|---|---|---|
| 7 vb (solo óptica) | 16847 | 280.8 |
| 13 vb (fusionado) | 17399 | **290.0** |

**+3.3%**. Las 6 columnas de inventario cuestan 9 ms por ONT; leerlas aparte cuesta la pasada entera.

### 8.3 Brazo fusionado completo vs las dos pasadas

Mismo ciclo de medición (17:35–17:52 UTC), para que la deriva del agente afecte igual a los dos:

| Brazo | Scope | Columnas | Wall-clock | Puertos fallidos |
|---|---|---|---|---|
| `C6` óptica sola (referencia de la ventana) | per-port ×22 | 7 | **490.8 s** | 0 |
| `C3` fusionado | per-port ×32 | 13 | **454.6 s** | 0 |
| `C7` inventario full-table | full-table | 8 | 5.8 s | 0 |

En esa ventana la OLT estaba lenta (la óptica sola costó 490.8 s frente a los 270.7 s del smoke de la mañana), pero la comparación intra-ventana es la válida: **el brazo fusionado de 32 puertos cuesta menos que el de óptica sola de 22** y entrega además las 8 columnas de inventario con `817/817` filas en cada una de las 13.

### 8.4 Los puertos vacíos no son gratis — probe barato

Los 10 puertos sin ONTs costaron 2.8-7.2 s cada uno con 13 vb: **28.1 s** de peaje por ciclo solo por preguntar. La página se paga aunque no haya filas.

| Probe por puerto vacío | ms/puerto | Total 10 puertos |
|---|---|---|
| 13 vb (fusión completa) | 2806 | 28.1 s |
| 8 vb (solo config) | 96 | 0.96 s |
| **1 vb (`runState` de `.46`)** | **51** | **0.52 s** |

Por eso el cliente hace primero un GETBULK de **una** columna de la tabla de config (`ONT_RUN_STATUS.{ifIndex}`). Vacío seguro = `endOfMibView` o OID fuera de subárbol (el agente dice “no hay más”). PDU vacío o bindings inconclusos **no** se tratan como puerto vacío: el puerto cuenta en `portsFailed` (fail-closed; un skip falso tombstonearía ONUs). Barrer los 32 puertos cuesta 0.5 s más que barrer los 22, no 28 s.

### 8.5 Riesgo de cobertura: resuelto barriendo la placa entera

`OpticalPollScope.portsFromOnus` deriva los puertos de las ONUs en BD, así que un ONT configurado en un puerto hoy vacío sería invisible para un inventario per-port. Autofind (`.52`) **no** cubre el hueco: solo ve ONTs *no* configuradas; una ONT ya autorizada en un puerto virgen no aparece ni en autofind ni en el scope derivado.

Solución implementada: `OpticalPollScope.fusedScanPorts(onus, portsPerBoard)` enumera **todos** los puertos de cada placa que tenga al menos una ONU (0..15 por placa), en vez de solo los puertos ocupados, y sin filtrar por `optical-online-only` (el inventario necesita también las offline). Con el probe barato el sobrecoste es de ~0.5 s.

Segunda guarda: `persistSnapshot` hace soft-delete de lo que no aparece en el snapshot. Un barrido con puertos fallidos borraría ONUs válidas, así que **el inventario solo se publica si `portsFailed == 0`**; si falla algún puerto, la óptica se aplica igual (es per-port y no borra nada) pero el inventario cae al walk full-table de siempre.

Tercera guarda: el sync **no** persiste un snapshot fusionado cuyo set de SN no cubre las ONUs activas en BD (`createdAt`/`updatedAt` posteriores a `capturedAt`, primera ONU de una placa no barrida, puerto que el probe saltó). Consume el snapshot y cae a `listConfiguredOnus()`.

`fused-inventory-optical=true` fuerza per-port serial (`parallelism=1`) e ignora `OLT_GATEWAY_SNMP_OPTICAL_PER_PORT` / `OLT_GATEWAY_SNMP_OPTICAL_PARALLEL_PORTS`. Si esas flags contradicen, hay WARN; el arranque no falla.

### 8.6 Timeout de inventario (Parte 4)

Barrido del walk de inventario 8 vb full-table variando timeout/reintentos, en ventana sana:

| timeout / retries | Wall-clock | Página mediana | Página máx |
|---|---|---|---|
| 20 s / 2 (actual) | 5816 ms | 95 ms | 1296 ms |
| **5 s / 4** | 5351 ms | 91 ms | 1475 ms |
| 3 s / 6 | 4035 ms | 103 ms | 256 ms |
| 2 s / 8 | 3574 ms | 91 ms | 210 ms |

En ventana sana da igual: no hay drops que esperar. El valor está en la ventana mala, donde cada drop cuesta hoy 20 s × 3 intentos = 60 s (los 137 s de §7 son eso). Con páginas de 100-800 ms, un techo de 5 s es 6x el peor caso observado y acota cada drop a 25 s en vez de 60 s. Aplicado como `inventory-timeout-ms=5000` / `inventory-retries=4` **solo** a los jobs `INVENTORY` y `AUTOFIND`; `OPTICAL` y `FUSED` conservan 20 s / 2 porque sus páginas legítimas llegan a 12-25 s.

### 8.7 Implementación

| Archivo | Cambio |
|---|---|
| `snmp/OltSnmpClient.kt` | `OltSnmpFusedSnapshot(onus, optical, portsAttempted, portsFailed)` + `listInventoryAndOptical(ports)` con default no-fusionado |
| `snmp/Snmp4jOltSnmpClient.kt` | `FUSED_COLUMNS` (13), `fetchFusedForPort`, probe barato `portHasOnts`, decode posicional a `ParsedOnuSummary` + `SnmpOntOptical` desde la misma página |
| `snmp/OltSnmpJobTimeouts.kt` | Nuevo. `Budget(timeoutMs, retries)` por `SnmpJobType` |
| `snmp/OltSnmpBus.kt` | `SnmpJobType.FUSED` |
| `snmp/OpticalPollScope.kt` | `fusedScanPorts(onus, portsPerBoard)` |
| `service/OltFusedInventoryCache.kt` | Nuevo. Snapshot consume-once con `capturedAt` y `takeIfFresh(maxAgeMs)` |
| `service/OltSignalPollService.kt` | `captureFused()`; publica el inventario en la caché solo si `portsFailed == 0` |
| `service/OltInventorySyncService.kt` | Consume la caché si está fresca **y** cubre los SN activos de BD; si no, walk full-table |
| `config/OltGatewayConfig.kt` | Bean `OltFusedInventoryCache` inyectado en los dos servicios |

Los dos `@Scheduled` siguen separados y los `POST /admin/sync/*` siguen entrando por el servicio: el tick de óptica hace la pasada fusionada y deja el inventario en la caché; el tick de inventario lo recoge sin volver a hablar con la OLT. Contratos intactos (`SnmpOntOptical`, `ParsedOnuSummary`, `SnmpOpticalMerger`, `lastOpticalWalkPorts*`); `persistSnapshot` y `applyOpticalUpdatesByPort` sin tocar. Reparto posicional validado por prefijo sin cambios (§7).

Properties nuevas (defaults en `application-dev.properties` y `application-prod.properties`, nombres en [vps-secrets-management.md](./vps-secrets-management.md)):

| Property | Env | Default |
|---|---|---|
| `olt.gateway.snmp.fused-inventory-optical` | `OLT_GATEWAY_SNMP_FUSED_INVENTORY_OPTICAL` | `true` |
| `olt.gateway.snmp.fused-snapshot-max-age-ms` | `OLT_GATEWAY_SNMP_FUSED_SNAPSHOT_MAX_AGE_MS` | `900000` |
| `olt.gateway.snmp.inventory-timeout-ms` | `OLT_GATEWAY_SNMP_INVENTORY_TIMEOUT_MS` | `5000` |
| `olt.gateway.snmp.inventory-retries` | `OLT_GATEWAY_SNMP_INVENTORY_RETRIES` | `4` |

Tests (TDD, RED demostrado antes de cada implementación): `Snmp4jOltSnmpClientFusedTest` (13 columnas en una página, decode dual, puerto vacío canónico vs PDU vacío = `portsFailed`, fused serial aunque `PARALLEL_PORTS=3`), `OpticalPollScopeTest` (`fusedScanPorts` cubre toda la placa), `OltFusedInventoryCacheTest`, `OltFusedPassWiringTest` (scope de placa, publicación, no-publicación con puerto fallido, consumo desde el sync, fallback sin snapshot, authorize-después-de-publish no tombstonea, fused inmune a PER_PORT=false), `OltSnmpJobTimeoutsTest`. Suite `com.dscorp.wispadmin.oltgateway.**`: **488 tests, 0 fallos** (8 skipped: live).

### 8.8 Verificación live (18:0x UTC)

`Snmp4jOltSnmpClientFusedLiveSmokeTest` (nuevo, `@Tag("live")`), 32 puertos, config horneada:

```text
LIVE SNMP FUSED RESULT host=10.11.104.2 portsScanned=32 occupiedPorts=22
  inventoryRows=817 opticalRows=817 withRx=737 fusedMs=198320 portsFailed=0
```

| Ciclo | Wall-clock |
|---|---|
| Antes: inventario full-table 137.2 s + óptica per-port 270.7 s | **407.9 s** |
| Ahora: pasada fusionada 32 puertos | **198.3 s** |

**−51.4%.** El inventario deja de costar (sale de la misma página que la óptica) y el barrido de 32 puertos en vez de 22 no se nota gracias al probe barato. Cobertura completa: 817 filas de inventario y 817 de óptica (737 con Rx), 0 puertos fallidos.
