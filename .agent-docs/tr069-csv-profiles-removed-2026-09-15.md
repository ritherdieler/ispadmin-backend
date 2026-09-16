# TR-069: se eliminó el mecanismo CSV de perfiles

Fecha: 2026-09-15.

El alta ya no importa CSV de GenieACS ni resuelve paths TR-069 desde `tr069_model_profile`. El ACS encola provisions por HTTP NBI.

## Camino vivo

```text
Cliente
  │ HTTP
  ▼
Core  POST /subscription
  │ HTTP
  ▼
Gateway  POST /api/acs/v1/cpe/provision
  │ HTTP
  ▼
ACS  CpeFacadeService
  │
  └── NamedCpeProvisioner
        │ HTTP NBI
        ▼
      GenieACS  gf-pppoe-wan2-poc | gf-static-wan2-poc | gf-wifi-ssid-poc | gf-reboot-poc
```

Layouts: `NamedCpeLayouts` (`F6600R`, `V2804AX15T`, `VSOLVA74`). STATIC_IP encola `gf-static-wan2-poc`. PPPoE encola `gf-pppoe-wan2-poc`.

## Qué se borró

- Extractor/import/registry CSV (`GenieAcsCsvProfileExtractor`, `Tr069ModelProfileImportService`, `Tr069ModelProfileRegistry`)
- Provisioner SPV por paths (`Tr069ProvisioningService`) y el post-install Core (`Tr069PostInstallProvisioner`, `Tr069AsyncApplicator`)
- Endpoints `/admin/tr069-profiles`, `/api/acs/v1/profiles`, `/api/olt-gateway/acs/profiles`
- Seeds `copy-tr069-profiles-to-acs.sql` y `stg-acs-pppoe-wan-paths.sql`
- ACS Flyway `V5__drop_tr069_model_profile.sql` (V1–V4 quedan históricas)

## Qué no se tocó

- Perfiles GPON de línea/servicio en Gateway (OLT), distintos de CSV TR-069
- Scripts JS de GenieACS y `VparamProvisioner`
- `Tr069ProvisionRequest` / `Tr069ProvisionOutcome` (DTOs del provisioner nombrado)

## Tests

`AcsProfileOwnershipTest` prohíbe reintroducir archivos y endpoints CSV.
