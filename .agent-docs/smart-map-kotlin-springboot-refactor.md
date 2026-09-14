# Refactor Smart Map → kotlin-springboot

Fecha: 2026-07-22  
Alcance: endpoints `/smart-map/*` del backend.

## Objetivo

Alinear el módulo Smart Map con las prácticas de `kotlin-springboot`:
controllers delgados, lógica en `@Service`, DTOs con `@Valid`, errores vía
`@ControllerAdvice`, config tipada e inmutable, y coroutines sin `runBlocking`.

## Cambios

### Dependencias
- Se agregó `spring-boot-starter-validation` al `pom.xml`.

### Paquete feature
- `smartmap/SmartMapAccessPolicy.kt`: reglas de acceso por `userType`.
- `smartmap/SmartMapIntelligenceService.kt`: CRUD de cobertura, leads y
  oportunidades (antes vivía en el controller contra repositorios).

### Controllers
- `SmartMapController` y `SmartMapIntelligenceController` sin `try/catch`,
  sin `printStackTrace`, sin `ErrorLogRepository`.
- Endpoints de routing / enrich usan `suspend` (sin `runBlocking`).
- Body requests con `@Valid`.
- Rutas y contratos JSON se mantienen.

### Validación
- `CoverageZoneRequest`, `SalesLeadMapRequest`, `CommercialOpportunityRequest`
- `CollectionVisitRequestDto`, `SmartMapCollectionRouteRecalculateRequestDto`
- `@JsonIgnoreProperties(ignoreUnknown = true)` en requests.

### Config
- `SmartMapRoutingProperties` pasó a `data class` + `@ConstructorBinding`.
- Registrada con `@EnableConfigurationProperties` en
  `SmartMapRoutingConfiguration`.

### Errores globales (`GlobalExceptionHandler`)
- `IllegalArgumentException` → 400
- `ResponseStatusException` → status correspondiente (403/404)
- `MethodArgumentNotValidException` → 400 con mapa de campos
- `SmartMapSectorValidationException` y `MapboxDirectionsException` se mantienen

## Compilación

`sh mvnw -q -DskipTests compile` → **BUILD SUCCESS**

## Pendiente (no incluido)

- Mover `SmartMapService` / DTOs / repos a un único paquete feature `smartmap`
  (el resto del proyecto sigue organizado por capas).
- Reemplazar `userType` query param por Spring Security (security está
  comentado en el `pom.xml`).
