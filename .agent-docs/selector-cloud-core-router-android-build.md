# Selector CLOUD_CORE_ROUTER — construcción

**Fecha:** 2026-07-20  
**Plan:** `selector_cloud_core_router_android_b7ff2731.plan.md`  
**Hub:** [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)

Registro de construcción tras implementar el plan: renombrar `enabled` → `disabled` en `NetworkDevice`, filtrar cores activos en la API, y permitir elegir el core router en el registro Android cuando hay más de uno activo.

## Resumen

| Área | Resultado |
|------|-----------|
| Backend | Campo `disabled` (default `false`); endpoints de core solo listan activos |
| Android | 0 cores → error; 1 → auto-asignación; >1 → dropdown + validación `HOST_DEVICE` |
| SQL | Migración `scripts/sql/20260720_network_device_enabled_to_disabled.sql` |
| SQL seed | `scripts/sql/network-device-disabled-seed.sql` — solo **Mikrotik CCR 2** (`id=8`) con `disabled=false` para altas |

## Backend (ispadmin-backend)

### Modelo y contrato

- `NetworkDevice.disabled: Boolean = false` (`@Column(nullable = false)`)
- Expuesto en `NetworkDeviceDto` y `NetworkDeviceRequest`
- Mapper `UserMapper` mapea `disabled`
- `FiberInstallationStrategy`: rechaza instalación si `hostDevice.disabled`

### Persistencia

```sql
ALTER TABLE network_device ADD COLUMN disabled TINYINT(1) NOT NULL DEFAULT 0;
UPDATE network_device SET disabled = NOT enabled WHERE enabled IS NOT NULL;
```

Script: `scripts/sql/20260720_network_device_enabled_to_disabled.sql`

### API

`NetworkDeviceRepository.findActiveCloudCoreRouters()`:

```kotlin
@Query("SELECT n FROM NetworkDevice n WHERE n.networkDeviceType = 'CLOUD_CORE_ROUTER' AND n.disabled = false")
fun findActiveCloudCoreRouters(): List<NetworkDevice>
```

Usado en:

| Endpoint | Controller |
|----------|------------|
| `GET /ispadmin/networkDevice/coreTypes` | `NetworkDeviceController` |
| `GET /ispadmin/networkDevice/connection/cloud-core-routers` | `NetworkDeviceConnectionController` |

### Tests

- `NetworkDeviceDisabledTest`
- `FiberInstallationStrategyTest` (semántica `disabled`)
- `NetworkDeviceControllerTest` / `NetworkDeviceConnectionControllerTest` (excluyen `disabled=true`)

## Android (IpsAdmin-android app)

### Modelo

- `NetworkDevice.disabled: Boolean = false` (deserialización desde DTO)

### Registro de suscripción

| Pieza | Comportamiento |
|-------|----------------|
| Carga vía `coreTypes` | Filtra activos; 0 → fallo de carga |
| 1 activo | `selectedHostDevice` auto; sin UI |
| >1 activos | Dropdown `testTag("register_host_device_dropdown")` |
| Submit | `FormFieldKey.HOST_DEVICE` en `blockingForSubmit` |

Archivos principales:

- `RegisterSubscriptionFormState` — `activeCoreDevices()`, `shouldShowHostDeviceSelector()`
- `RegisterSubscriptionIntent.HostDeviceSelected`
- `RegisterSubscriptionComposeViewModel` — auto-asignación / selector
- `RegisterSubscriptionForm` — `AnimatedVisibility` + `MyOutLinedDropDown`

### Tests

- `RegisterSubscriptionComposeViewModelTest` (0 / 1 / >1 cores, selección, validación)
- `RegisterSubscriptionFormValidationTest` (`HOST_DEVICE`, helpers)
- `RegisterSubscriptionFormHostDeviceTest` (visibilidad dropdown)

Build: `./gradlew compileDebugSources` + tests unitarios del módulo `presentation`.

## Operación

Política altas (2026-07-20): solo **Mikrotik CCR 2** (`id=8`) habilitado. Seed idempotente: `scripts/sql/network-device-disabled-seed.sql`. Con un único activo, Android auto-asigna sin dropdown (sin hardcodear el nombre).

Para sacar un core del selector (p. ej. mantenimiento) sin borrarlo:

```sql
UPDATE network_device SET disabled = 1 WHERE id = <id>;
```

Para reactivarlo: `disabled = 0`. Tras el cambio, `coreTypes` deja de devolverlo (o vuelve a incluirlo).
