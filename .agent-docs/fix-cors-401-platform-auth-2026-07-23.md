# Fix CORS en 401 de PlatformAuthFilter (2026-07-23)

## Síntoma (prod)

Backoffice (`https://backoffice.gigafiberperu.cloud`) reportaba:

```
Access to XMLHttpRequest at
'https://api.gigafiberperu.cloud/ispadmin/smart-map/collection-places?userType=ADMIN'
from origin 'https://backoffice.gigafiberperu.cloud' has been blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

## Reproducción curl (antes del fix)

| Request | Status | `Access-Control-Allow-Origin` |
|---------|--------|-------------------------------|
| `OPTIONS .../smart-map/collection-places` + Origin backoffice | **200** | presente |
| `GET .../smart-map/collection-places` sin Bearer + Origin | **401** | **ausente** |
| `GET .../smart-map/summary` sin Bearer + Origin | **401** | **ausente** |
| `GET .../actuator/health` + Origin (ruta pública) | **200** | presente |

El preflight pasaba; el GET autenticado fallido (token ausente/inválido/expirado) devolvía 401 **sin** CORS. El navegador enmascara el 401 como error CORS.

No era un bug exclusivo de `collection-places`: cualquier ruta protegida sin token válido mostraba el mismo patrón.

## Causa raíz

1. `PlatformAuthFilter` (`@Order(HIGHEST_PRECEDENCE + 18)`) responde 401 y **no** continúa la cadena de filtros.
2. En Spring Framework 5.3 (Boot 2.7), `CorsFilter` **ya no implementa** `Ordered`. El `@Bean CorsFilter` se registraba con orden por defecto `LOWEST_PRECEDENCE`.
3. Orden efectivo: auth corta → `CorsFilter` nunca corre en ese request → sin `Access-Control-Allow-Origin`.
4. `OPTIONS` y rutas públicas sí llegaban al `CorsFilter` / DispatcherServlet → CORS OK (engañaba el diagnóstico del preflight).

No era nginx ni un mapping faltante de smart-map: el endpoint existe; el fallo visible era CORS por orden de filtros + 401.

## Fix

`CorsConfig.kt`:

- Expone `CorsConfigurationSource` como `@Bean`.
- Registra `CorsFilter` vía `FilterRegistrationBean` con `order = Ordered.HIGHEST_PRECEDENCE` (antes de `PlatformAuthFilter` y demás auth filters).

Así el procesador CORS escribe headers **antes** de que el auth short-circuitée.

## Tests

`CorsConfigTest`:

- Orden del registration = `HIGHEST_PRECEDENCE`.
- Cadena `CorsFilter` → `PlatformAuthFilter` en `GET /smart-map/collection-places` con Origin backoffice → 401 **con** `Access-Control-Allow-Origin`.

```bash
./mvnw -Dtest=CorsConfigTest test
```

## Deploy / verificación

Tras merge a `develop` y `./scripts/deploy.sh --deploy`:

```bash
curl -sS -D - -o /dev/null \
  -X GET 'https://api.gigafiberperu.cloud/ispadmin/smart-map/collection-places?userType=ADMIN' \
  -H 'Origin: https://backoffice.gigafiberperu.cloud'
```

Esperado: status **401** (sin token) **y** header `Access-Control-Allow-Origin: https://backoffice.gigafiberperu.cloud`.

Con Bearer válido: 200/403 según política de deuda, también con CORS.

## Front

No requiere redeploy del backoffice: el origen ya estaba permitido; faltaba el header en respuestas 401 del filtro.
