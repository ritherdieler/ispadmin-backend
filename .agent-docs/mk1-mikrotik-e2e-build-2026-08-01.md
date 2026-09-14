# Construcción — validación Mikrotik MK1 lab (2026-08-01)

## Backend

- Añadido `RouterOsEntryId` y normalización en `MikroTikConnectionService` para ids address-list.
- Scripts: `scripts/mk1-mikrotik-e2e-full.sh`, actualizado `scripts/mk1-mikrotik-smoke.sh`.
- Informe: `.agent-docs/e2e-mk1-mikrotik-validation.md`.

Pruebas locales:

```bash
./mvnw test -Dtest=RouterOsEntryIdTest,MikroTikConnectionServiceTest
./mvnw test -Plive-mk1 -Dtest=RouterOs7RestAdapterTest,MikrotikPollAdapterLiveTest  # ROUTEROS_MK1_*
export ISP_PASS=… && ./scripts/mk1-mikrotik-e2e-full.sh
```

## Android

- `RegisterPaymentViewModelTest` (MockK) en `presentation/src/test/.../payment/register/`.
- Build verificado: `./gradlew :presentation:compileDevDebugSources`.

Flujo Mikrotik en app: `RegisterPaymentViewModel` → `IRepository.registerPayment` → `PUT /payment` → `MikrotikService.reactivateServiceInMikrotik` (remove address-list `deudores`).
