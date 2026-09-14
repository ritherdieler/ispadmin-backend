# OLT Gateway — OltCliBus (dispatcher de 2 sesiones)

## Objetivo

No saturar el Reenter de la MA5608T (3 VTY; el Gateway usa 2; la 3.ª queda para operador). Un solo dispatcher con cola de prioridad estricta, mutex de una escritura y sesión reservada para el técnico.

## Sesiones

| Sesión | `CliLane` | Qué corre |
|--------|-----------|-----------|
| Reservada | `INTERACTIVE` | `AUTHORIZE` (P1), `UNCONFIGURED` (P2), `WRITE` solo si el fondo está ocupado |
| Fondo | `BACKGROUND` | Lecturas largas (`INVENTORY`, `SIGNAL_POLL`, `ALARM_POLL`, `AUTOFIND_POLL`), `ADHOC`, `WRITE` preferido, y `AUTHORIZE`/`UNCONFIGURED` si la reservada está ocupada |

`pool-size=1` degrada a la sesión reservada: todo se serializa ahí, mismas prioridades.

## Prioridad (estricta, sin envejecimiento, sin cancelar el job en curso)

| P | Tipo | Quién |
|-----|------|-------|
| 1 | `AUTHORIZE` | `OltGatewayCommandService.authorize` |
| 2 | `UNCONFIGURED` | `GET` de ONUs no autorizadas (`display ont autofind all`) |
| 3 | resto | delete/move/reboot, health, inventario, señal, alarmas, keepalive |

FIFO dentro de la misma prioridad. Un authorize en cola pasa delante en el **siguiente límite de job**.

## Mutex de config

Como máximo **una** escritura CLI a la vez (`AUTHORIZE` o `WRITE`). Una lectura puede ir en paralelo en la otra sesión.

## Singleflight P2

Dos `GET` de no autorizadas a la vez: un solo SSH. La segunda espera y recibe el mismo resultado.

## API

- `submit(type, block): CompletableFuture<CliBusResult<T>>`
- `execute(type, block): CliBusResult<T>` (bloqueante)
- Resultado: `Ok(value)` o `Skipped(reason)` (`already_running` / `already_queued` solo para syncs P3; `UNCONFIGURED` no se descarta, se comparte)
- Status: `queueDepth()`, `busyJobType()`, `sessionCount()` (=2, o 1 si `pool-size=1`)
- Reentrante en el worker: nested `execute`/`run` no reencola

## Consumidores

| Productor | Job type |
|-----------|----------|
| `OltGatewayCommandService.authorize` | `AUTHORIZE` |
| `OltGatewayQueryService.autofindParsed` | `UNCONFIGURED` |
| `OltGatewayCommandService` (move/delete/reboot) | `WRITE` |
| `OltGatewayQueryService` (health/optical/detail/oltInfo/…) | `ADHOC` |
| `ParallelOnuInventoryReader` | `INVENTORY` |
| `OltSignalPollService` | `SIGNAL_POLL` |
| `OltAlarmCliService` | `ALARM_POLL` |
| Keepalive del bus | `KEEPALIVE` (solo si esa sesión está idle) |

El poll programado de autofind (`OltAutofindRefreshScheduler`) está **apagado** (`olt.gateway.autofind.enabled=false`). El único `display ont autofind all` vivo es el GET a demanda.

## Config

- `olt.gateway.session.pool-size=2` (tope de código 2)
- Keepalive de sesión Huawei desactivado dentro del bus; el bus programa el ping por sesión idle
- Heartbeat de transporte MINA (`keepalive@sshd.apache.org`) **desactivado** en `OltSshClient`

## Keepalive: dos capas

| Capa | Mecanismo | Durante un job en esa sesión |
|------|-----------|------------------------------|
| Transporte SSH (MINA) | `HEARTBEAT_INTERVAL` | **Off** (0) |
| Aplicación CLI | `display clock` vía bus si esa sesión está idle | No corre con job activo |

`HuaweiCliSession.readUntil` aborta si el canal/sesión SSH se cierra (reconexión vía `execute` en lugar de esperar el timeout completo).
