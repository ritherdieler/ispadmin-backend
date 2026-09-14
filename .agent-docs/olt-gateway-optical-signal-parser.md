# OLT Gateway — OpticalInfoParser + SignalCategory

Construcción del plan `signal_poll óptico` (alcance parser-optical-all + signal-category).

## OpticalInfoParser

Clase: `com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser` (`@Component`)

| Método | Firma | Uso |
|--------|-------|-----|
| `parse` | `parse(output: String, ontId: Int): ParsedOpticalInfo` | Una ONT (on-demand / compat) |
| `parseAll` | `parseAll(output: String): List<ParsedOpticalInfo>` | Tabla `display ont optical-info {port} all` (signal poll) |

### ParsedOpticalInfo

```kotlin
data class ParsedOpticalInfo(
    val ontId: Int,
    val rxPowerDbm: Double? = null,      // ONU Rx
    val txPowerDbm: Double? = null,      // ONU Tx
    val oltRxPowerDbm: Double? = null,   // OLT Rx ONT power (SmartOLT “Signal”)
    val temperatureC: Double? = null,
    val voltageV: Double? = null,
    val biasCurrentMa: Double? = null
)
```

Valores `-` en CLI → `null`.

### Formatos CLI soportados

1. **Live MA5608T** (`display ont optical-info {port} all`):

```text
ONT  Rx power    Tx power    OLT Rx ONT  Temperature  Voltage     Current
ID   (dBm)       (dBm)       power(dBm)  (C)          (V)         (mA)
  1  -18.54      2.20        -24.82      57           3.220       13
```

2. **Legacy** (fixture antigua, oltRx como `-`): sigue parseando; `oltRxPowerDbm = null`.

Fixture live: `src/test/resources/oltgateway/fixtures/display-ont-optical-info-all-live.txt` (captura real).

## SignalCategoryCalculator

Clases:

- `com.dscorp.wispadmin.oltgateway.service.SignalCategory` (enum)
- `com.dscorp.wispadmin.oltgateway.service.SignalCategoryCalculator` (`@Component`)

| Método | Firma |
|--------|-------|
| `fromOnuRxDbm` | `fromOnuRxDbm(rxPowerDbm: Double?): SignalCategory?` |

Umbrales **sobre ONU Rx** (`rxPowerDbm`):

| Categoría | Condición | `SignalCategory.value` |
|-----------|-----------|------------------------|
| GOOD | `rx >= -25` | `"good"` |
| WARNING | `-27 <= rx < -25` | `"warning"` |
| CRITICAL | `rx < -27` | `"critical"` |
| — | `rx == null` | `null` |

Constantes: `GOOD_THRESHOLD_DBM = -25.0`, `WARNING_THRESHOLD_DBM = -27.0`.

Persistencia: usar `category.value` en `olt_mgr_onu_status_current.signal_category`.

## OpticalInfoDto

Campo añadido: `oltRxPowerDbm: Double? = null` (mapeado desde `ParsedOpticalInfo` en query real y mock).

## Uso en signal poll

`OltSignalPollService` encola **un** job `CliJobType.SIGNAL_POLL` en `OltCliBus` (1 sesión SSH):

```kotlin
val rows = opticalInfoParser.parseAll(cliOutput)
for (row in rows) {
    val category = signalCategoryCalculator.fromOnuRxDbm(row.rxPowerDbm)
    // upsert status: onuRxDbm, onuTxDbm, oltRxDbm, temperatureC, signalCategory = category?.value, polledAt
}
```

Match ONU por `(board, port, onuIndex)`. No modifica `run_state` / `match_state`.

API: `POST /api/olt-gateway/admin/sync/signal` → `SignalPollResultDto`.  
`GET /onus/configured` expone `onuRxDbm`, `onuTxDbm`, `oltRxDbm`, `signalCategory`.

## Tests

- `OpticalInfoParserTest` — legacy + live + `parseAll`
- `SignalCategoryCalculatorTest` — good / warning / critical / null
- `OltSignalPollServiceTest` — un submit, slots/ports secuenciales, upsert, skips
