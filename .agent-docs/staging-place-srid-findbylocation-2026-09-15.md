# Staging `place.area` SRID — findByLocation 404 (2026-09-15)

El e2e Android FIBER contra staging falló en el precheck geo: `GET /place/findByLocation?latitude=-11.2156&longitude=-77.4107` → 404. Había 24 places (incluido `9 de octubre`) y NAP `NO-001`, pero ningún polígono contenía el punto.

## Causa

`scripts/sql/staging-e2e-registration-catalog.sql` copiaba `place.area` con `ST_GeomFromText(ST_AsText(p.area), 4326)` para cumplir la columna Hibernate `POLYGON SRID 4326`.

En MySQL 8 ese WKT se interpreta como lat/lon. Prod guarda el polígono en SRID 0 (cartesian lon/lat). Tras el rewrite, `ST_Contains(ST_SRID(area, 4326), POINT(lon lat, 4326))` da 0 incluso en el centroide.

| Schema | SRID `place.id=1` | Contiene lab `-77.4107 -11.2156` |
|--------|-------------------|----------------------------------|
| `ispadmin` (prod) | 0 | sí |
| `ispadmin_staging` (tras rewrite WKT) | 4326 | no |
| `ispadmin_staging` (tras `ST_SRID(p.area, 4326)`) | 4326 | sí |

## Fix

Seed: `ST_SRID(p.area, 4326)` (cambia SRID, no coordenadas). Aplicado en vivo:

```sql
UPDATE ispadmin_staging.place s
INNER JOIN ispadmin.place p ON p.id = s.id
SET s.area = CASE WHEN p.area IS NULL THEN NULL ELSE ST_SRID(p.area, 4326) END;
```

Verificado: `findByLocation` → 200 `9 de octubre`; `/napbox/near` incluye `NO-001`.

## Relacionado

- [staging-e2e-fixtures.md](./staging-e2e-fixtures.md)
- `scripts/sql/staging-e2e-registration-catalog.sql`
