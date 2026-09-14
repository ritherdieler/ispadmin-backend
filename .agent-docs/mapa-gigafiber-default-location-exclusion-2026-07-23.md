# Exclusión coordenada por defecto en rutas de cobranza

Fecha: 2026-07-23

## Problema

502 suscripciones compartían la coordenada `-11.233708, -77.376278` (valor por defecto al registrar sin GPS real). Pasaba el filtro `location != (0,0)` y entraba en rutas de cobranza.

## Solución

- `ClientLocationRules` (`wispadmin/smartmap/ClientLocationRules.kt`): `hasRealClientGps()`, `isDefaultClientCoordinate()`.
- SQL de barrido/polígono: excluye la coordenada por defecto en las 4 queries elegibles.
- `SmartMapService`: contador `ubicacion_no_real` en `excludedReasons`; preview `omittedNonRealLocationCount`.
- Front Mapa Gigafiber: burbuja `MapaGigafiberOmissionBubble` (ver `ispadmin-backoffice/.agent-docs/mapa-gigafiber-ui.md`).

## API

| Campo / clave | Dónde |
|---------------|-------|
| `excludedReasons.ubicacion_no_real` | `GET /smart-map/collection-route`, `collection-sweep-route` |
| `omittedNonRealLocationCount` | `GET /smart-map/collection-pending` |

## Verificación

```bash
./mvnw test -Dtest=ClientLocationRulesTest
```
