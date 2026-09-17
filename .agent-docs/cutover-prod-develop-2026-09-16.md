# Cutover prod ← develop (preparación, 2026-09-16)

No se desplegó prod. No se ejecutó `deploy.sh --env prod`. No se tocó `tomcat9027` ni el WAR `/ispadmin`. No se apagó `traffic.poll.enabled`.

El informe de factibilidad `staging-a-prod.md` está desfasado: `develop` local está **limpio** en `6c555b3`, Fase 3 RouterOS allowlist ya va en `605914d`, y staging ya corre Traffic dueño MK (`1.0.3+a1f0dfe`). Este runbook es la preparación ejecutable sobre ese HEAD.

## Pin de release

| Superficie | SHA / release | Notas |
|---|---|---|
| **Pin recomendado** | HEAD de `develop` **después** de los commits de este cutover (incluye `6c555b3` + V52 partido) | Traffic MK + CGLIB + poll lock + allowlist + V52 sin backfill |
| Prod vivo | `1.0.3+83de8fe` en `tomcat9027` `/ispadmin/` | No tocar. Rollback = `/opt/gigafiber/ispadmin.war` |
| Staging vivo | `1.0.3+a1f0dfe` en `tomcat-staging` `/ispadmin-staging/` | Lab `#35` ZTE `ZTEGDC47BFFD` / `#36` VSOL `VSOL0031C0B6` — no borrar |
| `origin/develop` | atrasado (~76 commits) | Este turno **no** pushea |

Ancestros que el pin debe incluir:

| SHA | Qué aporta |
|---|---|
| `af7c810` | live-readings 360 por HTTP Traffic |
| `e2849b6` | todo I/O MikroTik por Traffic HTTP |
| `5b82a19` | recorte RouterOS de Ahora |
| `7936d57` | lock poll legado + recorte directorio/series |
| `605914d` | allowlist RouterOS (Fase 3, ya commiteada) |
| `a1f0dfe` | CGLIB SmartOLT (WAR staging actual) |
| `6c555b3` | docs: conservar lab ZTE/VSOL |

No hace falta “esperar Fase 3”. No cortar desde working tree sucio; este trabajo deja `develop` con solo los commits de preparación.

### `83de8fe..6c555b3` (99 commits, agrupados)

No es un dump. Conteo al pin base `6c555b3`:

| Grupo | n | Qué es |
|---|---:|---|
| docs / deploy notes | 34 | staging e2e, WAN, 360, isolation Tomcat |
| feat / feat(core) / feat(staging) | ~12 | WAR Gradle único (`3bbfb3a`), PPPoE, telemetría push, live-readings |
| feat(traffic) | 5 | `af7c810` `e2849b6` `5b82a19` `7936d57` `605914d` |
| fix(core) / fix(traffic) / fix(olt) | 15 | directorio XOR, CGLIB, live identity |
| fix / fix(e2e) | 14 | TR-069, gate 360, cleanup |
| chore / refactor / merge | ~12 | Maven→Gradle, hotfixes a develop |

`7a3d0f8` (docs post-WAN) es lo que staging **pretendía**; el WAR vivo ya pasó a `a1f0dfe`. El pin de prod no es `7a3d0f8`.

## V52 (bloqueante, hecho)

Flyway `V52__subscription_pppoe.sql` ahora es **solo DDL**:

- `access_mode` default `STATIC_IP`
- columnas `pppoe_*` / `management_*`
- `uk_subscription_pppoe_username` (seguro: todos NULL; MySQL permite varios NULL)
- índice `idx_subscription_access_mode`

**No** hay `UPDATE` a `PPPOE_FIXED`. **No** hay dump hardcodeado de 96 IPs.

UNIQUE: se crea con usernames NULL. El duplicado prod `192.168.26.199` ×2 no choca hasta que alguien asigne el **mismo** username. Eso queda fuera de Flyway.

Backfill opcional: `scripts/sql/backfill-subscription-pppoe-legacy.sql`

- **Do not run on cutover.**
- `SIGNAL SQLSTATE` si `192.168.26.199` tiene más de una fila.
- No vive en `db/migration/`.

`pppoe.new-subscriptions.enabled` sigue `${PPPOE_NEW_SUBSCRIPTIONS_ENABLED:false}` en el overlay prod.

Tests: `SubscriptionPppoeMigrationTest` (falla si el SQL de Flyway vuelve a hacer backfill masivo o UNIQUE+UPDATE).

## Overlay prod ≠ staging (hecho)

En `application-prod.properties`:

| Debe | Valor |
|---|---|
| Flyway | `enabled=true`, `baseline-on-migrate=true`, `baseline-version=38` |
| Hibernate Core | `ddl-auto=validate` (no `update`) |
| Altas PPPoE | `pppoe.new-subscriptions.enabled=false` |
| Tag | **unset** (comentario explícito; nunca `stg` / `lpstg`) |
| Poll | `traffic.poll.enabled=true` |
| Satélites JDBC | `prod_acs` / `prod_oltgateway` / `prod_traffic` |

Tests: `ProdEnvironmentPropertiesTest` + `SchemaGovernancePropertiesFileTest` + `ServiceHealthEnvironmentWiringTest`.

## Decisión schemas `prod_*`: **Opción A**

El WAR unificado asume satélites encendidos (`matchIfMissing=true` + `createDatabaseIfNotExist=true`). Opción B (flags off + preflight) apagaría ACS/OLT/Traffic in-process y rompe TR-069 / dueño MK.

**Opción A (elegida):**

1. Crear schemas **vacíos** `prod_acs`, `prod_oltgateway`, `prod_traffic` **antes** del primer boot (no fiarse solo de `createDatabaseIfNotExist`).
2. Primer boot: ACS Flyway V1–V5, OLT Flyway V1, Traffic `ddl-auto=update`.
3. **No** copiar filas de `stg_*` (lab).
4. **No** ejecutar `migrate-olt-mgr-to-prod_oltgateway.sql` en el cutover (RENAME fuera de `ispadmin`).
5. `migrate-traffic-to-prod_traffic.sql` es opcional **después**, si se quieren series históricas del Core.

Scripts (no ejecutados contra prod):

| Archivo | Uso |
|---|---|
| `scripts/sql/create-prod-satellite-schemas.sql` | `CREATE DATABASE` prod_* — solo en ventana de cutover |
| `scripts/sql/create-scratch-satellite-schemas.sql` | `scratch_acs` / `scratch_oltgateway` / `scratch_traffic` |
| `scripts/flyway-clone-ispadmin.sh` | dump SELECT `ispadmin` → scratch + baseline 38 + V39–V54 |

```bash
# Rehearsal (nunca ispadmin / ispadmin_staging):
SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --dry-run
SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --vps
# o --local si hay mysql/mysqldump y un dump de ispadmin
```

El script **Refuse** cualquier target `ispadmin` / `ispadmin_staging`. Password MySQL solo desde `MYSQL_ROOT_PASSWORD` del contenedor; no se imprime ni se commitea.

## Flyway clon

Ver la sección “resultado del clon” al final de este archivo (se actualiza cuando corre `--vps` / `--local`). Si no hubo dump alcanzable, el comando de arriba queda como la receta.

Roturas esperadas a anotar si el clon corre:

| Ver | Riesgo |
|---|---|
| V42 | procedimiento + FK `fiber_onu` (huérfanos) |
| V49 | DROP `FKj3wtbty2hpt2essof46euiuv0` |
| V50 | DROP `tr069_model_profile` (~3 filas en prod) |
| V52 | ya no UNIQUE+backfill; ADD COLUMN sobre Hibernate vacío |
| V54 | índices `acs_wifi_*` (tablas deben existir vía Hibernate/V36) |

## Plan MK / poll

Un solo runtime abre MK2: **Traffic** (`e2849b6`+). Core/NetDiag no inyectan `MikrotikClient`.

- `traffic.poll.enabled=true` se queda encendido.
- `gigafiber.environment.tag` prod **vacío** → sin prefijo `[stg]`, sin pool `.250`.
- Tras V52 sin backfill, prod queda `STATIC_IP` → directorio XOR emite **solo IP** (colas `target=IP/32`), no `pppoe-in`.
- Lab `#35` / `#36` y colas `*89*` viven en **staging**. El directorio prod se lee de `ispadmin`, no de `ispadmin_staging`.
- Dry-run SELECT: `scripts/sql/prod-traffic-directory-dry-run.sql` (`USE ispadmin`).
- No lanzar un poll de staging contra clientes prod. Mismo CCR, tags distintos.

## Higiene VPS (documentar, no ejecutar ahora)

1. Nginx `/ispadmin-staging*` → `tomcat-staging:8081` (no `tomcat9027`).
2. Quitar leftover `ispadmin-staging*` exploded/WAR en `tomcat9027` **después** de confirmar nginx.
3. `APP_RELEASE` no puede vivir solo en `/opt/gigafiber/.env` compartido (staging miente `83de8fe`). Hornear por contenedor o env file propio.
4. Rollback = WAR `83de8fe` ya en `/opt/gigafiber/ispadmin.war` (`sha256 986f736b…`).
5. Backup `mysqldump` de `ispadmin` (y `ispadmin_telemetry` si aplica) **antes** de cualquier WAR prod.

## Criterio “ya se puede cortar”

| Ítem | Estado en esta preparación |
|---|---|
| Pin limpio en `develop` local (V52 partido + overlay) | sí, tras los commits de este turno |
| Pin en `origin/develop` | **no** (sin push) |
| Clon Flyway verde V39–V54 | ver resultado abajo |
| V52 sin backfill ciego ni UNIQUE roto | sí |
| Overlay prod ≠ staging | sí |
| Decisión `prod_*` vs flags | Opción A; scripts listos, **no** creados en prod |
| Plan MK/poll por escrito | sí |
| Preflight módulos con OK del usuario | **no** (prohibido `--yes` por iniciativa) |
| `deploy.sh --env prod` | **no ejecutar** |

Hasta `origin` alineado, clon verde y schemas `prod_*` creados en ventana: **no cortar**.

## Checklist de cutover (cuando alguien lo ejecute)

1. Backup `ispadmin`.
2. `SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --vps` verde.
3. `scripts/sql/create-prod-satellite-schemas.sql` en el MySQL del VPS.
4. `scripts/deploy-disabled-modules-preflight.sh --env prod` — parar si avisa; no `--yes` solo.
5. `./scripts/deploy.sh --env prod` **solo** con pin acordado y confirmación explícita.
6. Health: `/ispadmin/` 200, `APP_RELEASE` del WAR nuevo, `flyway_schema_history` V39–V54 `success=1`, `access_mode` = `STATIC_IP` (no 97 `PPPOE_FIXED`), FK `fiber_onu_sn` ausente, login + search deuda.
7. No borrar lab staging `#35` / `#36`.

## Resultado del clon Flyway

**No corrió contra un dump de `ispadmin`.** SSH `root@212.85.13.47` en BatchMode → `Permission denied (publickey,password)`. No hay `SSH_IDENTITY_FILE` en `deploy.config.local`. No se pidió ni se usó password. No se hizo DML/DDL en `ispadmin` ni `ispadmin_staging`.

`--dry-run` local **PASS**: lista V39–V46, V48–V54 (no hay V47) y se niega `SCRATCH_SCHEMA=ispadmin` / `ispadmin_staging`.

Comando exacto cuando haya llave SSH:

```bash
cd ispadmin-backend
SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --dry-run
SCRATCH_SCHEMA=ispadmin_flyway_clone ./scripts/flyway-clone-ispadmin.sh --vps
```

Luego: `SELECT version, success FROM ispadmin_flyway_clone.flyway_schema_history ORDER BY installed_rank;` — anotar FAIL por versión aquí. Nunca `DROP`/`USE` `ispadmin`.
