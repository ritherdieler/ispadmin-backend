# Catálogo `place` para e2e staging (2026-09-01)

`GET /place/findByLocation` resuelve el sector con `ST_Contains(place.area, POINT(longitude latitude))`. En staging el catálogo nació vacío; hay que **copiar el contenido de prod**, no inventar un punto.

## Seed

```bash
mysql < scripts/sql/staging-e2e-place-nap.sql
```

Idempotente: inserta filas que falten y actualiza `name` / `latitude` / `longitude` / `area` desde `ispadmin.place`. También copia `mufa` y `nap_box` (FK de las cajas).

Aplicado 2026-09-01:

| Tabla staging | Filas |
|---------------|-------|
| `place` | 24 (22 con polígono) |
| `mufa` | 22 |
| `nap_box` | 162 |

Verificación:

```bash
curl -sS -G -H "Authorization: Bearer $TOKEN" \
  "https://api.gigafiberperu.cloud/ispadmin-staging/place/findByLocation" \
  --data-urlencode "latitude=-11.2177" \
  --data-urlencode "longitude=-77.4137"
# status 200, name = 9 de octubre
```

## Cómo elegir coordenadas

Usar `place.latitude` y `place.longitude` de una fila con `area IS NOT NULL`. Esas columnas están **dentro** de los 22 polígonos (comprobado en prod).

No usar el centro del envelope: queda fuera en `san bosco` (17) y `la merced` (22).

Sin polígono (findByLocation no las resuelve): `san miguel` (10), `DESCONOCIDO` (150).

### Fixture por defecto (app Android)

| Campo | Valor |
|-------|--------|
| id | 1 |
| name | `9 de octubre` |
| latitude | `-11.2177` |
| longitude | `-77.4137` |

Cualquier otro lugar con `area` sirve si se pegan **sus** lat/lon y el `E2E_PLACE` coincide con el nombre resuelto.

Detalle Android: `IpsAdmin-android app/.agent-docs/e2e-register-fiber-staging-lab-onu-2026-09-01.md`.
