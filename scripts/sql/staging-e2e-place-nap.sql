-- DEPRECATED: usar scripts/sql/staging-e2e-registration-catalog.sql (incluye place + plan + MK + pool).
-- Copia idempotente del catalogo geografico de prod hacia staging.
-- Las pruebas de registro FIBER deben pegar coordenadas DENTRO de un poligono
-- de place.area (GET /place/findByLocation usa ST_Contains, POINT(lon lat)).
--
-- place.id=1 "9 de octubre" columns lat=-11.2177 lon=-77.4137 estan DENTRO del poligono, pero
-- el e2e lab FIBER con NAP NO-001 DEBE usar lat=-11.2156 lon=-77.4107
-- (coords de nap_box NO-001; ver staging-e2e-registration-catalog.sql y runbook).
-- No usar el centro del envelope: en san bosco y la merced queda fuera.
-- No intercambiar lat/lon (findByLocation 404).
-- Sin poligono (no sirven para findByLocation): san miguel (10), DESCONOCIDO (150).
--
-- Aplicar:
--   mysql < scripts/sql/staging-e2e-place-nap.sql

SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO ispadmin_staging.place
SELECT *
FROM ispadmin.place p
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.place s WHERE s.id = p.id
);

UPDATE ispadmin_staging.place s
INNER JOIN ispadmin.place p ON p.id = s.id
SET
  s.latitude = p.latitude,
  s.longitude = p.longitude,
  s.name = p.name,
  s.area = p.area;

INSERT INTO ispadmin_staging.mufa
SELECT *
FROM ispadmin.mufa m
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.mufa s WHERE s.id = m.id
);

INSERT INTO ispadmin_staging.nap_box
SELECT *
FROM ispadmin.nap_box n
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.nap_box s WHERE s.id = n.id
);

UPDATE ispadmin_staging.nap_box s
INNER JOIN ispadmin.nap_box n ON n.id = s.id
SET
  s.address = n.address,
  s.code = n.code,
  s.latitude = n.latitude,
  s.longitude = n.longitude,
  s.ports_number = n.ports_number,
  s.mufa_id = n.mufa_id,
  s.olt_board = n.olt_board,
  s.olt_id = n.olt_id,
  s.olt_port = n.olt_port,
  s.place_id = n.place_id;

SET FOREIGN_KEY_CHECKS = 1;

SELECT COUNT(*) AS staging_places FROM ispadmin_staging.place;
SELECT COUNT(*) AS staging_places_with_area FROM ispadmin_staging.place WHERE area IS NOT NULL;
SELECT COUNT(*) AS staging_nap_boxes FROM ispadmin_staging.nap_box;

SELECT p.id, p.name
FROM ispadmin_staging.place p
WHERE p.area IS NOT NULL
  AND ST_Contains(
    ST_SRID(p.area, 4326),
    ST_GeomFromText('POINT(-77.4107 -11.2156)', 4326)
  )
LIMIT 1;
