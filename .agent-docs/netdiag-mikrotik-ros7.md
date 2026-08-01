# NetDiag Fase 1.5 — RouterOS 7 avanzado (MK1)

> **Última actualización:** 2026-07-26  
> Hub ROS7: [routeros-7-gigafiber-cores.md](./routeros-7-gigafiber-cores.md) · Seed: [netdiag-mikrotik-seed.rsc](./netdiag-mikrotik-seed.rsc) · Runbooks: [netdiag-runbooks.md](./netdiag-runbooks.md)

## Objetivo

Ampliar NetDiag con probes push/pull ROS7 sin aplicar configuración automáticamente a routers de producción:

| Componente | Clase | Reason codes |
|---|---|---|
| Netwatch poll | `MikrotikNetwatchAdapter` | `UPSTREAM_PROBE_FAIL` |
| SFP DOM (troncales) | `MikrotikOpticalAdapter` | `OPTICAL_RX_LOW`, `OPTICAL_TX_FAULT` |
| SNMP traps | `NetDiagSnmpTrapIngestService` + UDP opcional | `SNMP_TRAP_*` |
| Syslog | `SyslogIngestAdapter` + UDP opcional | `LOOP_PROTECT_TRIGGERED`, `LINK_FLAP`, `HIGH_TEMPERATURE`, `PPP_MASS_DISCONNECT` |

## Prerrequisitos operativos (checklist)

Antes de activar Fase 1.5 contra MK1 (`38.224.231.2`):

1. **www-ssl / REST**
   - `/ip service` → `www-ssl` enabled, puerto 443
   - Truststore backend (`router.os.client.rest.*`) importa el cert del router
   - Credenciales solo vía `network_device` / `NetDiagDeviceDirectoryPort`

2. **Netwatch seed (manual)**
   - Aplicar a mano el script documentado en [netdiag-mikrotik-seed.rsc](./netdiag-mikrotik-seed.rsc)
   - **No** auto-aplicar desde el backend a prod
   - Probes mínimos: HTTP GET + DNS hacia resolvers públicos (upstream)

3. **Optical / troncales**
   - En `net_diag_target.monitor_config` listar SFPs a monitorear, p.ej. `opticalInterfaces: ["sfp-sfpplus1","sfp-sfpplus2"]`
   - Umbrales: `net.diag.optical.rx-low-dbm` (default `-14.0`), `net.diag.optical.tx-fault-dbm` (default `-40.0`)

4. **SNMP traps**
   - En el router: habilitar traps (`interfaces`, `start-trap`, `temp-exception`) hacia el VPS
   - Opción A (recomendada en lab): relay HTTP → `POST /api/netdiag/traps/ingest`
   - Opción B: `net.diag.snmp.trap.udp-enabled=true` y `udp-port` (default `1620`; 162 suele requerir root)

5. **Syslog**
   - `/system logging action` remoto UDP al VPS
   - Opción A: bridge HTTP → `POST /api/netdiag/syslog/ingest`
   - Opción B: `net.diag.syslog.udp-enabled=true` (default port `5514`)

6. **Flag módulo**
   - `net.diag.enabled=true`
   - Header `X-Netdiag-Key`

> Live MK1 puede estar inalcanzable desde el entorno de desarrollo; la suite CI usa mocks. Validación live: perfil `-Plive-mk1` solo cuando haya conectividad.

## Lecturas REST por poll

Además de Fase 1 (`/interface`, `/system/health`, `/system/routerboard`, `/system/resource`):

| Path / call | Adapter |
|---|---|
| `POST /rest/tool/netwatch/print` | `MikrotikNetwatchAdapter` |
| `POST /rest/interface/ethernet/monitor` (`numbers`, `once`) | `MikrotikOpticalAdapter` |

`MikrotikSession.call` se usa para comandos que no son `print` (monitor DOM).

## monitor_config (ejemplo MK2 — túnel VPS WireGuard)

```json
{
  "criticalInterfaces": ["sfp-sfpplus1", "sfp-sfpplus2", "sfp-sfpplus3", "wg-ispadmin-vps"],
  "expectedFirmware": "7.23.2",
  "netwatchNames": ["upstream-http", "upstream-dns"],
  "opticalInterfaces": ["sfp-sfpplus1", "sfp-sfpplus2", "sfp-sfpplus3"]
}
```

Si `wg-ispadmin-vps` cae, el poll emite `LINK_DOWN` (no `GRE_TUNNEL_DOWN`; esa razón aplica solo a interfaces GRE).

## monitor_config (ejemplo MK1 legacy)

```json
{
  "criticalInterfaces": ["sfp-sfpplus1", "sfp-sfpplus2"],
  "expectedFirmware": "7.23.2",
  "netwatchNames": ["upstream-http", "upstream-dns"],
  "opticalInterfaces": ["sfp-sfpplus1", "sfp-sfpplus2"]
}
```

## Correlación

- Si hay incidente `OPEN` con `UPSTREAM_PROBE_FAIL` en el target (o ancestro), se suprimen: `LINK_DOWN`, `GRE_TUNNEL_DOWN`, `OPTICAL_*`, `LINK_FLAP`, `SNMP_TRAP_LINK_DOWN`.
- Padre-hijo Fase 1 se mantiene.

## Endpoints nuevos

| Método | Path | Body / notas |
|---|---|---|
| POST | `/api/netdiag/traps/ingest` | `TrapIngestRequestDto` → `TrapIngestResponseDto` |
| POST | `/api/netdiag/syslog/ingest` | `{ targetId, message }` → decisions |

Persistencia push: entidad `net_diag_trap_event`.

## LLM / diagnostic-json

Bundles incluyen secciones explícitas:

- `healthSnapshot`
- `netwatchSnapshot`
- `opticalSnapshot`
- `recentTraps`

## Properties

```properties
net.diag.optical.rx-low-dbm=-14.0
net.diag.optical.tx-fault-dbm=-40.0
net.diag.snmp.trap.udp-enabled=false
net.diag.snmp.trap.udp-port=1620
net.diag.syslog.udp-enabled=false
net.diag.syslog.udp-port=5514
net.diag.syslog.ppp-mass-threshold=20
net.diag.syslog.ppp-mass-window-seconds=60
```

## Tests

```bash
./mvnw test -Dtest='*NetDiag*,*AlertEvaluator*,*AlertSignal*,*WhatsAppOps*,*MikrotikPoll*,*MikrotikNetwatch*,*MikrotikOptical*,*RouterOsUptime*,*CorrelationEngine*,*SyslogIngest*,*SnmpTrap*'
```

No incluye Fase R4–R5 (switch wispadmin a REST).
