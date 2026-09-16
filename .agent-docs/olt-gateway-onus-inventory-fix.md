# Fix: GET /onus — timeout y lista vacía

> 2026-09-16: el path SSH de inventario (`ParallelOnuInventoryReader`) se eliminó. Inventario live = SNMP. Ver `remove-ssh-fallbacks-2026-09-16.md`.

## Síntoma

`GET /api/olt-gateway/onus` devolvía HTTP 200 con `{"items":[],"total":0}` tras ~180 s.

Log: `Inventory job failed command=display ont info 0 all: CLI command timed out after 180000ms`

## Causa

1. `InventoryJobPlanner` usaba un solo `display ont info 0 all` (FrameAll) que excede el timeout en la OLT live (~757 ONUs).
2. `ParallelOnuInventoryReader` tragaba la excepción y devolvía lista vacía sin error HTTP.

## Fix (2026-07-18)

1. **Estrategia por slot:** `display ont info 0 <slot> all` por cada GPON slot descubierto.
2. **Fallback port-all:** si slot-all falla o devuelve vacío → `display ont info 0 <slot> <port> all` × N puertos (16 en GPFD).
3. **Probe corto:** `slot-all-probe-timeout-ms=15000` (no esperar 180 s en slots grandes).
4. **Cache port-all:** slots que requieren fallback se recuerdan en memoria (no repiten probe).
5. **Errores visibles:** si todos los jobs fallan → propaga `OltCommandTimeoutException` → HTTP 504.

## Resultado live (Gigafiber MA5608T)

| Métrica | Antes | Después |
|---------|-------|---------|
| Total ONUs | 0 | ~758 |
| Tiempo (1ª petición) | ~182 s | ~85 s |
| Tiempo (cache port-all) | — | ~25 s |
| Slot 0 | — | 144 (slot-all) |
| Slot 1 | — | 614 (port-all) |

## Tests

- `InventoryJobPlannerTest` — planifica SlotAll por slot
- `ParallelOnuInventoryReaderTest` — slot-all, port fallback, propagación timeout

## Verificación

```bash
curl -s -w "\nTIME:%{time_total}s\n" -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/onus | python3 -c \
  "import sys,json; from collections import Counter; d=json.load(sys.stdin); print(d['total'], dict(Counter(i['slot'] for i in d['items'])))"
```

---

## Optimización persistencia sync (2026-07-18)

### Síntoma

`POST /admin/sync/inventory` tardaba ~39 s con ~758 ONUs ya sincronizadas: ~15 s lectura OLT + ~24 s escritura MySQL.

### Causa

Patrón N+1 en `OltInventorySyncService`:

- `findById` de status por cada ONU (~758 queries)
- `save(onu)` incondicional aunque no cambiara nada
- `save(status)` siempre (actualizaba `polledAt` aunque `runState`/`matchState` fueran iguales)

≈ 2270 round-trips por sync completo.

### Fix

1. **Preload:** `findByOlt_IdWithStatus` con `@EntityGraph(status)` — una query con join.
2. **Skip writes:** no persiste onu ni status si snapshot y entidad son iguales.
3. **Batch:** acumula cambios en `PendingWrites` y hace `saveAll` al final (onus, statuses, audits).
4. **Transacción acotada:** `TransactionTemplate` envuelve solo `persistSnapshot` (SSH fuera de TX).

Misma estrategia aplicada a `OltSignalPollService.applyOpticalUpdates`:

- `findByOlt_IdWithStatus` + status precargado en memoria
- skip si rx/tx/olt/temp/categoría no cambiaron
- `saveAll` batch de statuses

### Resultado live (2026-07-18, Gigafiber MA5608T)

| Escenario | Antes | Después |
|-----------|-------|---------|
| Sync 758 unchanged | ~39 s | **~14.8 s** |
| Writes BD unchanged | ~2270 ops | 1 SELECT + 0 writes |
| Sync con 2 updates menores | — | ~34.9 s (15 s OLT + writes puntuales) |

Medición:

```text
Sync 1: unchanged=756, updated=2, durationMs=34784, TIME=34.9s
Sync 2: unchanged=758, updated=0, durationMs=14730, TIME=14.8s
Sync 3: unchanged=758, updated=0, durationMs=~15s (confirmación)
```

### Tests

- `OltInventorySyncServiceTest` — unchanged, bulk 100, status-only update
- `OltSignalPollServiceTest` — optical sin cambios, batch sin findById

### Verificación sync

```bash
curl -s -w "\nTIME:%{time_total}s\n" -X POST \
  -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/admin/sync/inventory
```
