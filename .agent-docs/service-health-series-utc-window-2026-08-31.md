# Service-health series UTC window fix — 2026-08-31

## Problema

Las muestras ópticas/ACS se persisten con `UtcInstantType` (DATETIME en reloj UTC). Las queries derivadas `findBy…ObservedAtBetween(Instant, Instant)` bindeaban el `Instant` con `hibernate.jdbc.time_zone=America/Lima`, desplazando la ventana ~5 h. En prod, `#2328` tenía 16 ópticas en 24 h UTC pero la UI mostraba vacío (simulación SQL: 16 vs 1).

## Fix

- `UtcInstantText.format(Instant)` → texto UTC `yyyy-MM-dd HH:mm:ss.SSSSSS`
- Queries nativas `observed_at >= :fromText AND observed_at <= :toText` en repos ópticos, Wi‑Fi, estados, eventos y acciones
- Extensiones `listBySubscriptionInUtcWindow` / `pageBySubscriptionInUtcWindow` en `UtcWindowQueries.kt` (no default methods en la interface: Spring Data las trata como queries derivadas)

## Piloto prod (ops)

`SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS=2310,2328` con flags ópticos/ACS ON. ACS sigue con `PARAMETERS_NOT_REFRESHED_FOR_INFORM` (trabajo aparte).

## Verificación

```bash
./mvnw -Dtest=HealthPersistenceTest,UtcInstantTextTest test
```
