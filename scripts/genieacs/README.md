# GenieACS provisions (Gigafiber)

Fuente de verdad de scripts y presets desplegados contra la NBI (`7557`).

## Auth HTTP CWMP (`cwmp.auth`)

En prod: **`cwmp.auth = true`** — el ACS no exige usuario/contraseña HTTP en el Inform (VSOL/ZTE gSOAP no completan Digest 401). Documentación: `.agent-docs/genieacs-cwmp-auth-http.md`. Script: `configure-genieacs-pilot.sh`.

## Problema Huawei (HG8145X6)

`ManagementServer.Password` y `ConnectionRequestPassword` son **write-only**: la ONU informa `""` en cada Inform. GenieACS pisa el cache → Summon / Connection Request responde **401** aunque la password esté aplicada en el CPE.

## Solución

| Artefacto | Rol |
|-----------|-----|
| `provisions/huawei-writeonly-acs-credentials.js` | SPV de passwords en cada Inform (canal `inform`) |
| Preset `huawei-acs-credentials` | Canal **`default`** (cada sesión CWMP), OUI `00259E` / Huawei / HG8145 |
| `provisions/inform.js` | Inform global: passwords con SPV (`null` timestamp). No declara `PeriodicInform*` (ZTE F6600R 9007) |
| `provisions/gigafiber-bootstrap.js` | Bootstrap: `declare` + **SPV** (`null` timestamp) de passwords |

## Wi‑Fi telemetry (canal Inform, flota)

Provision `gigafiber-wifi-telemetry` + `apply-wifi-telemetry.py --all-models`: `ProductClass` F6600R y V2804AX15T, canal propio, radios 1+5. Ext `ext/wifi-inform-notify.js` + env `GENIEACS_TO_ACS_NOTIFY_URL` (prod) y `GENIEACS_TO_ACS_STAGING_NOTIFY_URL` (solo lab). **No** envolver `declare`/`ext` en `try/catch` (rompe Symbols GenieACS). `ext` va **después** de los `declare` WLAN.

- **Flujo canónico** (GenieACS → ACS → Gateway → Redis `cpe.inform` → Core → WifiCharts): `.agent-docs/wifi-on-inform-flujo-acs-gateway-core.md`
- Apply prod `--all-models` (2026-09-17): `.agent-docs/prod-wifi-telemetry-all-models-2026-09-17.md`
- Escalón lab / rollback: `.agent-docs/piloto-wifi-on-inform-vsol-lab-2026-09-08.md`
- Diagnóstico auto-ext VSOL: `.agent-docs/wifi-inform-auto-ext-vsol-2026-09-08.md`

Rollback: `--disable --apply`.

## VirtualParameters Gf* (staging ACS)

JS en `virtual-parameters/` (mapeo V2804AX15T / F6600R). El ACS staging (`genieacs.vparams.enabled=true`) hace SPV/GPV de `VirtualParameters.Gf*` en vez de paths TR-069. Tras deploy ACS staging:

```bash
GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-virtual-parameters-via-nbi.sh
```

Tests Node: `npm test` en este directorio. Detalle: `.agent-docs/tr069-perfiles-gobierno-acs.md`.

## Despliegue

Local:

```bash
GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-provisions.sh
```

Prod (desde host con acceso a la NBI Docker):

```bash
GENIEACS_NBI_URL=http://gigafiber-genieacs:7557 ./scripts/genieacs/apply-provisions.sh
```

## Verificación

```bash
curl -s 'http://127.0.0.1:7557/presets/huawei-acs-credentials' | python3 -m json.tool
curl -s 'http://127.0.0.1:7557/provisions/huawei-writeonly-acs-credentials' | python3 -m json.tool
```

Tras el próximo Inform de una Huawei, `ConnectionRequestPassword` en cache debe mostrar el valor (no `""`), y Summon debe responder HTTP 200.
