# Pruebas: camino más corto (dev / staging)

Regla de agentes: **AGENTS.md → «Pruebas: camino más corto»**.

## Principio

Verificar con el menor ciclo que aporte evidencia real. Deploy a staging y e2e Espresso son caros; no son el primer instrumento para un cambio acotado.

## Matriz práctica

| Qué estás probando | Camino corto | Cuándo subir a staging |
|--------------------|--------------|-------------------------|
| Parser / secuencia CLI / validación de salida OLT | Unit + smoke `@Tag("live")` local (`OLT_WRITE_LIVE=true`, etc.) | Solo si el WAR en VPS difiere (bake, perfiles, writes flag) |
| Delete / authorize / move OLT vía Gateway | Mismo stack SSH que el Gateway en local (`OltGatewayCommandService` + `OltCliBus`) | Tras verde local, si hay que validar HTTP del WAR o soft-delete en MySQL staging |
| Activate / TR-069 / alta FIBER local | WAR único `:8082` + GenieACS `:7557` + **MK2** id 8; solo ONU **`lab`** (`ZTEGDC47BFFD`). Alta = `POST /subscription` en Core — [pruebas-local-gateway-acs-lab.md](./pruebas-local-gateway-acs-lab.md) | Si el fallo es bake/overlay del WAR en Tomcat |
| Cierre ACS → Core / Redis Streams | Logs + `XLEN` / consumer en VPS o compose local Redis | Cuando el fallo es de perfiles/WAR/grupos Redis en Tomcat |
| Alta FIBER Android end-to-end | Espresso staging | Cuando OLT+Gateway+ACS+flags Core ya pasaron por caminos cortos |

## Ejemplo (2026-09-04)

Se iba a redeployar staging para comprobar que `ont delete` borra en la OLT. La Mac **ya alcanza** `10.11.104.2`. El camino corto fue:

```bash
OLT_WRITE_LIVE=true ./gradlew :oltgateway:test --tests "OltGatewayDeleteLiveSmokeTest" --tests "OltGatewayCommandServiceTest"
```

Resultado: `LIVE GATEWAY DELETE OK` sin deploy. El deploy queda para cuando haga falta el WAR en Tomcat, no para descubrir el bug de CLI.

## Checklist antes de `deploy.sh --env staging`

- [ ] ¿Hay un unit test que falle sin el fix?
- [ ] ¿La dependencia (OLT, Redis local, MySQL local) es alcanzable sin VPS?
- [ ] ¿El fallo sospechado es de **código** o de **empaquetado/overlay/env en VPS**?
- [ ] Si es código puro → local primero. Si es bake/perfiles/Tomcat → staging.

## Pruebas largas: mecanismo de fin de job (agentes)

Fuente de verdad: `gigafiber/AGENTS.md` → «Pruebas largas: no bloquear el turno».

En **cada** prueba larga (incluye Espresso Android, ping MK con reintentos, cleanup duro, live OLT, deploy):

1. Background (`block_until_ms: 0`).
2. Mensaje corto al usuario.
3. Cerrar turno — **sin** `AwaitShell` ni polls.
4. Al llegar la notificación de fin de job → leer salida y reportar.

No existe (ni se pide) un callback desde la app Android: el canal es la notificación del shell en background de Cursor.