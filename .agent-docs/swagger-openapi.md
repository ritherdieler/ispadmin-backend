# Swagger / OpenAPI (SpringDoc)

Documentación interactiva de la API con **SpringDoc OpenAPI UI 1.8.0** (compatible con Spring Boot 2.7.x).

## URLs (local)

Context-path: `/ispadmin` · puerto: `8080`

| Recurso | URL |
|---------|-----|
| Swagger UI | http://localhost:8080/ispadmin/swagger-ui.html |
| Swagger UI (index) | http://localhost:8080/ispadmin/swagger-ui/index.html |
| OpenAPI JSON (todo lo escaneado) | http://localhost:8080/ispadmin/v3/api-docs |
| OpenAPI JSON (grupo OLT Gateway) | http://localhost:8080/ispadmin/v3/api-docs/olt-gateway |

La UI de Swagger **no** pide Bearer ni API key para abrirse. Al ejecutar endpoints del OLT Gateway desde la UI, usar el esquema `OltGatewayApiKey` (`X-Olt-Gateway-Key`).

## Auth

`PlatformAuthFilter` excluye:

- `/swagger-ui/**`, `/swagger-ui.html`
- `/v3/api-docs`, `/v3/api-docs/**`
- `/webjars/**` (assets de Swagger UI)

Los endpoints de negocio siguen con su auth habitual (Bearer plataforma u `X-Olt-Gateway-Key` en el gateway).

## Configuración

- Bean: `com.dscorp.wispadmin.wispadmin.config.OpenApiConfig`
- Properties: `springdoc.*` en `application.properties`
- Escaneo actual: paquete `com.dscorp.wispadmin.oltgateway` y paths `/api/olt-gateway/**`
- Grupo SpringDoc: `olt-gateway`

## Cómo documentar un endpoint nuevo

1. Anotar el controller con `@Tag`, `@Operation`, `@ApiResponse` (y `@SecurityRequirement` si aplica).
2. Si el endpoint está fuera de `oltgateway`, ampliar `springdoc.packages-to-scan` / `paths-to-match` o añadir un `GroupedOpenApi` en `OpenApiConfig`.
3. Verificar en Swagger UI o con `GET /ispadmin/v3/api-docs`.

## Tests

```bash
./mvnw -Dtest=PlatformAuthFilterSwaggerTest,OpenApiConfigTest,OpenApiDocsTest test
```

## Dependencia

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-ui</artifactId>
  <version>1.8.0</version>
</dependency>
```
