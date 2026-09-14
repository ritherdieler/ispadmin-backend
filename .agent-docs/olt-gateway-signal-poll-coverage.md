# Signal poll — cobertura óptica (MA5608T)

## Comando bulk único

El `signal_poll` **solo** usa el bloque más grande disponible por puerto:

```text
interface gpon 0/{slot}
display ont optical-info {port} all
```

No hay fallback `display ont optical-info {port} {onuIndex}` en el poll programado.
La consulta on-demand por ONU (`OltGatewayQueryService`) sigue existiendo solo para la API puntual.

## Reintento bulk (sin per-ONU)

Si el primer `all` devuelve parse vacío o falla por timeout/CLI, se reentra `interface gpon 0/{slot}` y se repite **una vez** el mismo comando `all`.
Si el segundo intento también falla, ese puerto queda sin actualizar en ese ciclo.

## Gap histórico 237 → 720 (2026-07-17)

### Causa raíz

1. Timeout/CLI vacía en un puerto del slot 1 dejaba la sesión fuera de `interface gpon`.
2. Puertos siguientes devolvían ~128 chars sin tabla (`empty parse`).
3. Resultado inicial: solo ~237 ONUs actualizadas (board 0 + board1 ports 0–1).

### Fix aplicado

Reintento bulk tras reentrar `interface gpon` (sin comandos por ONU).

### Verificación live (2026-07-18, bulk-only, sin per-ONU)

| Métrica | Antes | Con reintento bulk | **Actual (bulk-only)** |
|---------|------:|-------------------:|-----------------------:|
| `onusUpdated` | 237 | 720 | **720** (poll 1) |
| Online con `oltRxDbm` | 236 | 718 / 721 | **50/50** en página 0 de `/onus/configured` |
| Duración poll | ~227 s | ~508 s | **~124 s** |
| `portsPolled` | — | — | **32** (2 slots × 16 puertos) |

Poll 2 inmediato: `onusUpdated=678`, `durationMs=123317` — las lecturas ópticas fluctúan entre ciclos, el skip de writes solo aplica cuando rx/tx/temp/categoría son idénticos.

```bash
curl -s -w "\nTIME:%{time_total}s\n" -X POST \
  -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/admin/sync/signal
```

### Contención bus CLI (2026-07-18)

| Escenario | Resultado |
|-----------|-----------|
| Signal + inventory en paralelo | Ambos completan (~120–136 s); se serializan en el bus, no hay `skippedReason` |
| Dos inventory en paralelo | Segundo: `skippedReason=sync_already_running` |
| `GET /onus/configured?page=0&size=50` | **0.15 s**, `totalElements=758` |

Ejemplo (WALTHER / SN …DC47DF15): `onuRxDbm=-19.78`, `oltRxDbm=-24.69`, `signalCategory=good`.

Las ONUs offline o puertos sin tabla óptica en ese momento quedan sin lectura hasta el siguiente ciclo bulk.
