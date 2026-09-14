# NetDiag — correcciones falsos positivos (2026-08-01)

## Cambios

| Área | Problema | Fix |
|------|----------|-----|
| `HuaweiOltAlarmParser` | Substring `los` dentro de **loss** (alarma BITS LOL) → `PON_PORT_DOWN` | `containsGponPortLos()` con `\blos\b` o `(los)` |
| `MikrotikOpticalAdapter` | Módulo presente sin lecturas → `opticalDdmAvailable=true` | Default `false` si no hay rx/tx/temp ni copper/DAC |
| `AlertSignalExtractor` | `OPTICAL_TX_FAULT` con tx null y sin DDM | Ignorar óptica sin lecturas |
| `AlertEvaluator` + `NetDiagPollService` | Incidentes poll zombi | `reconcilePollSignals()` cierra OPEN de poll si el dedupKey no está en señales activas |

## Pruebas

```bash
./mvnw test -Dtest=HuaweiOltAlarmParserTest,AlertSignalExtractorRos7Test,AlertEvaluatorTest,NetDiagPollServiceTest,MikrotikOpticalAdapterTest,AlertEvaluatorResolveClearTest
```

## Post-deploy

- Resolver manualmente incidentes E2E y `PON_PORT_DOWN` #230 (BITS) si siguen OPEN.
- Tras deploy, el poll de MK1 debería cerrar `OPTICAL_TX_FAULT`, `POLL_STALE` y `TIMEOUT` cuando el snapshot esté sano.
