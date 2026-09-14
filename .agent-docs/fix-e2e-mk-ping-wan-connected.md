# Fix: ping MikroTik e2e tras TR-069 COMPLETE

Fecha: 2026-09-04.

## Problema

E2e Espresso llegó a `olt=COMPLETE` + `tr069=COMPLETE` (VLAN 100, IP `.250.21`) pero `tr069-e2e-mk-ping.sh` falló con `host unreachable` vía `192.168.250.1`.

Causa: COMPLETE solo comparaba GPV de `ExternalIPAddress` + SSIDs; **no** exigía `WANIPConnection.ConnectionStatus=Connected`. El CPE puede reportar la IP configurada en ACS sin tener aún L2/ARP en VLAN 100. El ping solo reintentaba ~30 s.

## Cambios

1. **ACS / Core GenieACS** (`Tr069ProvisioningService`): COMPLETE requiere también `ConnectionStatus=Connected`.
2. **`tr069-e2e-mk-ping.sh`**: defaults `PING_ATTEMPTS=12`, `PING_SLEEP_SECS=10` (~2 min); dump `/rest/ip/arp` al fallar; `sshpass -e`.
3. Helpers Python: `arp_has_address`, `is_wan_connection_ready` + `tr069_e2e_mk_ping_test.py`.

## Pruebas (verde 2026-09-04)

- `Tr069ProvisioningServiceTest` (27) incl. `does not complete while client WAN ConnectionStatus is Disconnected`
- `DeployEnvScriptTest.tr069_e2e_mk_ping_targets_staging_schema_when_env_staging`
- `python3 scripts/tr069_e2e_mk_ping_test.py`

## Operación

Para que staging deje de marcar COMPLETE prematuro: redeploy WAR **ACS** (`ispadmin-staging-acs` o el artefacto ACS del multi-WAR). Sin ese deploy, el e2e Espresso sigue con la lógica vieja en el VPS.
