# E2E registro FIBER/TR-069 VSOL lab — prod (2026-09-05)

## Objetivo

Alta Android `prodDebug` con ONU lab `VSOL0031C0B6` contra API prod. **Sin post-cleanup** (suscripción viva a pedido).

## Resultado

| Campo | Valor |
|-------|--------|
| Subscription id | `2349` |
| DNI | `98638890` |
| Nombre | EEEFIBER PRUEBA |
| SN | `VSOL0031C0B6` |
| ACS device | `B46415-V2804AX15T-12345B4641531C0B6` |
| IP | `192.168.30.18` |
| `service_status` | `ACTIVE` |
| `tr069_provision_status` | `COMPLETE` |
| Espresso | `FiberRegisterFirstOnuE2ETest` OK |
| Post-cleanup | **omitido** |

## WiFi (POST /subscription)

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `lab-vsol-e2e-24` | `LabVsolWifi24!` |
| 5 GHz | `lab-vsol-e2e-24 - 5G` | `LabVsolWifi24!` |

## Notas

- Pre-paso: ONU estaba autorizada en OLT sin suscripción; se liberó con `tr069-e2e-hard-cleanup.sh --sn VSOL0031C0B6 --allow-empty` para que volviera a `unconfigured_onus`.
- MikroTik ICMP a `192.168.30.18` falló (100% loss); ARP en MK2 sí muestra MAC `B4:64:15:31:C0:C0` reachable en `sfp-sfpplus2` (L2 OK; ICMP suele ir bloqueado en CPE).
- NAP / geo: `NO-001`, lat `-11.2156`, lon `-77.4107` (place 9 de octubre).
