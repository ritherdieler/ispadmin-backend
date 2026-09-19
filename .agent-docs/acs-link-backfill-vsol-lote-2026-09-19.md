# Backfill ACS 1:1 lote VSOL (2026-09-19)

Cruza GenieACS `_id` con la suscripción ACTIVE por sufijo hex de 6. No re-provisiona WAN ni WiFi.

## Core

- `FiberOnuSnClaimService` en el alta (un SN ocupado).
- `POST /subscription/acs/link` `{ "deviceId", "dryRun" }` → `LINKED | SKIP_NONE | SKIP_AMBIGUOUS | SKIP_NOT_ACTIVE | SKIP_DEVICE_NOT_FOUND`.
- `GET /subscription/acs/ghosts` lista CPE sin dueño ocupado.
- `POST /subscription/acs/ghosts/delete` `{ "deviceId" }` borra NBI solo si no hay ACTIVE/CUT_OFF/SUSPENDED.
- COMPLETE en `tr069ProvisionStatus` solo si lastInform < 24 h. Inform viejo: vínculo sí, badge `—`.
- SN de Core se unifica al de Gateway (`GET …/onu/get_onus_details_by_sn/{sn}`) si existe.
- `ensure-mgmt` VLAN 1000 vía Gateway; 404 no aborta el vínculo.

## Lote

```bash
./scripts/genieacs/link-acs-lote.sh --prod --dry-run --file /tmp/vsol-tr069-inverted-todo.tsv
./scripts/genieacs/link-acs-lote.sh --prod --file /tmp/vsol-tr069-inverted-todo.tsv
./scripts/genieacs/link-acs-lote.sh --prod --list-ghosts
```

`--delete-ghosts` es explícito y no forma parte del backfill.

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
