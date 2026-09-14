# OLT MA5608T — Alarmas activas y syslog

Captura live: **2026-07-31** · OLT `10.11.104.2` · firmware sesión V800R015.  
Parser ampliado: **2026-07-31** (`HuaweiOltAlarmParser` + `isClear`).

## Hallazgos CLI

| Comando | Resultado |
|---------|-----------|
| `display alarm active all` | **OK** — bloques Frame/Slot/Port/ONT, alarm ID hex, DESCRIPTION/CAUSE/ADVICE |
| `display logbuffer` | Desconocido en esta sesión |
| `display snmp-agent trap all` | Comando inválido en V800R015 (2026-08-26). Usar `display snmp-agent trap enable` → `Trap is disabled`; 0 target-host. Ver [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md) |
| `display alarm history` | Incompleto sin argumentos adicionales |
| `info-center` / loghost | Backup cfg sin `loghost`; no hay push remoto configurado hoy |

## Activas en Gigafiber (live)

| Alarm ID | Nombre | reasonCode |
|----------|--------|------------|
| `0x2e112007` | LOSi/LOBi distribute fiber | `ONT_OFFLINE` |
| `0x2e11a00b` | dying-gasp (DGi) | `ONT_DYING_GASP` |
| `0x2e21a102` | configuration recovery fails | `ONT_CONFIG_RECOVERY_FAIL` |

## Catálogo clasificado (diagnóstico puertos / ONUs / HW)

El parser clasifica por **Alarm ID** (preferido) y por nombre. Los IDs `0x2e12*` / `0x2e22*` marcan `isClear=true` (recover).

### Puerto físico GPON / ODN

| Alarm ID (fault) | Clear | reasonCode | Sev | Qué indica |
|------------------|-------|------------|-----|------------|
| `0x2e11a001` | `0x2e12a001` | `PON_PORT_DOWN` | P0 | LOS feeder — puerto sin óptica |
| `0x2e11a002` | `0x2e12a002` | `PON_PORT_HW_FAULT` | P0 | Hardware del puerto GPON |
| `0x2e314020` | — | `PON_OPTICS_ABSENT` | P0 | Transceptor óptico del puerto ausente |
| `0x2e314021` / `0x2e314022` | — | `PON_ROGUE_ONT` | P0 | ONT rogue / ilegal |
| `0x2e11999c` | `0x2e12999c` | `PON_RANGING_FAIL` | P1 | Fallos de ranging en el puerto |
| `0x2e31305f` | — | `PON_MASS_POWER_OFF` | P0 | Muchas ONTs del puerto sin energía |
| `0x2e11a523` | `0x2e12a523` | `PON_PROTECTION_FIBER` | P0 | Fibra backbone Type B protection |

### ONU (enlace / óptica / CPE)

| Alarm ID (fault) | Clear | reasonCode | Sev | Qué indica |
|------------------|-------|------------|-----|------------|
| `0x2e112007` | `0x2e122007` | `ONT_OFFLINE` | P1 | LOSi/LOBi — sin óptica de esa ONT |
| `0x2e11a00b` | `0x2e12a00b` | `ONT_DYING_GASP` | P2 | Corte de energía ONT |
| `0x2e112006` | `0x2e122006` | `ONT_LOFI` | P1 | Loss of frame |
| `0x2e112004` | `0x2e122004` | `ONT_SFI` | P1 | Signal fail |
| `0x2e112003` | `0x2e122003` | `ONT_SDI` | P2 | Signal degrade |
| `0x2e112002` | `0x2e122002` | `ONT_LCDGI` | P1 | Loss GEM delineation |
| `0x2e112001` | `0x2e122001` | `ONT_RDI` | P2 | Remote defect indication |
| `0x2e11a00c` | `0x2e12a00c` | `ONT_LOAMI` | P2 | Loss of PLOAM |
| `0x2e11a009` | `0x2e12a009` | `ONT_DFI` | P2 | Deactivation failure |
| `0x2e11a00f` | `0x2e12a00f` | `ONT_PEE` | P2 | Physical equipment error |
| `0x2e111999` | `0x2e121999` | `ONT_INITIATIVE_OFFLINE` | P2 | ONT se fue offline por iniciativa |
| `0x2e305015` | — | `ONT_AUTH_INVALID` | P1 | Autenticación inválida |
| `0x2e21a102` | `0x2e22a102` | `ONT_CONFIG_RECOVERY_FAIL` | P2 | Fallo recovery config OMCI |
| `0x2e11999e` | `0x2e12999e` | `ONT_DOWNSTREAM_SD` | P2 | Downstream signal degrade |
| `0x2e11999f` | `0x2e12999f` | `ONT_DOWNSTREAM_SF` | P1 | Downstream signal fail |
| `0x2e31305c` / `05e` | — | `ONT_OPTICAL_ALARM` | P2 | Parámetros ópticos alarm |
| `0x2e313060` / `062` | — | `ONT_OPTICAL_WARNING` | P2 | Parámetros ópticos warning |
| `0x2e313015` | — | `ONT_HW_FAULT` | P1 | Hardware ONT |
| `0x2e313024` | — | `ONT_ETH_LOS` | P2 | LOS puerto Ethernet ONT |
| `0x2e313016`–`019` | — | `ONT_BATTERY` | P2 | Batería backup ONT |
| `0x2e11a524` | `0x2e12a524` | `ONT_DOWI_THRESHOLD` | P2 | Umbral DOWi |
| `0x2e112009` / `00a` | recovers | `ONT_FEC_*` | P2 | FEC correctable / uncorrectable |
| `0x2e11a104` | `0x2e12a104` | `ONT_LOOCI_THRESHOLD` | P2 | Umbral LOOCi |

### Hardware / chasis OLT (por nombre; IDs varían por módulo)

| Patrón nombre | reasonCode | Sev |
|---------------|------------|-----|
| board failed / faulty | `OLT_BOARD_FAULT` | P0 |
| control board failed | `OLT_CONTROL_BOARD_FAULT` | P0 |
| power fail / abnormal | `OLT_POWER_FAULT` | P0 |
| fan failed | `OLT_FAN_FAULT` | P1 |
| temperature high | `OLT_TEMP_HIGH` | P1 |
| uplink down / los | `OLT_UPLINK_DOWN` | P0 |
| resto no mapeado | `OLT_ALARM` | según CRITICAL/MAJOR/… |

## Componentes

| Parámetros | `component` |
|------------|-------------|
| Slot + Port | `gpon-{slot}/{port}` |
| Solo Slot | `board-{slot}` |
| Ninguno | `olt` |

## Fallback: nunca descartar alarmas

Cualquier salida de la OLT que **no** se pueda parsear se conserva:

| Caso | `reasonCode` | Persistencia |
|------|--------------|--------------|
| Bloque sin `ALARM NAME` | `OLT_ALARM_UNPARSED` | `net_diag_olt_log_event` (`is_unparsed=true`) |
| Dump sin `--- END` / sin estructura | `OLT_ALARM_UNPARSED` | raw completo en `raw_message` |
| Alarma conocida / desconocida tipada | reason específico o `OLT_ALARM` | siempre se guarda el `rawBlock` |

Servicio: `OltAlarmIngestService.ingestCliActiveAlarms` — persiste **todas** (parseadas y unparsed). No descarta por fallo de parseo.

## Wire a alertas NetDiag (2026-07-31)

| Pieza | Rol |
|-------|-----|
| `OltAlarmPollScheduler` | Cada `olt.gateway.sync.alarm-interval-ms` (default 120 s) |
| `OltAlarmPollService` | CLI `display alarm active all` vía `CliJobType.ALARM_POLL` |
| `OltAlarmIngestService` | Persist + `AlertEvaluator.evaluateIngest` / `resolveByDedupKey` |
| Targets | PON por `board/port`; si no hay PON → target OLT |

**Emite incidente** si `reasonCode` ∈ `ALERTABLE_REASON_CODES` (no incluye `OLT_ALARM_UNPARSED`).  
**Clear:** mensaje recover → resolve; además, OPEN del scope OLT/PON ausente del active set → auto-resolve.  
**Dedup:** `{reason}:{targetId}:{gpon-X/Y[:ont-N]}`.

Props:

```properties
olt.gateway.sync.alarm-enabled=true
olt.gateway.sync.alarm-interval-ms=120000
olt.gateway.sync.alarm-initial-delay-ms=45000
```

## Estrategia hasta loghost

Poll CLI `display alarm active all` → ingest → incidentes NOC. Syslog/loghost sigue como mejora (misma ingest).  
El signal/inventory poll **no** abre alertas de puerto.

## API + UI de logs (2026-07-31)

| Pieza | Detalle |
|-------|---------|
| `GET /api/netdiag/olt/logs` | Página de `net_diag_olt_log_event` (incluye `isUnparsed`) |
| Query | `board`, `port`, `unparsedOnly`, `dateFrom`, `dateTo` (ISO Instant), `page`, `size` (max 200) |
| Servicio | `NetDiagOltLogQueryService` → DTO `OltLogPageDto` / `OltLogEventDto` |
| Backoffice | `/noc/olt-logs` · link desde `/noc` |

Los unparsed **no** abren incidente; se consultan aquí para ampliar el parser.

## Activación local verificada (2026-08-01)

Reinicio `run-dev` con `NET_DIAG_ENABLED=true` + `OLT_GATEWAY_SYNC_ALARM_ENABLED=true`:

- Tabla `net_diag_olt_log_event` creada (ddl-auto)
- Primer poll OK: **203** eventos persistidos (~7.5 s)
- Distribución ejemplo: `ONT_CONFIG_RECOVERY_FAIL` 162 · `ONT_DYING_GASP` 23 · `ONT_OFFLINE` 9 · `ONT_LCDGI` 6 · `OLT_ALARM_UNPARSED` 1
- CLI: `screen-length 0 temporary` + manejo `{ <cr>}` / `More` (reintento space/`\\r`/`f`; si stall → `q` + parcial)
- API: `GET /api/netdiag/olt/logs` → `totalElements=203`

## Pruebas

```bash
./mvnw -Dtest=HuaweiOltAlarmParserTest,NetDiagOltLogQueryServiceTest,NetDiagControllerTest test
```

## Fuentes

- Huawei Support: ONU offline / flapping (LOS, LOSi, LCDGi, SDi, SFi, LOFi, rogue, DGi)
- Huawei alarm list / ONT alarm-policy (`0x2e11*`, `0x2e12*`, `0x2e21*`, `0x2e31*`)
- Captura live Gigafiber + fixture `src/test/resources/olt/alarm-active-sample.txt`
