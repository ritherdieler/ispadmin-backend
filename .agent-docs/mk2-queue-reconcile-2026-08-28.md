# Regularización colas MK2 vs BD prod

**Fecha:** 2026-08-28 · **Ampliado:** 2026-09-10 (borrado de colas de CANCELLED, `RETARGET`, filtros de apply)

Operación puntual **sin deploy de backend**: SQL para alinear `host_device_id` y script Node que cruza suscripciones MySQL con colas RouterOS REST del **MK2** (`network_device.id = 8`, `38.224.231.4`).

## Contexto

- Todas las suscripciones medibles deben apuntar a **MK2** (`host_device_id = 8`).
- Las colas simples deben existir en MK2 con nombre y `max-limit` canónicos (misma plantilla que `QueueManagerService`).
- Colas huérfanas en MK2 (IP estática sin fila en BD): **solo reporte**, no se borran automáticamente.
- Colas de suscripciones **CANCELLED**: se borran solo con `--delete-cancelled` explícito.

## Archivos

| Archivo | Uso |
|---------|-----|
| `scripts/sql/mk2-regularize-host-device.sql` | Dry-run + UPDATE comentado |
| `scripts/mk2-reconcile-queues.mjs` | CLI reconciliación |
| `scripts/mk2-reconcile-queues-lib.mjs` | Lógica pura (nombre, diff) |
| `scripts/mk2-reconcile-queues.test.mjs` | Tests Node sin Spring |

## Variables de entorno

No commitear secretos. Usar `.env.local` gitignored o export en shell.

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=13306
export MYSQL_USER=root
export MYSQL_PASSWORD='...'
export MYSQL_DATABASE=ispadmin

export MK2_HOST=38.224.231.4
export MK2_USER=gigafiber2023
export MK2_PASS='...'
```

Alias aceptados: `ROUTEROS_MK2_HOST`, `ROUTEROS_MK2_USER`, `ROUTEROS_MK2_PASSWORD`.

## Paso 0 — Verificar REST MK2

```bash
curl -sk -u "$MK2_USER:$MK2_PASS" \
  -X POST "https://38.224.231.4/rest/queue/simple/print" \
  -H 'Content-Type: application/json' \
  -d '{"\.proplist":[".id","name","target","max-limit"]}' | head -c 500
```

Debe responder HTTP 200 con JSON array.

## Paso 1 — Túnel MySQL prod

```bash
./scripts/db-tunnel.sh start
./scripts/db-tunnel.sh status
```

Conexión: `127.0.0.1:13306`, base `ispadmin`.

## Paso 2 — SQL `host_device_id` → 8

**Dry-run (solo lectura):**

```bash
mysql -h 127.0.0.1 -P 13306 -u root -p ispadmin < scripts/sql/mk2-regularize-host-device.sql
```

Revisar filas que cambiarían. Opcional conteo:

```sql
SELECT COUNT(*) AS to_update FROM subscription
WHERE service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
  AND ip IS NOT NULL AND ip <> ''
  AND (installation_type IS NULL OR installation_type <> 'ONLY_TV_FIBER')
  AND (host_device_id IS NULL OR host_device_id <> 8);
```

**Apply:** descomentar el bloque `UPDATE` en `scripts/sql/mk2-regularize-host-device.sql` y ejecutar de nuevo, o:

```sql
UPDATE subscription
SET host_device_id = 8
WHERE service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
  AND ip IS NOT NULL AND ip <> ''
  AND (installation_type IS NULL OR installation_type <> 'ONLY_TV_FIBER')
  AND (host_device_id IS NULL OR host_device_id <> 8);
```

## Paso 3 — Reconciliar colas (dry-run)

```bash
node scripts/mk2-reconcile-queues.mjs --dry-run --insecure
```

Revisar en la salida JSON:

| Acción | Significado |
|--------|-------------|
| `ADD` | Falta cola en MK2 |
| `UPDATE` | Mismo `id:N`, drift en `name` o `max-limit` → PATCH |
| `RETARGET` | El abonado cambió de IP y su cola `id:N` sigue en la IP vieja → PATCH de `target` (conserva contadores) |
| `RECLAIM` | Cola sin prefijo `id:` → remove + add |
| `CONFLICT` | IP en cola de otra suscripción → revisión manual |
| `DELETE_CANCELLED` | Cola cuyo `id:N` o IP es de una suscripción **CANCELLED** → borrar solo con `--delete-cancelled` |
| `ORPHAN` | Cola MK2 sin suscripción en BD → solo reporte, **nunca** se borra |
| `UNCHANGED` | OK |

Targets `<pppoe-...>` se ignoran en el cruce; aparecen en `pppoeIgnored`. Cualquier target IPv4 entra al cruce (no solo `192.168.*`): si no, una cola en otro prefijo (p. ej. `192.169.22.170`) queda invisible y el diff propone un `ADD` que fallaría por nombre duplicado.

`max-limit` se compara normalizado a bits: `200M/200M` y `200000000/200000000` son iguales y no generan `UPDATE`.

**Reglas de seguridad del borrado:**

- `CANCELLED_SQL` excluye toda IP que además pertenezca a una suscripción no cancelada (`NOT EXISTS`).
- Antes de borrar, medir tráfico real: dos lecturas de `bytes` separadas ~25 s. Un cancelado que sigue navegando **no** debe perder su cola (quedaría sin límite de velocidad); se excluye con `--skip-queue-ids` y se escala como corte, no como higiene.

## Paso 4 — Apply

Tras revisar conflicts/orphans:

```bash
node scripts/mk2-reconcile-queues.mjs --apply --insecure
```

Apply acotado (recomendado: separar altas/patches del borrado):

```bash
node scripts/mk2-reconcile-queues.mjs --apply --only ADD,UPDATE,RETARGET,RECLAIM --insecure
node scripts/mk2-reconcile-queues.mjs --apply --delete-cancelled --only DELETE_CANCELLED \
  --skip-queue-ids '*85,*113,*176' --insecure
```

| Flag | Uso |
|------|-----|
| `--only A,B` | Limita qué acciones se ejecutan en `--apply` |
| `--delete-cancelled` | Habilita el borrado de `DELETE_CANCELLED` (sin él no borra nada) |
| `--skip-queue-ids *1,*2` | Excluye colas concretas de cualquier acción |
| `--cancelled-json FILE` | Canceladas desde JSON, para pruebas offline |

Guardar reporte:

```bash
node scripts/mk2-reconcile-queues.mjs --dry-run --insecure --json-out /tmp/mk2-reconcile-report.json
```

## Paso 5 — Spot-check

1. Elegir 2–3 suscripciones del reporte `ADD`/`UPDATE`.
2. Verificar cola en MK2: nombre con `id:{subscriptionId}` y `max-limit` del plan.
3. Si el backend corre: `POST /ispadmin/traffic/poll` y revisar consumo en backoffice.

## Convención por ambiente

- Prod: `id:{id}, usuario:...` (sin prefijo). Comment opcional con el `InstallationType`.
- Staging: `[stg] id:{id}, usuario:...` y comment `env=stg`.
- El reconciliador **ignora** colas con prefijo `[tag]` o `env=` en el comment. No las trata como huérfanas de prod.
- Purga de staging: `scripts/mk2-purge-staging-queues.mjs --tag stg` (dry-run; `--apply` para borrar).

## Plantilla de nombre (réplica backend)

- **FIBER:** `id:{id}, usuario:{first} {last}, lugar:{place}, nap:{nap}, plan:{plan}, tipo:{type}`
- **WIRELESS:** sin segmento `nap:`
- Staging antepone `[stg] ` a esa plantilla.

**max-limit:** `{uploadSpeed}M/{downloadSpeed}M`

## Tests locales (sin prod)

```bash
node --test scripts/mk2-reconcile-queues.test.mjs
./mvnw test -Dtest=Mk2ReconcileQueuesScriptTest
```

Modo offline con fixtures JSON:

```bash
node scripts/mk2-reconcile-queues.mjs --dry-run \
  --subscriptions-json /tmp/subs.json \
  --queues-json /tmp/queues.json
```

## Riesgos

- **Contadores de tráfico:** preferir `UPDATE` (PATCH); recreate solo en `RECLAIM`.
- **Conflictos de IP:** no se auto-resuelven.
- **Pools .26 vs .30:** el script no mueve IPs; solo alinea colas según BD actual.

## Fuera de alcance

- Endpoints Spring nuevos
- Borrado automático de huérfanas MK2
- Corrección de `/generate-simple-queues` legacy
- Resolución de `CONFLICT` (dos suscripciones con la misma IP en BD)

## Ejecución prod 2026-08-28

**Fase 1 SQL `host_device_id`:** 0 filas pendientes (todas medibles ya en `host_device_id=8`).

**Dry-run colas (865 suscripciones, 217 colas MK2):**

| Métrica | Valor |
|---------|-------|
| ADD | 727 |
| UPDATE | 133 |
| RECLAIM | 4 |
| CONFLICT | 1 |
| ORPHAN | 24 |
| pppoe ignorados | 51 |

**Apply (~84 s):** 719 ADD, 133 PATCH, 1 RECLAIM OK; **11 errores** (`already have such name` — nombre duplicado en MK2). Colas MK2 pasaron de ~217 a ~933.

**Conflicto manual:** suscripción **629** vs cola `id:1629` en IP `192.168.30.36`.

**Spot-check:** suscripción **615** (`192.168.25.92`) — cola `*42F` con nombre canónico correcto.

**Nota técnica:** el JOIN usa tabla `nap_box` (no `napbox`). Tras apply, un segundo dry-run puede marcar muchos UPDATE por formato `max-limit` (RouterOS devuelve bytes `200000000/200000000` vs `200M/200M` en PATCH); no implica drift operativo si nombre e IP coinciden.

Reportes locales (máquina operador): `/tmp/mk2-dryrun-report.json`, `/tmp/mk2-apply-report.json`.

## Ejecución prod 2026-09-10

Segunda pasada tras la auditoría profunda. **870** suscripciones medibles, **435** canceladas con IP, **973** colas MK2.

**Dry-run inicial:** 6 ADD, 20 UPDATE, 1 RECLAIM, 5 CONFLICT, 17 DELETE_CANCELLED, 27 ORPHAN, 838 UNCHANGED.

Tres correcciones al script salieron de revisar ese dry-run antes de aplicar:

| Hallazgo | Efecto sin corregir | Corrección |
|----------|--------------------|-----------|
| Cola de la suscripción **840** con target `192.169.22.170` invisible por el filtro `192.168.*` | `ADD` duplicado → error `already have such name` | `isStaticIpTarget` acepta cualquier IPv4 |
| 3 abonados (**665**, **1637**, **1842**) cambiaron de IP; su cola vieja quedaba huérfana y el `ADD` colisionaba de nombre | Cola duplicada + contadores perdidos | Acción `RETARGET` (PATCH de `target`) |
| `-p$PASSWORD` insertado en la posición equivocada del argv de `mysql` | `mysql falló al leer suscripciones` | `MYSQL_PWD` por entorno (`buildMysqlArgs` + test) |

**Apply 1 (`--only ADD,UPDATE,RETARGET,RECLAIM`):** 2 ADD, 20 PATCH, 3 RETARGET, 1 RECLAIM, **0 errores**. De los 20 PATCH, 3 eran velocidad real distinta del plan (**796** y **1270** de 400M a 200M, **2031** de 20M a 200M); el resto, nombre desactualizado (NAP recién poblado, cambio de titular o de lugar).

**Apply 2 (`--delete-cancelled --only DELETE_CANCELLED`):** 14 de 17 borradas, 0 errores.

**Las 3 no borradas tienen tráfico vivo** (delta de bytes en 25 s), y borrar su cola las dejaría **sin límite de velocidad**:

| Cola | IP | Suscripción CANCELLED | Delta 25 s |
|------|----|----------------------|-----------|
| `*113` | `192.168.30.139` | 833 TATIANA LORENA CORTEZ VILLANUEVA | 16,4 MB |
| `*176` | `192.168.30.159` | 860 ZACARIAS MORE COVE | 5,9 MB |
| `*85` | `192.168.30.106` | 1522 PEDRO DANIEL JARA ZORRILLA | 462 B |

Esto es el hallazgo «CANCELLED con tráfico» de la auditoría: se resuelve **cortando** (address-list / `processAndLogCancelledSubscriptions`), no borrando colas.

**Estado final:** 0 ADD, 0 UPDATE, 0 RETARGET, 0 RECLAIM. Quedan 3 `DELETE_CANCELLED` (las de arriba), 5 `CONFLICT` y 24 `ORPHAN`.

**CONFLICT pendientes** (dos suscripciones compitiendo por la misma IP; requiere decidir en BD):

| IP | Suscripción en BD | Dueño de la cola |
|----|-------------------|------------------|
| `192.168.30.36` | 629 | 1629 |
| `192.168.26.199` | 657 | 1302 |
| `192.168.210.75` | 1287 | 1612 |
| `192.168.30.51` | 1628 | 1735 |
| `192.168.30.250` | 1895 | 2344 |

**ORPHAN (24)**: se dejan a propósito. Incluyen clientes sin `id:` en el nombre (`BASE GIGAFIBER`, `FUNDO MONSERRAT`, `FUNDO VILLA VICTORIA`, `OFICINA GIGA`), colas de laboratorio (`EEEFIBER PRUEBA`, `LabCore Fiber`) y colas viejas por cambio de IP.
