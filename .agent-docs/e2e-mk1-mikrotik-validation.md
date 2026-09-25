# Validación E2E Mikrotik (MK1 lab) — 2026-08-01

MK1 (`network_device.id=1`, `38.224.231.2`) está dedicado a **pruebas**. Validación con curl, script automatizado, JUnit `live-mk1` y cliente Android (mismo contrato `PUT /payment`).

## Corrección aplicada

- `RouterOsEntryId.normalize`: ids RouterOS sin `*` (p. ej. `19916B`) se normalizan a `*19916B` en `MikroTikConnectionService`.
- Pruebas: `RouterOsEntryIdTest`, `MikroTikConnectionServiceTest` (comando `set` con `*id`).

## Scripts

| Script | Uso |
|--------|-----|
| `scripts/mk1-mikrotik-smoke.sh` | Lecturas + mutación opcional (`RUN_MUTATE=true`) |
| `scripts/mk1-mikrotik-e2e-full.sh` | Flujos completos: snapshot, enable/disable, pago, masivos |

```bash
export ISP_PASS='…'
./scripts/mk1-mikrotik-e2e-full.sh
# Solo lecturas/mutación local: RUN_MASS=false RUN_PAYMENT=false
```

Snapshots JSON en `$SNAPSHOT_DIR` (default `/tmp/mk1-e2e-*`).

## Resultados ejecutados (MK1 lab)

| Flujo | Resultado | Evidencia |
|--------|-----------|-----------|
| Lecturas API + cruce REST `deudores` | **PASS** | 41→368 entradas tras regeneraciones |
| `POST /api/filter-rules/disable\|enable` (batch) | **PASS** | |
| `POST .../disable\|enable/{deviceId}/{id}` sin `*` | **PASS** | id vigente `19916B` |
| `POST .../{id}` con `%2A` (star encoded) | **PASS** | |
| `PUT /payment` → quita IP de `deudores` | **PASS** | sub 882, pay 13042717, IP 192.168.25.124 |
| `PUT /subscription/cortarDeudores` | **PASS** | 42 deudores procesados en corrida E2E |
| `POST /subscription/generate-address-list-cancelled-subscriptions` | **PASS** | 368 creadas |
| `POST /subscription/generate-simple-queues` | **PASS** | 854 colas, ~8 min, 887 subs |
| `PUT /subscription/restore-internet-connection` | **PASS** | Solo BD (sin MK) |
| JUnit `RouterOs7RestAdapterTest`, `MikrotikPollAdapterLiveTest` | **PASS** | `-Plive-mk1` |
| Android `RegisterPaymentViewModelTest` | **PASS** | MockK, flujo `registerPayment` → API |
| Android UI journey (`e2e_mk1_payment_lab.sh`, eliminado) | **PASS** | 2026-08-01: fixture `900001`, IP `192.168.250.1` sale de `deudores` tras `PUT /payment` desde app |

## Pendiente / bajo demanda

- Flujos adicionales con fixture dedicado (`900002`, `192.168.250.2`): ver `scripts/mk1-lab-all-flows.sh` (cancel/reactivate, update-plan, ip-pool, pago lab).
- Backoffice NOC (filter rules UI): mismos endpoints que curl; codifica `*` en path (`filterRuleService.ts`).

### Ejecutado 2026-08-01 (todos los flujos MK1 lab)

| Flujo | Resultado |
|--------|-----------|
| `mk1-lab-all-flows.sh` (update-plan, POST/DELETE ip-pool, cancel/reactivate `900002`, PUT payment lab) | **8/8 PASS** |
| `mk1-mikrotik-e2e-full.sh` `RUN_MASS=true` | **13/13 PASS** (~9 min, 856 colas) |
| `mk1-lab-mikrotik-remaining.sh` | payment-commitment, alta WIRELESS, migración **PASS**; `PUT /plan` persist **PASS**; cola async lab opcional; smoke **MK1+MK2** |

Ver `.agent-docs/mk1-lab-mikrotik-remaining-build-2026-08-01.md`.

## Notas

- `generate-simple-queues` puede tardar varios minutos; timeout HTTP recomendado ≥ 900 s.
- Tras masivos, los `.id` de address-list cambian; repetir enable/disable con un id actual de `debt-cut`.
- Reiniciar backend tras cambios Kotlin: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local -DskipTests`.

## Referencias

- `.agent-docs/mk1-lab-android-e2e.md` — fixture fijo + reset + journey Android
- `.agent-docs/mk1-lab-fixture-build-2026-08-01.md`
- `.agent-docs/routeros-client-port.md`
- `scripts/mk1-mikrotik-smoke.sh`
- `scripts/mk1-lab-reset.sh`
