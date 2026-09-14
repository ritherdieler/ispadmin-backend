# Fix: descubrimiento incompleto de boards GPON (MA5608T)

## Síntoma

`GET /api/olt-gateway/onus` (live) devolvía **144** ONUs (solo slot 0). En DB (`/onus/configured`) había **757** (slot 0 = 144, slot 1 = 613).

Logs: `Discovered 1 GPON slots: [0]` y `jobs=16`.

## Causa raíz

En MA5608T V800R015:

- `display board 0` es la vista de **frame 0**: tabla con todos los SlotID (0 H805GPFD, 1 H806GPFD, 3 H801MCUD1, 4 H801MPWD, …).
- `display board 1` (y superiores) responde `% Parameter error`.

El discovery hacía un probe por índice y `BoardParser.parse(..., expectedSlot)` solo tomaba la fila del índice pedido. En probes 1..N la salida era error → boardName vacío → se descartaba el segundo GPFD.

## Fix

1. `BoardParser.parseAll(output)`: parsea todas las filas `SlotID BoardName Status` (sin cruzar líneas con `\s`).
2. `OltGponTopologyDiscovery` / `oltInfo`: acumulan boards de cada probe con `parseAll` + dedupe por slot; clasifican GPON con `GponBoardClassifier`.
3. Fixture real: `display-board-frame-0-chassis.txt`. Tests: `BoardParserTest`, `OltGponTopologyDiscoveryTest`.

## Resultado live (post-fix)

- Boards: 0 H805GPFD, 1 H806GPFD, 3 H801MCUD1, 4 H801MPWD
- GPON slots: `[0, 1]`
- Listado masivo: **slot-all por GPON slot** con fallback **port-all** si timeout (slot 1 en Gigafiber). Cache en memoria de slots que requieren port-all.
- ONUs live: **757** (0→144, 1→613)

## Verificación

```bash
curl -s -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/olt/info | python3 -m json.tool

curl -s -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/onus | python3 -c \
  "import json,sys; from collections import Counter; d=json.load(sys.stdin); print(d['total'], Counter(i['slot'] for i in d['items']))"
```
