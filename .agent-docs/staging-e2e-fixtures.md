# Fixtures e2e staging — precarga

Fuente de verdad para **todos** los datos que el e2e de registro FIBER (y smoke ACS/TR-069) necesita en `ispadmin_staging`. No inventar filas a mano: copiar desde prod con el seed.

**Runbook completo (red, deploy, cleanup, cómo repetir):** [staging-fiber-e2e-runbook.md](./staging-fiber-e2e-runbook.md).

## Precargar (un comando)

Desde `ispadmin-backend/` (usa `scripts/deploy.config.local`):

```bash
./scripts/sql/staging-e2e-seed-all.sh
```

Aplica `scripts/sql/staging-e2e-registration-catalog.sql` (idempotente) y hace `touch` del WAR `ispadmin-staging` para que `Tr069ModelProfileRegistry` recargue al arrancar. Sin reload, el SQL queda en MySQL pero el alta FIBER sigue diciendo «No hay perfiles TR-069 importados». No crear `ispadmin-staging.xml` vacío en `webapps/` (rompe el context).

SQL suelto en el VPS:

```bash
# en el host, con el archivo ya en el servidor
docker exec -i mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < staging-e2e-registration-catalog.sql
```

## Qué se copia / fija

| Dato | Origen | Dónde vive | Obligatorio para |
|------|--------|------------|------------------|
| `place` (24, polígonos) | `ispadmin.place` | MySQL staging | `findByLocation`, selector Lugar |
| `mufa` | `ispadmin.mufa` | MySQL staging | FK de NAP |
| `nap_box` (162, incl. `NO-001`) | `ispadmin.nap_box` | MySQL staging | selector NAP, `/napbox/near` |
| `plan` (al menos 1 `FIBER` activo) | `ispadmin.plan` | MySQL staging | paso plan |
| `network_device` id **8** (MK2) | `ispadmin.network_device` | MySQL staging | host + ping e2e |
| `ip_pool` `192.168.250.1/24` → host 8 | seed (no solapa prod) | MySQL staging | IP VLAN 100 + tag `stg` |
| **`tr069_model_profile`** | `ispadmin.tr069_model_profile` | MySQL staging | TR-069 / ACS; sin esto → `MANUAL_REQUIRED` |
| Usuario staff `dscorp` | seed de app | MySQL users | login e2e |
| ONU lab `ZTEGDC47BFFD` | SmartOLT cloud | no es MySQL | `GET /onu/unconfigured_onus` |

Scripts legacy (no usar para un seed nuevo): `staging-e2e-place-nap.sql`, `staging-ip-pool.sql` — el catálogo completo ya los incluye.

## Perfiles TR-069 (prod → staging)

Necesarios para que `Tr069ProvisioningService` no corte el alta. El e2e lab usa tipo ONU **`F6600RV9.0.21`**, alias del perfil **`F6600R`**.

| `product_class` | Fabricante | Aliases | Serial origen (import prod) |
|-----------------|------------|---------|-----------------------------|
| `F6600R` | ZTE | `F6600`, `F6600RV9.0.21` | `ZTEGDC47C838` |
| `HG8145X6` | Huawei | `HG8145X6` | `48575443C6FBA6AA` |
| `V2804AX15T` | Realtek | — | `12345B4641531C0B6` |

Verificar:

```bash
TOKEN=$(curl -sS -X POST https://api.gigafiberperu.cloud/ispadmin-staging/users/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"dscorp","password":"nohacker"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
curl -sS -H "Authorization: Bearer $TOKEN" \
  https://api.gigafiberperu.cloud/ispadmin-staging/admin/tr069-profiles
# ≥3 productClass, incluye F6600R
```

Tras un **nuevo import en prod** (CSV GenieACS en Administración → Perfiles TR-069), volver a correr `staging-e2e-seed-all.sh` para sincronizar staging.

## Fixture lab Android

| Campo | Valor |
|-------|--------|
| API | `https://api.gigafiberperu.cloud/ispadmin-staging/` |
| Usuario / clave | `dscorp` / `nohacker` |
| Place | `9 de octubre` (id 1; `findByLocation` con el geo abajo) |
| Geo e2e | lat `-11.2156` lon `-77.4107` |
| NAP | `NO-001` (id 104); el wizard puede elegir otra por geo |
| Plan | primer `FIBER` activo (`lab-basico` id 1 si existe) |
| Host MK | id **8**, pool `192.168.250.1/24`, VLAN **100** (MK2 `sfp-sfpplus2` tiene `192.168.250.1/24` desde 2026-09-01; `wg-olt` aún no anuncia `.250`) |
| ONU SN | `ZTEGDC47BFFD` (hex `5A544547DC47BFFD`) |
| Tipo ONU | `F6600RV9.0.21` → perfil `F6600R` |
| Puerto GPON | 6 |
| WiFi 2.4 | SSID `lab-zte-e2e-24` / `LabZteWifi24!` |
| Script | `IpsAdmin-android app/scripts/e2e_register_fiber_staging_espresso.sh` |

```bash
E2E_ONU_SN=ZTEGDC47BFFD E2E_WIFI_SSID=lab-zte-e2e-24 E2E_WIFI_PASS='LabZteWifi24!' \
  ./scripts/e2e_register_fiber_staging_espresso.sh
```

## Verificación API post-seed

```bash
curl -H "Authorization: Bearer $TOKEN" …/place/findByLocation?latitude=-11.2156&longitude=-77.4107
curl -H "Authorization: Bearer $TOKEN" …/plan
curl -H "Authorization: Bearer $TOKEN" …/napbox
curl -H "Authorization: Bearer $TOKEN" …/networkDevice/coreTypes
curl -H "Authorization: Bearer $TOKEN" …/admin/tr069-profiles
curl -H "Authorization: Bearer $TOKEN" …/onu/unconfigured_onus
```

## Relacionado

- `staging-fiber-e2e-runbook.md` — cómo repetir el e2e de punta a punta
- `scripts/sql/staging-e2e-registration-catalog.sql`
- `staging-e2e-registration-catalog-2026-09-01.md`
- `staging-vlan100-pool-2026-09-01.md`
- `staging-smartolt-alignment-2026-09-01.md`
- `staging-mk2-pool-250-2026-09-01.md`
