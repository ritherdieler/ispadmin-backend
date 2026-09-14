# Provisions nombrados: Core → Gateway → ACS

Fecha: 2026-09-12. Tras el plan *Provisions ACS Gateway*. El cliente no habla con ACS.

```text
Cliente
  | HTTP JWT
  v
Core
  | HTTP (activate / cpe/provision / cpe/wifi / cpe/reboot / access-layout)
  v
Gateway
  | HTTP
  v
ACS WAR
  | NBI tasks name=provisions
  v
GenieACS  -->  ONU
```

## Qué cambió

- Alta FIBER siempre `PPPOE_DYNAMIC` (VLAN y flag `pppoe.new-subscriptions.enabled` no deciden). Secret MK2 + user/pass en el activate.
- WiFi del alta: `wifiPassword24` en 2.4 y 5.8. `wifiPassword5` se ignora. Android no cambia.
- ACS encola `gf-pppoe-wan2-poc` / `gf-wifi-ssid-poc` / `gf-reboot-poc`. Al arrancar hace PUT de los tres JS (`src/main/resources/genieacs/provisions/`).
- COMPLETE: GPV de IP `10.64.*` + SSIDs. HTTP 200 **ni 202** bastan: GenieACS suele responder **202 Accepted** al encolar `gf-pppoe-wan2-poc`. `NamedCpeProvisioner` trata 200/202 como enqueue OK y **siempre** hace `waitForComplete` (GPV). Devolver `PENDING` al 202 dejaba TR-069 colgado: el Gateway en `ACS_STATUS` no reencola si el ACS sigue `PENDING`. Gateway ACS RestTemplate: read timeout 120 s (`genieacs.waitTimeoutMs` default 90 s).
- Product class fuera de `F6600R` / `V2804AX15T` / `VSOLVA74` (Huawei incluido) → `FAILED` sin encolar.
- Migración IP→PPPoE y `retry-tr069` van por Gateway `cpe/provision` (sin re-authorize OLT). Core ya no usa `AcsCpeCoreClient`.
- WiFi post-alta: `POST /subscription/{id}/acs/wifi`. Reboot ACS: el endpoint existente encola `gf-reboot-poc`.
- Revert de migración sigue siendo WAN IP (rama vparams/path, no el script PPPoE).

Args, layouts y curl de lab: [genieacs-provisions-lab.md](./genieacs-provisions-lab.md). Contrato CWMP: [genieacs-provisions-f6600r-hallazgos.md](./genieacs-provisions-f6600r-hallazgos.md).

## Pruebas

- Node: `node --test scripts/genieacs/provisions/test/*.test.js` — 15 pass.
- Maven (102 tests, 0 fallos): `NamedCpeProvisionerTest` (incluye HTTP 202 → GPV COMPLETE), `CpeFacadeServiceTest`, `GenieAcsNamedProvisionBootstrapTest`, `GenieAcsVirtualParametersTest`, `OnuActivationServiceTest`, `FiberInstallationStrategyTest`, `PppoeAltaPolicyTest`, `AccessMigrationServiceTest`, `SubscriptionProvisionServiceTest`, `SubscriptionControllerAcsEndpointsTest`, `SubscriptionServiceIdempotencyTest`.
