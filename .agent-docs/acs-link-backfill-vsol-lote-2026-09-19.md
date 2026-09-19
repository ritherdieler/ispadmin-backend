# Backfill ACS 1:1 lote VSOL (2026-09-19)

Cruza GenieACS `_id` con la suscripción ACTIVE por sufijo hex de 6. No re-provisiona WAN ni WiFi.

## Deploy prod

`develop` @ `c4bb7aa` (`1.0.3+c4bb7aa`). Preflight: cadena FIBER/TR-069 completa. `GET /ispadmin/` HTTP **200**.

Incluye `FiberOnuSnClaimService` (alta sin duplicar SN) y `POST /subscription/acs/link`.

Los intentos `ffc6deb` / `21f38b3` dejaron Tomcat abajo: el paquete `service.genieacs` no se escanea, y la clase Kotlin final no admite proxy CGLIB de `@Transactional`. Arreglo: `SubscriptionAcsLinkConfig` + `open class` / `open fun link`.

## Core

- `FiberOnuSnClaimService` en el alta (un SN ocupado).
- `POST /subscription/acs/link` `{ "deviceId", "dryRun" }` → `LINKED | SKIP_NONE | SKIP_AMBIGUOUS | SKIP_NOT_ACTIVE | SKIP_DEVICE_NOT_FOUND`.
- `GET /subscription/acs/ghosts` lista CPE sin dueño ocupado.
- `POST /subscription/acs/ghosts/delete` `{ "deviceId" }` borra NBI solo si no hay ACTIVE/CUT_OFF/SUSPENDED.
- COMPLETE en `tr069ProvisionStatus` solo si lastInform < 24 h. Inform viejo: vínculo sí, badge `—`.
- SN de Core se unifica al de Gateway (`GET …/onu/get_onus_details_by_sn/{sn}`) si existe.
- Si el sufijo no tiene dueño, se intenta un cruce **1:1 por IP de WAN internet** (`ExternalIPAddress`, no TR-069 `192.168.252/22` ni `10.20/22`) contra `subscription.ip` con SN vacío.
- `ensure-mgmt` VLAN 1000 vía Gateway; 404 no aborta el vínculo.

## Reconcile por IP

```bash
./scripts/genieacs/link-acs-lote.sh --prod --from-ghosts --dry-run
./scripts/genieacs/link-acs-lote.sh --prod --from-ghosts
```

`listGhosts` / `deleteGhost` son `open` (mismo proxy CGLIB). `GET /subscription/acs/ghosts` → HTTP **200**, **106** filas. No se usó `--delete-ghosts`.

## Lote (prod, 2026-09-19)

Archivo `/tmp/vsol-tr069-inverted-todo.tsv` (108 `_id`).

| Paso | Resultado |
|------|-----------|
| Dry-run | HTTP 200 × 108. `LINKED` 101, `SKIP_NONE` 7, `SKIP_AMBIGUOUS` 0 |
| Write | igual. Sin PUT NBI de WAN / sin `retryTr069` |

`SKIP_NONE` (sin ACTIVE/CUT_OFF/SUSPENDED):

- `12345B4641586D849`
- `12345B46415F5F566`
- `12345B46415F5ECD6`
- `12345B4641500CBB4`
- `12345B46415F5DFA6`
- `12345B4641531FAE6` (TESTHU)
- `12345B4641531EF96`

## Fantasmas

NBI 403 CPE × sufijos ocupados en Core: **106** sin dueño. No se borró ninguno.

Incluye la lab `ZTEGDC47BFFD` (sigue conectada) y los 7 `SKIP_NONE` del lote.

```bash
./scripts/genieacs/link-acs-lote.sh --prod --list-ghosts
# --delete-ghosts es explícito y no forma parte del backfill
```

## Duplicados Core

Antes del backfill: `scripts/sql/release-duplicate-fiber-onu-sn-core-2026-09-19.sql` (ver `duplicados-fiber-onu-sn-core-2026-09-19.md`).

## Tests

```bash
./gradlew :core:test \
  --tests "com.dscorp.wispadmin.wispadmin.service.genieacs.SubscriptionAcsLinkServiceTest" \
  --tests "com.dscorp.wispadmin.wispadmin.controller.SubscriptionControllerAcsEndpointsTest" \
  --tests "com.dscorp.wispadmin.wispadmin.scripts.genieacs.LinkAcsLoteScriptTest" \
  --tests "com.dscorp.wispadmin.wispadmin.service.FiberOnuSnClaimServiceTest"
```
