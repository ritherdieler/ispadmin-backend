# WAN internet se reemplaza en el alta (STATIC / PPPoE) — 2026-09-16

Contrato: cada ONU tiene exactamente 2 WAN. WAN1 = TR-069/mgmt (no se toca). WAN2 = internet. Un registro nuevo **reemplaza** WAN2; no agrega una tercera ni deja leftover del modo anterior.

## Causa (#13 VSOL leftover `gf12`)

`NamedCpeProvisioner.provisionStatic` solo encolaba `gf-wifi-ssid-poc` y devolvía `COMPLETE` cuando coincidían los SSID. No escribía WAN STATIC ni borraba el PPP leftover. `CpeFacadeService` usa solo el named provisioner (no vparams).

## Fix

| Alta | Provision NBI | WAN2 |
|------|---------------|------|
| PPPoE | `gf-pppoe-wan2-poc` | borra leftover IP, deja PPP |
| STATIC | `gf-static-wan2-poc` | borra leftover PPP, deja IP estática |
| WiFi día 2 | `gf-wifi-ssid-poc` | no toca WAN |

COMPLETE STATIC exige GPV de `ipExternalIp` = IP pedida (VSOL `WCD.2.WANIP.1`, F6600R `WCD.1.WANIP.2`). Un leftover `10.64.*` o `gf12` no cierra COMPLETE.

F6600R también borra leftover `WANIP.3` (caso #14 `192.168.250.20`) para que queden solo WAN1 mgmt + WAN2.

ACS hace PUT del JS al arrancar (`NamedGenieAcsProvisions.ids`). No deploy en este turno.

## Pruebas

```text
./gradlew :acs:test --tests "com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisionerTest" --tests "com.dscorp.wispadmin.acs.genieacs.GenieAcsNamedProvisionBootstrapTest"
./gradlew :core:test --tests "com.dscorp.wispadmin.wispadmin.scripts.genieacs.GenieAcsVirtualParametersTest"
node --test scripts/genieacs/provisions/test/gf-static-wan2-poc.test.js
```
