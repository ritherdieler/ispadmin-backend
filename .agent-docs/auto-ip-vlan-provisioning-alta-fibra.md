# Aprovisionamiento automático IP y VLAN en alta de fibra

**Fecha:** 2026-07-20  
**Rama:** `cursor/auto-ip-vlan-provisioning-f644`

## Objetivo

Al registrar una suscripción de fibra (`POST /subscription`), el backend asigna automáticamente:

1. **IP** — desde el pool elegible del `hostDevice` resuelto.
2. **VLAN** — desde `hostDevice.vlanId` al autorizar la ONU (`FiberInstallationStrategy.resolveVlan`).
3. **Posición OLT de la ONU** — `board`, `port` y `olt_id` derivados del `NapBox` cuando están configurados.

## Servicios nuevos

| Servicio | Responsabilidad |
|----------|-----------------|
| `SubscriptionIpAllocationService` | `allocateFreeIp(hostDeviceId)` — filtra pools por core router |
| `FiberSubscriptionProvisioningService` | `resolveHostDeviceId`, `enrichOnuFromNapBox` |

## Flujo en `registerSubscription`

```
1. Cargar NapBox (si napBoxId)
2. resolveHostDeviceId(request, napBox)  → NapBox.hostDevice tiene prioridad
3. enrichOnuFromNapBox(onu, napBox)      → solo FIBER
4. allocateFreeIp(hostDeviceId)
5. FiberInstallationStrategy → authorize_onu con VLAN del hostDevice
6. Cola MikroTik con subscription.ip
```

## Modelo `NapBox`

Nuevo campo opcional:

- `hostDevice` (`ManyToOne` → `NetworkDevice`)

Expuesto en `NapBoxDto` como `hostDeviceId`.

Hibernate `ddl-auto=update` crea la columna en dev. En prod, validar FK antes de seed.

## Seed recomendado (prod)

Asignar `host_device_id` en NAPs según zona/core:

```sql
-- Piloto VLAN 100 / MK2
UPDATE nap_box SET host_device_id = 8 WHERE /* criterio negocio piloto */;

-- Legacy VLAN 1 / MK1 (si aplica)
UPDATE nap_box SET host_device_id = 1 WHERE /* criterio negocio legacy */;
```

## Pruebas

- `SubscriptionIpAllocationServiceTest`
- `FiberSubscriptionProvisioningServiceTest`
- `FiberInstallationStrategyTest` (VLAN existente)

## Pendiente operativo

- Poblar `nap_box.host_device_id` en producción por zona.
- Verificar que cada core tenga su `IpPool` con `is_eligible=1` y `host_device_id` correcto.
- El técnico sigue eligiendo la ONU por SN; board/port ya no dependen del listado si el NAP está mapeado.
