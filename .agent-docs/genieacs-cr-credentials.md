# GenieACS: credenciales ACS / Connection Request

> **Auth HTTP CWMP (Inform):** en prod `cwmp.auth = true` — el ACS **no exige** usuario/contraseña HTTP. Motivo y riesgos: [genieacs-cwmp-auth-http.md](./genieacs-cwmp-auth-http.md).

## Problema evitado

Las ONUs no reportan `Password` ni `ConnectionRequestPassword` en TR-069 (vienen vacíos). GenieACS guardaba ese vacío y el Connection Request fallaba con **401**.

## Qué hace la config actual

| Provision | Cuándo corre | Qué hace |
|-----------|--------------|----------|
| `inform` | Cada Inform | User/URL ACS y CR (`{value: 1}`). Passwords con SPV (`null` timestamp). **No** toca `PeriodicInform*`. |
| `gigafiber-bootstrap` | BOOTSTRAP | Misma credencial + `PeriodicInformEnable/Interval=3600` (`{value: now}`) |
| `default` | Canal default | `{path: hourly, value: 1}` en passwords ACS/CR para no sobrescribir con vacío. Refresca WAN IP + SSID. **No** recorre `Hosts.Host.*`. |

Credenciales CPE/CR: `ACS_CPE_USERNAME` / `ACS_CPE_PASSWORD` en `/opt/gigafiber/genieacs/.env` (se empujan al ONU en `inform` / bootstrap). El **Connection Request hacia el CPE** usa ese par (Digest). El **Inform entrante** no valida HTTP Digest — ver [genieacs-cwmp-auth-http.md](./genieacs-cwmp-auth-http.md).

## Aplicar cambios

**En VPS (persistente, reinicia GenieACS):**

```bash
cd /opt/gigafiber/genieacs
./configure-genieacs-pilot.sh
```

**Vía NBI con túnel local:**

```bash
GENIEACS_NBI_URL=http://127.0.0.1:7557 ./scripts/genieacs/apply-provisions-via-nbi.sh
```

Fuente en repo: `scripts/genieacs/configure-genieacs-pilot.sh`, `scripts/genieacs/apply-provisions-via-nbi.sh`.

## Nota

El script viejo de pilot generaba **password aleatorio por ONU** en `inform`; eso rompía CR. La versión actual usa siempre el par fijo de `.env`.

`{value: now}` en cada Inform forzaba SPV de ManagementServer y, con `Hosts.Host.*` en `default`, el script superaba el timeout de 50 ms (CPU del VPS al 90%+). Bootstrap sigue usando `{value: now}` solo en BOOTSTRAP.

## ZTE F6600R: `PeriodicInformTime` 9007 bloquea CR

ZTE F6600R de fábrica reporta `PeriodicInformTime = 0001-01-01T00:00:00.000Z` e intervalo 180 s. Si `inform` intenta pasar el intervalo a 3600 **en el mismo SPV que las passwords**, el CPE responde `cwmp.9003` / `9007 Invalid parameter value` sobre `PeriodicInformTime`. GenieACS marca el canal `inform` en fault y **nunca graba** `ConnectionRequestPassword` → Summon: `Incorrect connection request credentials`.

Flota ACS 2026-08-31: 4/5 F6600R (DABE, BF8F, C838, DAA0) ya tenían intervalo 3600 y fecha `1970-01-01…`; solo **ZTEGDC47DAD1** quedó con intervalo 180, fecha year-0001, CR password vacía y fault `inform`.

`inform` ya no declara `PeriodicInformEnable/Interval/Time`. El intervalo 3600 queda en `gigafiber-bootstrap` (primer contacto). Recuperar un CPE atascado: borrar fault `DEVICE:inform` y esperar el próximo Inform (o SPV solo de user/pass CR, **sin** `PeriodicInform*`, `connection_request=false`).
