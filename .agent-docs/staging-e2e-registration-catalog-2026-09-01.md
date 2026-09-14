# Catálogo staging para e2e registro FIBER (2026-09-01)

Datos mínimos en `ispadmin_staging` copiados desde prod para que la app Android complete el wizard **sin inyección** (solo foto de fachada).

## Seed (aplicar en VPS)

Preferido (catálogo + reload Tomcat):

```bash
./scripts/sql/staging-e2e-seed-all.sh
```

Fuente de verdad de fixtures: `.agent-docs/staging-e2e-fixtures.md`.

```bash
mysql < scripts/sql/staging-e2e-registration-catalog.sql
```

Idempotente. Incluye:

| Tabla | Origen prod | Uso en e2e |
|-------|-------------|------------|
| `place` | completa | `findByLocation`, selector Lugar |
| `mufa` | completa | FK de `nap_box` |
| `nap_box` | completa | selector NAP + `/napbox/near` |
| `plan` | completa | al menos 1 plan `FIBER` activo |
| `network_device` | id **8** (MK2) | host auto-seleccionado |
| `ip_pool` | `192.168.250.1/24` → host 8 | asignación IP staging |
| `tr069_model_profile` | `F6600R`, `HG8145X6`, `V2804AX15T` | ACS/TR-069 (alias `F6600RV9.0.21`) |

Script legacy (solo geo): `staging-e2e-place-nap.sql` — usar el catálogo completo arriba.

## Verificación API (Bearer tras login staff)

```bash
TOKEN=… # POST /users/login dscorp/nohacker
curl -H "Authorization: Bearer $TOKEN" …/place/findByLocation?latitude=-11.2177&longitude=-77.4137
curl -H "Authorization: Bearer $TOKEN" …/plan          # ≥1 type FIBER
curl -H "Authorization: Bearer $TOKEN" …/napbox       # ≥1 fila, incl. NO-001
curl -H "Authorization: Bearer $TOKEN" …/networkDevice/coreTypes  # MK id=8 activo
curl -H "Authorization: Bearer $TOKEN" …/onu/unconfigured_onus    # ZTEGDC47BFFD (SmartOLT, no DB)
curl -H "Authorization: Bearer $TOKEN" …/admin/tr069-profiles     # ≥3, incluye F6600R
```

## Fixture lab (Android)

Ver `IpsAdmin-android app/.agent-docs/e2e-register-fiber-staging-lab-onu-2026-09-01.md`.

## Notas

- ONU lab viene de SmartOLT (`unconfigured_onus`), no de MySQL.
- Usuario staff `dscorp` / `nohacker` es seed de app, no parte de este SQL.
- Re-seleccionar tipo FIBER en el paso 2 **borra** NAP/ONU/WiFi del ViewModel; el e2e no vuelve a pulsar instalación FIBER.
