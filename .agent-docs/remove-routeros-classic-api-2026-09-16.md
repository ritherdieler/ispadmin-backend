# Deprecar API clásica RouterOS — 2026-09-16

El único transporte MikroTik en runtime es REST HTTPS (`RouterOs7RestAdapter`, `/rest/...` `:443`).
La superficie de la API binaria `:8728` **sigue existiendo** para binding de overlays viejos; está marcada `@Deprecated` y no mueve tráfico.

## Deprecado (no eliminado)

| Superficie | Estado |
|---|---|
| `RouterOsClientProperties.adapter` / `ClassicProperties` | `@Deprecated`; default `adapter=rest`, `classic.port=8728` |
| `router.os.client.classic.port` / `classic.timeout-ms` | Siguen en overlays; no hay cliente Legrange |
| `router.os.client.adapter` / `ROUTER_OS_CLIENT_ADAPTER` | Sigue bindable; `adapter≠rest` emite WARN |
| `net.diag.mikrotik.fallback-classic` | Bindable en `NetDiagProperties.mikrotik`; no cambia el transporte |
| Remap `8728 → rest.port` en `resolveRestPort` | Compatibilidad si un `MikrotikDeviceRef` trae `:8728` |
| `MikrotikClientAccessor.classicPort()` | `@Deprecated` → `restPort()` |
| `Mk1LiveSupport.classicDevice()` | `@Deprecated`; live usa `restDevice()` |

`LegrangeClassicAdapter` no se restauró: no había cliente clásico en runtime.

## Call sites vivos

`MikroTikConnectionService`, `executeCommand`, traffic poll y live monitor construyen `MikrotikDeviceRef` con `router.os.client.rest.port` (443).

## Tests

- `RouterOsClassicApiDeadSurfaceTest` (superficie deprecada, no ausente)
- `RouterOs7RestAdapterPortResolutionTest`
- `RouterOsClientConfigTest`
- `MikroTikConnectionServiceTest`
- `ApplicationProdNetDiagPropertiesFileTest`
- `NetDiagPropertiesTest`
