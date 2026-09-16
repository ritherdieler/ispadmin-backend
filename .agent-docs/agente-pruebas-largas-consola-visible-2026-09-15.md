# Agentes — pruebas largas con consola visible (2026-09-15)

## Decisión

Se **retira** la regla de 2026-09-05 (lanzar e2e en background, cerrar el turno y esperar la notificación de fin). El usuario necesita ver el progreso y la consola mientras corre la prueba.

## Regla vigente

Fuente: `gigafiber/AGENTS.md` → «Pruebas largas: consola visible».

1. Ejecutar con salida visible en el chat.
2. Mantener el turno abierto con `AwaitShell` hasta el final.
3. Reportar PASS/FAIL al terminar.

**Prohibido:** redirigir a `/tmp/*.log` y cerrar el turno sin progreso visible.

## Archivos actualizados

| Archivo | Qué |
|---------|-----|
| `gigafiber/AGENTS.md` | Regla de plataforma |
| `ispadmin-backend/AGENTS.md` | Remisión |
| `IpsAdmin-android app/AGENTS.md` | Remisión Espresso |
| `.agent-docs/pruebas-camino-mas-corto.md` | Checklist |
| `.agent-docs/pruebas-local-gateway-acs-lab.md` | POST/poll y Android local |
| `IpsAdmin-android app/scripts/e2e_*.sh` | Comentarios de cabecera |

## Histórico

La política anterior (notificación de fin de job) quedó obsoleta el 2026-09-15.
