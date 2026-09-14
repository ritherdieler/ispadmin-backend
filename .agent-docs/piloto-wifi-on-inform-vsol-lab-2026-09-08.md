# Piloto GenieACS WiFi-on-Inform — VSOL lab (2026-09-08)

**Canónico (cómo funciona el push ACS→Gateway→Core):** [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md). Esta nota es alcance/allowlist/apply del piloto GenieACS.

## Alcance

Solo el CPE de laboratorio:

| Campo | Valor |
|-------|--------|
| GenieACS `_id` | `B46415-V2804AX15T-12345B4641531C0B6` |
| Serial | `12345B4641531C0B6` |
| ProductClass | `V2804AX15T` |
| Tag | `lab` |

No se modificó el preset/provision global `inform` / `inform.js`.

## Contrato del piloto

| Pieza | Valor |
|-------|--------|
| Provision | `scripts/genieacs/provisions/gigafiber-wifi-telemetry.js` |
| Apply | `scripts/genieacs/apply-wifi-telemetry.py` |
| Canal preset | `inform` |
| Eventos | `{}` (cada Inform, no solo `2 PERIODIC`) |
| Frescura | `Date.now()` (sin periodo horario) |
| Radios VSOL | WLAN **1** (5 GHz) y **5** (2.4 GHz) |
| Allowlist | precondition Serial+ProductClass del `_id` anterior |
| PeriodicInformInterval | **180 s** solo serial `12345B4641531C0B6` (declare en el provision piloto; no en `inform.js` de flota). Bootstrap global sigue en 3600. |

## Allowlist anterior (restaurar si hace falta)

Antes del piloto el preset apuntaba a ZTE PERIODIC:

```text
(DeviceID.SerialNumber = "ZTEGDC47BF8F" AND DeviceID.ProductClass = "F6600R")
 OR (DeviceID.SerialNumber = "ZTEGDC47DAD1" AND DeviceID.ProductClass = "F6600R")
channel: gigafiber-wifi-telemetry
events: {"2 PERIODIC": true}
```

## Apply / rollback

Dry-run:

```sh
python3 scripts/genieacs/apply-wifi-telemetry.py --device-id 'B46415-V2804AX15T-12345B4641531C0B6'
```

Apply (túnel NBI `127.0.0.1:7557`):

```sh
GENIEACS_NBI_URL=http://127.0.0.1:7557 \
  python3 scripts/genieacs/apply-wifi-telemetry.py --apply \
  --device-id 'B46415-V2804AX15T-12345B4641531C0B6'
```

Rollback (quita preset; provision puede quedar):

```sh
GENIEACS_NBI_URL=http://127.0.0.1:7557 \
  python3 scripts/genieacs/apply-wifi-telemetry.py --disable --apply
```

## Validación 2026-09-08

| Señal | Antes | Después (CR + Inform) |
|-------|--------|------------------------|
| `_lastInform` | `2026-09-08T17:33:25.536Z` | `2026-09-08T17:54:03.604Z` |
| WLAN1/5 `TotalAssociations` lag vs Inform | ~46799 s | **0 s** |
| `AssociatedDevice` | vacío | WLAN1 `[1]` MAC+RSSI; WLAN5 `[1]` MAC+RSSI |

El task CR pidió solo `TotalAssociations`; los índices `AssociatedDevice` con `_timestamp` alineado al Inform confirman el provision del canal Inform.

Tests: `node scripts/tests/wifi-telemetry-provision.test.cjs` → PASS.

## Intervalo Inform lab (2026-09-08)

| Campo | Antes | Después |
|-------|--------|---------|
| `PeriodicInformInterval` | 3600 | **180** |
| `PeriodicInformEnable` | true | true |
| Próximo Inform (máx.) | 60 min | **3 min** |

Persistencia: SPV + CR inmediato; el provision piloto re-declara 180 con `{value: 1}` solo para ese serial en cada Inform del allowlist.

## Cableado push (mismo día)

Camino GenieACS `ext` → ACS → Gateway → Core (canónico):
[wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).
Resumen del día: [wifi-on-inform-cableado-2026-09-08.md](./wifi-on-inform-cableado-2026-09-08.md).

El provision piloto invoca `ext("wifi-inform-notify", …)` **antes** de los `declare` WLAN (no después): así el notify no depende de GPV largos / `too_many_commits`. Ver [wifi-inform-auto-ext-vsol-2026-09-08.md](./wifi-inform-auto-ext-vsol-2026-09-08.md).

Validación E2E staging (secretos + WAR + sample Core):
[wifi-on-inform-validacion-staging-2026-09-08.md](./wifi-on-inform-validacion-staging-2026-09-08.md).
