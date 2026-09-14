# Deploy staging 2026-09-04 (fixes e2e 2+3)

Comando:

```bash
./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs
```

Primer intento abortó por `SubscriptionServiceIdempotencyTest` (aún esperaba `olt=PENDING`). Tras ajustar a `FAILED`, redeploy OK.

## WARs

| WAR | Contexto |
|-----|----------|
| `ispadmin-staging.war` | Core (`olt=FAILED` inmediato) |
| `ispadmin-staging-acs.war` | ACS (`ConnectionStatus=Connected` para COMPLETE) |
| `ispadmin-staging-oltgateway.war` | Gateway |
| `ispadmin-staging-traffic.war` | Traffic |

## Verificación

- Suite Maven: 1669 tests, 0 failures
- `GET /ispadmin-staging/` → HTTP 200
- Release: `1.0.3+df83610`
