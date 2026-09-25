# Collation utf8mb4_unicode_ci

Los esquemas satélite (ACS, OLT Gateway, Traffic) y `ispadmin_staging` usan `utf8mb4_unicode_ci`. La URL JDBC lleva `connectionCollation=utf8mb4_unicode_ci`. El dialecto `UnicodeCiMySQLDialect` hace que `CAST(... AS string)` salga como `cast(? as char) collate utf8mb4_unicode_ci`. Sin eso MySQL 8 asigna `utf8mb4_0900_ai_ci` al cast y el `LIKE` contra columnas `unicode_ci` responde 500; el Core lo muestra como 502 `UPSTREAM_FAILURE`.

`ispadmin` de producción sigue en `utf8mb4_0900_ai_ci` (unas 16 GB, sobre todo observabilidad y tráfico). No se convierte en el arranque: reescribiría esas tablas con el servicio caído. Su URL JDBC no fuerza `unicode_ci`.
