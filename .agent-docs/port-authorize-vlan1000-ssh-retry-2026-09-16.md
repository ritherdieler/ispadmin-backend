# Port hotfix authorize VLAN 1000 + SSH retry → develop (2026-09-16)

Cherry-pick no aplicó (monolito `src/` → `core/` + `oltgateway/`). Se portaron los 3 commits únicos de `hotfix/authorize-vlan100-1000` sin mergear la rama ni traer `8c9ad3a` / `73022b6`.

## Commits en develop

1. `fix(olt): authorize SmartOLT abre mgmt VLAN 1000 sin SSH` — `SmartOltMgmtVlanPolicy` + `SmartOltMgmtIpDhcpApplier` en Core. Tras authorize cloud (vlan 100) POST `onu/set_onu_mgmt_ip_dhcp/{sn}` vlan 1000. El path Gateway SSH no llama al applier (el Gateway ya abre gem 2 / VLAN 1000).
2. `feat(olt): add SSH connection retry for authorize commands` — `OltCommandExecutor` reintenta `OltUnreachableException` / IO de conexión. No reintenta errores de negocio (`ont already exist`). Default `maxRetryAttempts=3`.
3. `fix(olt): change SSH retry to unlimited attempts by default` — `maxRetryAttempts=0` = ilimitado.

## Props / env

- `OLT_SMARTOLT_CUSTOMER_VLANS_WITH_MGMT` / `OLT_SMARTOLT_MGMT_VLAN` → `olt.smartolt.*`
- `OLT_GATEWAY_SSH_MAX_RETRY_ATTEMPTS` (0 = ilimitado) / `OLT_GATEWAY_SSH_RETRY_DELAY_MS` → `olt.gateway.ssh.*`

## Tests

- `SmartOltMgmtVlanPolicyTest`, `SmartOltMgmtIpDhcpApplierTest`, `RealOltServiceTest`
- `OltCommandExecutorRetryTest` (retry acotado + ilimitado + no-retry de negocio + authorize)

## Relacionado

- [vps-secrets-management.md](./vps-secrets-management.md)
- [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md)
