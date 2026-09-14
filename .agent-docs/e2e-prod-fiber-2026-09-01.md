# E2E FIBER / TR-069 en prod — 2026-09-01

Validación post-deploy `1.0.3+41ecc90`. ONU de lab únicamente (`ZTEGDC47BFFD`); no se usó la primera ONU del dropdown.

| Campo | Valor |
|-------|--------|
| API | `https://api.gigafiberperu.cloud/ispadmin/` |
| Flavor | `prodDebug` · `FiberRegisterFirstOnuE2ETest` |
| DNI | `98297648` |
| Sub | **2334** (borrada en hard cleanup) |
| SN | `ZTEGDC47BFFD` / tipo `F6600RV9.0.21` → `F6600R` |
| NAP / lugar | `NO-001` · `9 de octubre` (`-11.2156`, `-77.4107`) |
| IP | `192.168.30.238` · host MK2 `8` |
| TR-069 | `COMPLETE` |
| Ping MK2 | 4/4 · 0% loss · ~4 ms |
| Firebase cleanup | 204 |
| MySQL residual | 0 |
| ONU post-cleanup | otra vez en `unconfigured_onus` |

WiFi del `POST /subscription` (antes del cleanup):

| Banda | SSID | Clave |
|-------|------|-------|
| 2.4 GHz | `lab-zte-e2e-24` | `LabZteWifi24!` |
| 5 GHz | `lab-zte-e2e-24 - 5G` | `LabZteWifi24!` |

Espresso no usa `e2e_register_fiber_espresso.sh` tal cual (ese script toma la primera ONU). Se pasó `e2e.onuSn=ZTEGDC47BFFD` y cleanup `--env prod`.
