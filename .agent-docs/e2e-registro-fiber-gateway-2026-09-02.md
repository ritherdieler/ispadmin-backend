# E2E registro FIBER vía OLT Gateway — 2026-09-02

## Resultado

Alta lab **ZTEGDC47BFFD** contra backend local (`dev,local`) con **authorize por Gateway SSH** (no SmartOLT).

| Campo | Valor |
|-------|--------|
| Suscripción | `2331` |
| DNI | `98372674` |
| SN | `ZTEGDC47BFFD` |
| IP | `192.168.30.237` |
| `oltProvisionStatus` | `COMPLETE` |
| `mikrotikProvisionStatus` | `COMPLETE` |
| `tr069ProvisionStatus` | `COMPLETE` |
| CLI authorize | `ont add 6 115 … lineprofile-id 6 srvprofile-id 13` + `service-port … traffic-table 8/9` |

WiFi enviada en el POST:

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `lab-zte-e2e-24` | `LabZteWifi24!` |
| 5 GHz | `lab-zte-e2e-24 - 5G` | `LabZteWifi24!` |

## Condiciones

- Backend local: `olt.gateway.writes.enabled=true`, `smartolt-olt-id=2`. No hace falta `olt.gateway.enabled`.
- GenieACS NBI: túnel SSH `localhost:7557` → VPS
- App: `connectedDevDebugAndroidTest` → `FiberRegisterFirstOnuE2ETest`
- Staging cloud seguía en 404 (WAR incompleto); la prueba usó Gateway local

## Bugs corregidos en el turno

1. **`authorizeOnu` + soft-delete**: tras `delete`, la fila soft-deleted ocupaba el unique `(olt,board,port,onu_index)` y `findMaxOnuIndex` (solo no-deleted) reasignaba el mismo índice → `Duplicate entry`. Fix: revivir SN soft-deleted y contar soft-deleted en `findMaxOnuIndex`.
2. **Espresso**: aserción `doesNotContain("MANUAL")` fallaba con el mensaje de éxito «No requiere configuración manual».

## Notas

- `DELETE /onu/configured/{id}` en este branch aún delega a `MockOltService` (no borra en OLT). Borrado real: `POST /api/olt-gateway/onu/delete/{externalId}` con `X-Olt-Gateway-Key`.
- Hard cleanup staging script no aplica a local (`--env` solo prod|staging).
