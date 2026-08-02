# NetDiag — Mikrotik RouterOS 7 (REST)

**Última actualización:** 2026-08-01

## Optical en `probe_run`

Flujo: `NetDiagPollService` → `MikrotikPollAdapter.collectSnapshot` → `MikrotikOpticalAdapter.collect` → REST `POST /rest/interface/ethernet/monitor` con cuerpo `{"numbers":"<iface>","once":""}`.

Campos mapeados desde la respuesta JSON (claves con guiones, valores string):

| RouterOS | `OpticalSnapshot` |
|----------|-------------------|
| `name` | `interfaceName` |
| `sfp-rx-power` / `rx-power` | `rxPowerDbm` |
| `sfp-tx-power` / `tx-power` | `txPowerDbm` |
| `sfp-temperature` / `temperature` | `temperatureC` |
| `sfp-module-present` / `sfp-present` | `sfpPresent` |
| `sfp-connector-type` | `sfpConnectorType` |
| (derivado) | `opticalDdmAvailable` |

## DAC / cobre sin DDM (MK2)

En MK2 (`38.224.231.4`), `sfp-sfpplus1`–`3` usan **FT-SFP-DAC1M** (`sfp-connector-type=copper-pigtail`). RouterOS reporta `sfp-module-present=true` pero **no** incluye `sfp-rx-power`, `sfp-tx-power` ni `sfp-temperature` (no hay DDM óptico en DAC). Los `null` en `probe_run` son esperados, no fallo de parseo.

`opticalDdmAvailable=false` evita alertas `OPTICAL_*` falsas en esos puertos.

## Comandos de verificación (MK2)

CLI:

```
/interface ethernet monitor sfp-sfpplus1 once
```

REST (equivalente al poll):

```bash
curl -sk -u 'USER:PASS' -H 'Content-Type: application/json' \
  -X POST 'https://38.224.231.4/rest/interface/ethernet/monitor' \
  -d '{"numbers":"sfp-sfpplus1","once":""}'
```

Potencias ópticas solo aparecen en transceivers **de fibra** con DDM; no en DAC/cobre.

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
