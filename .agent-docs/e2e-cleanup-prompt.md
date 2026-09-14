# E2E Espresso: `--cleanup-mode` (ask / auto / skip)

Fecha: 2026-09-13.

Wrappers: `e2e_register_fiber_staging_espresso.sh`, `e2e_register_fiber_espresso.sh`, `e2e_register_fiber_local_espresso.sh`.

Tras imprimir WiFi (si Espresso OK), el **post-cleanup** lo decide este flag (no el TTY).

## Flag

```bash
--cleanup-mode ask|auto|skip
```

Env equivalente: `CLEANUP_MODE=ask|auto|skip`.

| Valor | Qué hace |
|-------|----------|
| `auto` (**default**) | Hard cleanup al terminar, sin preguntar. Agentes/CI. |
| `ask` | Pregunta `¿Ejecutar hard cleanup ahora? [s/N]`. `s`/`y`/`si` limpia; Enter o `n` deja la suscripción. |
| `skip` | No limpia. |

### Aliases

| Alias | Equivale a |
|-------|------------|
| `--ask-cleanup` | `--cleanup-mode ask` |
| `--auto-cleanup` / `--cleanup` | `--cleanup-mode auto` |
| `--no-cleanup` o `SKIP_POST_CLEANUP=1` | `--cleanup-mode skip` |

El precleanup (ONU libre **antes** del alta) no cambia.

## Ejemplos

Preguntar al final (inspeccionar WiFi / 360):

```bash
E2E_ONU_SN=ZTEGDC47BFFD ./scripts/e2e_register_fiber_staging_espresso.sh \
  --wifi-ssid polarwifi \
  --wifi-pass '11111111' \
  --cleanup-mode ask
```

Automático (default; no hace falta el flag):

```bash
E2E_ONU_SN=ZTEGDC47BFFD ./scripts/e2e_register_fiber_staging_espresso.sh \
  --wifi-ssid polarwifi \
  --wifi-pass '11111111' \
  --cleanup-mode auto
```

Dejar la suscripción viva:

```bash
./scripts/e2e_register_fiber_staging_espresso.sh --cleanup-mode skip
```

Runbook staging: [staging-fiber-e2e-runbook.md](./staging-fiber-e2e-runbook.md). Local: [pruebas-local-gateway-acs-lab.md](./pruebas-local-gateway-acs-lab.md).

## Pruebas

- `E2ePlaceLocationFixtureTest.staging espresso script delivers wifi credentials before cleanup`
- `E2eLocalCoreEspressoScriptTest.local core espresso script talks to Core via emulator reverse not staging or prod`
