# Staging deploy + FIBER E2E (2026-09-01)

## Deploy

Command used for the working WAR:

```bash
./scripts/deploy.sh --env staging --with oltgateway,servicehealth,netdiag,traffic
```

Manual rebuild shortcut (after `subsystems.sh` / test change):

```bash
WITH=oltgateway,servicehealth,netdiag,traffic
bash scripts/subsystems.sh --with "$WITH" --write-dir target
excludes=$(tr -d '\n' < target/subsystem-excludes.txt)
sh mvnw package -DskipTests -Ddjl.linux -Pstaging-war \
  -Dsubsystem.excludes="$excludes" -Dsubsystem.with="$WITH"
```

### Subsystem coupling

| `--with` minimum for E2E | Reason |
|--------------------------|--------|
| `oltgateway` | `/onu/unconfigured_onus`, ONU authorize |
| `servicehealth` | `HealthOnuPort` required by `LabOpticalSshPollService` |
| `netdiag` | `NetDiagOltAlarmParserPort` required by `NetDiagOltAlarmParserAdapter` |
| `traffic` | `HealthTrafficPort` adapter (kept with historical staging profile) |

Deploying only `oltgateway` (or `oltgateway,servicehealth`) fails Tomcat startup with missing port classes.

### OLT: SmartOLT (igual que prod)

`OnuService` (`/onu/*`, registro FIBER) siempre delega a la API SmartOLT (`RealOltService`). El env compartido `OLT_GATEWAY_ENABLED=true` puede crear el facade SSH, pero ya no intercepta authorize/unconfigured/delete.

See `staging-smartolt-alignment-2026-09-01.md`.

### VLAN 100 + pool `192.168.250.x`

Staging tag `gigafiber.environment.tag=stg` allows VLAN 100 on pool `192.168.250.1/24` (`SubscriptionVlanRules`).

### Fixtures (TR-069 y catálogo)

Precarga idempotente: `./scripts/sql/staging-e2e-seed-all.sh`. Catálogo completo: `staging-e2e-fixtures.md`. Los perfiles TR-069 de prod (`F6600R` + alias `F6600RV9.0.21`) son obligatorios; sin ellos el alta termina en `MANUAL_REQUIRED`.

## E2E result (MK2 gateway `.250`, 2026-09-01 15:45)

| Check | Result |
|-------|--------|
| Espresso `registerFiber_withFirstOnu_reachesSuccess` | **PASS** (~1m16s) |
| Subscription | id **2348**, IP **192.168.250.21**, DNI **98295415** |
| MikroTik / OLT / TR-069 | `COMPLETE` / `COMPLETE` / **`COMPLETE`** |
| MikroTik ping `192.168.250.21` | **OK** 4/4, 0% loss, ~4 ms (gateway `192.168.250.1` en `sfp-sfpplus2`) |
| SmartOLT cleanup | **ONU was deleted** |
| Firebase Storage delete | FAIL SSL cert verify (Python 3.13); MySQL se terminó a mano |

## E2E result (perfiles TR-069 importados, 2026-09-01 15:24)

| Check | Result |
|-------|--------|
| Espresso `registerFiber_withFirstOnu_reachesSuccess` | **PASS** |
| Subscription | id **2347**, IP **192.168.250.20**, DNI **98294212** |
| MikroTik / OLT / TR-069 | `COMPLETE` / `COMPLETE` / **`COMPLETE`** (perfil `F6600R`) |
| SmartOLT cleanup | **ONU was deleted** |
| MikroTik ping `192.168.250.20` | FAIL 100% loss (script exit 1; ACS reportó COMPLETE) |

## E2E result (post SmartOLT OnuService)

Script: `IpsAdmin-android app/scripts/e2e_register_fiber_staging_espresso.sh`

```bash
E2E_ONU_SN=ZTEGDC47BFFD E2E_WIFI_SSID=lab-zte-e2e-24 E2E_WIFI_PASS='LabZteWifi24!' \
  ./scripts/e2e_register_fiber_staging_espresso.sh
```

| Check | Result |
|-------|--------|
| Espresso `registerFiber_withFirstOnu_reachesSuccess` | **PASS** |
| Subscription persisted | id **2346**, IP **192.168.250.19**, DNI **98293323** |
| SmartOLT authorize + cleanup delete | **OK** (`ONU was deleted`) |
| MikroTik ping post-test | FAIL (100% loss — lab CPE / WAN) |

| Check | Result |
|-------|--------|
| Espresso `registerFiber_withFirstOnu_reachesSuccess` | **PASS** (~2m38s) |
| Subscription persisted | id **2343**, IP **192.168.250.16**, DNI **98289518** |
| MikroTik ping post-test | FAIL (100% loss — lab CPE may be offline) |
| SmartOLT cleanup URL with SN `(ZTEG-…)` | Still broken (`curl: URL malformed`) |

## Known follow-ups

- Fix `tr069-e2e-hard-cleanup.sh` SmartOLT URL encoding for SN with parentheses.
- Runbook maestro: `staging-fiber-e2e-runbook.md`. Firebase cleanup: `scripts/tr069_e2e_firebase_delete.py` (CA bundle; MySQL sigue si Storage falla).
