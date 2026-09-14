# Agentes — pruebas largas sin bloquear el turno (2026-09-05)

## Decisión

Tras el e2e Espresso staging FIBER (`ZTEGDC47BFFD`), se fijó que el agente **no debe** quedarse en `AwaitShell`/polling mientras corre una prueba larga. Debe usar la **notificación de fin de job en background** de Cursor.

## Dónde quedó la regla

| Archivo | Qué |
|---------|-----|
| `gigafiber/AGENTS.md` | Regla de plataforma (obligatoria en toda prueba larga) |
| `ispadmin-backend/AGENTS.md` | Remisión + smoke/VPS/Maven |
| `IpsAdmin-android app/AGENTS.md` | Remisión + Espresso / `e2e_*.sh` |
| `.agent-docs/pruebas-camino-mas-corto.md` | Checklist operativo |
| `IpsAdmin-android app/scripts/e2e_register_fiber_staging_espresso.sh` | Comentario en cabecera |

## Flujo canónico

```text
lanzar prueba (background)
        │
        ▼
avisar al usuario → cerrar turno
        │
        ▼ (notificación Cursor: job finished)
leer log → PASS/FAIL (+ WiFi si COMPLETE)
```

Aplica en **cada** prueba: unit suites largas, live OLT, gateway scripts, Espresso, ping MK, cleanup, deploy.
