# ACS STATIC_IP = provision GenieACS por HTTP — 2026-09-15

Los perfiles CSV (`tr069_model_profile`) no participan en el alta. El modelo (F6600R / V2804AX15T / VSOLVA74) lo decide el **script** que vive en GenieACS. ACS solo habla con el NBI por HTTP.

```text
Core
  | HTTP JWT  retry-tr069 / activate
  v
Gateway
  | HTTP  /api/acs/v1/cpe/provision
  v
ACS WAR
  | HTTP NBI  POST /devices/{id}/tasks  name=provisions
  v
GenieACS  -->  script JS  -->  ONU
```

Continua = HTTP. El JS en GenieACS ramifica por `DeviceID.ProductClass`.

| Alta | Script NBI | Qué escribe |
|------|------------|-------------|
| FIBER PPPoE | `gf-pppoe-wan2-poc` | WAN PPP + WiFi si hay SSIDs |
| FIBER STATIC_IP | `gf-wifi-ssid-poc` | Solo WiFi (WAN IP ya la pone MK2/OLT) |
| WiFi día 2 | `gf-wifi-ssid-poc` | SSIDs |
| Reboot | `gf-reboot-poc` | Reboot CWMP |

`CpeFacadeService.provision` siempre usa `NamedCpeProvisioner` (encola el script). Ya no cae a `Tr069ProvisioningService` ni a vparams en el alta.

Verificación:

```text
./gradlew :acs:test --tests "com.dscorp.wispadmin.acs.service.CpeFacadeServiceTest" --tests "com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisionerTest"
```
