# OLT Gateway — manejo cuando la OLT no está disponible

## Problema

Con `olt.gateway.mock.enabled=false` y la OLT real inalcanzable (p. ej. `10.11.104.2:22` sin VPN), el worker SSH martilleaba reconexiones y el sync de inventario devolvía `empty_snapshot` en lugar de un estado explícito de indisponibilidad. Eso impedía activar el circuit breaker y competía con otros polls (NetDiag REST).

## Solución

1. **`OltReachabilityTracker`** — tras `failure-threshold` fallos consecutivos SSH, entra en modo degradado `backoff-ms` y los jobs `INVENTORY`, `SIGNAL_POLL` y `KEEPALIVE` responden al instante con `skippedReason=olt_unreachable` (sin abrir SSH). `WRITE` y `ADHOC` siguen intentando.

2. **`OltGponTopologyDiscovery`** — si todos los probes de slot fallan por conectividad (`OltUnreachableException` / "Unable to reach OLT"), lanza excepción en lugar de devolver lista vacía. Así el bus registra fallo y no confunde "OLT caída" con "OLT sin placas GPON".

3. **`OltCliBus.execute`** — desempaqueta `ExecutionException` para propagar la causa real (`OltUnreachableException`).

4. **`OltInventorySyncService`** — mapea `OltUnreachableException` y `CliBusBusyException(olt_unreachable)` a `skippedReason=olt_unreachable`.

## Config dev (`application-dev.properties`)

```properties
olt.gateway.mock.enabled=${OLT_GATEWAY_MOCK_ENABLED:false}
olt.gateway.username=oltadmin
olt.gateway.password=${OLT_GATEWAY_PASSWORD:GigaOlt2026}
olt.gateway.reachability.failure-threshold=2
olt.gateway.reachability.backoff-ms=120000
```

Credenciales también en `application-local.properties` (perfil `local`, no versionado). `run-dev.sh` usa `dev,local` sin exports de password.

## Comportamiento esperado en logs

- Primer sync (OLT caída): probes rápidos → `Inventory sync skipped: olt_unreachable`
- Tras 2 fallos: `Signal poll skipped: olt_unreachable` sin tormenta SSH
- Cuando la OLT vuelva: el siguiente job exitoso resetea el tracker

## Tests

- `OltReachabilityTrackerTest`
- `OltCliBusTest` (`background jobs skip fast when OLT is degraded`, unwrap de excepción)
- `OltGponTopologyDiscoveryUnreachableTest`
- `OltInventorySyncServiceTest` (skip `olt_unreachable`)
