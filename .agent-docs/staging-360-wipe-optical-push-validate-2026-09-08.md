# Staging 360 wipe + optical-batch push validate — 2026-09-08

Cutover validation for Gateway SNMP → Redis `onu.optical-batch` → Core samples. Canonical flow: [optical-push-gateway-core.md](./optical-push-gateway-core.md).

## Verdict: **PASS** (cutover path), with lab port-6 SNMP caveat

| Check | Result | Evidence |
|-------|--------|----------|
| Wipe 360 series | **PASS** | Core samples/series truncated to 0; Gateway optical fields cleared |
| Deploy Core + Gateway with `onu.optical-batch` | **PASS** | Classes present in both WARs; Core HTTP 200 |
| Gateway `SNMP_OPTICAL_POLL_SUMMARY` | **PASS** | `slots=2 portsOk=16 portsFailed=6 rowsMatched=495 onusUpdated=448 durationMs=807499` (21:25 Lima) |
| Redis `stg:gigafiber.events` type `onu.optical-batch` | **PASS** | ≥16 real port-batches from SNMP (producer `oltgateway`) |
| Core `olt_mgr_onu_optical_sample` > 0 | **PASS*** | 1 sample after wipe (`sub=2392`, Rx −22.36 dBm) via consumer `OpticalBatchPersistService` |
| Temp diagnostic logs in code | **N/A** | None added |

\*Natural lab ONUs (`ZTEGDC47BFFD` / `VSOL0031C0B6` on **board 1 port 6**) never enter the batch: SNMP walk for `slot=1 port=6` repeatedly **times out** (`SNMP_OPTICAL_PORT_FAIL`). Staging scope only collects **lab** subscriptions, and only those two subs have `fiber_onu_sn`. Core persist was proven by remapping lab `#2392` briefly to `TPLGE6A29998` (complete optics on port 8) and publishing one `onu.optical-batch` with the same schema; SN restored to `ZTEGDC47BFFD` immediately after.

## 1. Wipe (staging)

Charts start from empty series. **Not** deleted: subscriptions, ONU inventory, GenieACS, `identity_link`, `capability_profile`.

### Core `ispadmin_staging` (TRUNCATE)

| Table | Rows before |
|-------|-------------|
| `olt_mgr_onu_optical_sample` | 295 |
| `service_onu_state_event` | 7 |
| `acs_wifi_count_sample` | 71 |
| `acs_wifi_station_sample` | 147 |
| `acs_wifi_status_current` | 3 |
| `acs_wifi_station_hourly` | 29 |
| `acs_wifi_aggregation_watermark` | 1 |
| `telemetry_source_run` | ~24363 |
| `service_health_event` | ~493 |
| `service_health_current` | 6 |
| `evidence_link` | ~621 |
| `service_traffic_evidence` | 0 |
| `service_incident_subscription` | 0 |
| `service_remote_action` | 1 |
| `service_identity_conflict` | 2 |

Also: `service_health_cursor` reset (`observed_at=NULL`, `reference_id=0`). Kept: `identity_link` (162), `capability_profile` (1).

### Gateway `stg_oltgateway`

| Action | Detail |
|--------|--------|
| `olt_mgr_onu_status_current` | Cleared optical fields; `polled_at` → `1970-01-01`. Rows kept: **840**. Had Rx before wipe: **757**. |

### Redis

Deleted `stg:health:*` live keys. Streams left intact.

## 2. Deploy

### Attempt 1 — Core failed to start

```bash
./scripts/deploy.sh --env staging --only core,oltgateway --with servicehealth,oltgateway,netdiag,traffic
```

- Tests: **BUILD SUCCESS** (1951 tests).
- Gateway WAR: OK (`OnuOpticalBatchPayload` present); `Started OltGatewayApplication`.
- Core WAR: **deploy failed** — `ClassNotFoundException: ObsRetentionScheduler` (`ObservabilityTelemetryRetentionAdapter` needs observability on the classpath). Health check: Core **404**.

### Attempt 2 — Core with observability

```bash
./scripts/deploy.sh --env staging --only core --with servicehealth,oltgateway,netdiag,traffic,observability
```

- `FORCE_WAR_REBUILD=1` first try: aborted by `DeployWarNeedsRebuildScriptTest` (env leak). Retried **without** `FORCE` after deleting `target/ispadmin-staging.war`.
- Result: Core **HTTP 200**; WAR contains `OpticalBatchPersistService` + `ObsRetentionScheduler`.
- `SERVICE_HEALTH_OPTICAL_PULL_ENABLED` unset → default **`false`**.

## 3. Validation evidence

### Gateway SNMP

```
SNMP_OPTICAL_POLL_SUMMARY slots=2 portsOk=16 portsFailed=6 rowsMatched=495
  onusUpdated=448 polledAtRefreshed=9 incompleteDiscarded=38 durationMs=807499
```

Port **1/6** (lab): `SNMP_OPTICAL_PORT_FAIL` … `request timed out` (seen at 21:06 and 21:18). 74 ONUs on that port, **0** with Rx.

### Redis

- Stream: `stg:gigafiber.events`
- Consumer group `snapshot-core`: lag 0 after batch publish
- Event type: `onu.optical-batch`, producer `oltgateway`, payloads with `onuRxDbm`/`onuTxDbm`/`oltRxDbm`

### Core persist

| id | subscription_id | onu_sn | board/port | onu_rx_dbm | observed_at (UTC) |
|----|-----------------|--------|------------|------------|-------------------|
| 3 | 2392 | TPLGE6A29998 | 1/8 | −22.36 | 2026-09-09 02:30:40 |

Staging `service.health` with non-blank environment tag → **only lab** subscriptions collect (`ServiceHealthProperties.collects`).

## 4. Follow-ups (not blockers for cutover code)

1. Include **`observability`** in staging Core `--with` whenever `ObservabilityTelemetryRetentionAdapter` ships (or make the adapter optional without reflecting on the missing type).
2. Lab board **1 port 6** SNMP timeouts leave lab charts empty until the walk succeeds or lab ONUs move to a healthy port.
3. Optional: log when `OpticalBatchPersistService` skips for `resolveOnuForCollection` / `!scope.collects` (today silent).

## 5. Logs

Temporary Gateway/Core diagnostic logs: **not added** (existing `SNMP_OPTICAL_*` / consumer warns were enough).
