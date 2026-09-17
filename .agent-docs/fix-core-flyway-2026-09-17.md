# Fix coreFlyway (2026-09-17)

Bean `coreFlyway` + `ApplyCoreFlywayUseCase` (Result + `runCatching`). Baseline 38, `classpath:db/migration`. EMF `@DependsOn("coreFlyway")`.

V55: `error_log.error` → TEXT. `Place.area` mapping `geometry` (Hibernate Spatial vs `POLYGON SRID 4326`).

Ensayo Spring validate contra `ispadmin_flyway_clone`: **PASS** (V39–V55 + Hibernate validate). `ispadmin` vivo no se tocó (`83de8fe` / 200).

No deploy prod. No reintento del WAR `56991bd`. Pin siguiente: `f45dcc0`.

Store: `docs/fix-core-flyway.md`.
