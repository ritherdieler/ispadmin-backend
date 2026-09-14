# NetDiag — validación de tests (2026-07-26)

## Resultados locales

| Suite | Comando | Resultado |
|-------|---------|-----------|
| Backend completo | `./mvnw test` | 410 tests, 0 failures |
| NetDiag + routeros | `./mvnw test -Dtest='*NetDiag*,*RouterOs*,*Legrange*,*MikroTikConnection*'` | 83 tests OK |
| Backoffice completo | `npm test -- --run` | 186 tests OK |
| NOC frontend | `npm test -- --run src/components/noc/ ...` | 21 tests OK |

## Live MK1 (pendiente)

Desde esta workstation: puertos **8728** y **443** cerrados (allowlist VPS), sin `ROUTEROS_MK1_*`.

Ejecutar en el VPS allowlisteado:

```bash
export ROUTEROS_MK1_HOST=38.224.231.2
export ROUTEROS_MK1_USER=...
export ROUTEROS_MK1_PASSWORD=...
export NETDIAG_API_KEY=dev-netdiag-key
./scripts/netdiag-smoke-vps.sh
```

Prerrequisitos ops: `www-ssl` en MK1, truststore backend, `net.diag.enabled=true`, seed `.agent-docs/netdiag-mikrotik-seed.rsc`.

## Fix aplicado en suite completa

`CollectionVisitServiceTest`: stub con valores exactos en lugar de `eq(listOf(...))` (Mockito + Kotlin devolvía null y contaminaba otros tests).
