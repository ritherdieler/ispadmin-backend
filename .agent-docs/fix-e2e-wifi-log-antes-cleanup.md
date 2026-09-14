# Fix: entregar WiFi en log e2e antes del cleanup

Fecha: 2026-09-04.

## Problema

Tras `tr069=COMPLETE`, las claves WiFi solo aparecían en el echo final OK **después** del hard cleanup. Si fallaba un paso posterior, el log no entregaba SSID/password (regla AGENTS + runbook TR-069).

## Cambio

`scripts/e2e_register_fiber_staging_espresso.sh` (y el wrapper local) imprime, tras test OK y **antes** del cleanup:

```text
wifi_24 ssid=… password=…
wifi_5 ssid=… password=…
```

5 GHz por defecto: `${E2E_WIFI_SSID} - 5G` (mismo patrón que el form Android).

## Pruebas

- `E2ePlaceLocationFixtureTest.staging espresso script delivers wifi credentials before cleanup`
