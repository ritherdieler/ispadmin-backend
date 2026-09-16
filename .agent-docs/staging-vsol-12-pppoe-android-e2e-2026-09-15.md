# Staging VSOL `#12` FIBER PPPoE — e2e Android (2026-09-15)

Alta lab dejada viva (`--cleanup-mode skip`) tras Espresso staging.

## Resultado

| Campo | Valor |
|-------|--------|
| Resultado | `E2E_FIBER_STAGING_ESPRESSO_OK` / `EXIT=0` (~3.5 min) |
| Suscripción | **#12** |
| ONU | `VSOL0031C0B6` |
| Tipo | `FIBER` / `PPPOE_DYNAMIC` |
| PPPoE | `gf12` |
| DNI | `99527174` |
| Nombre | `EEEVSOL PRUEBA` |
| Host | MK2 id **8**, VLAN **100** |
| OLT / MK / TR-069 | `COMPLETE` / `COMPLETE` / `COMPLETE` |
| WiFi 2.4 | `lab-vsol-e2e-24` / `LabVsolWifi24!` |
| WiFi 5 | `lab-vsol-e2e-24 - 5G` / `LabVsolWifi24!` |

## Comando

Desde `IpsAdmin-android app/`:

```bash
E2E_ONU_SN=VSOL0031C0B6 E2E_ACCESS_MODE=PPPOE_DYNAMIC \
  E2E_FIRST_NAME=EeeVsol E2E_LAST_NAME=Prueba \
  ./scripts/e2e_register_fiber_staging_espresso.sh --cleanup-mode skip \
  --wifi-ssid 'lab-vsol-e2e-24' --wifi-pass 'LabVsolWifi24!'
```

Log: `/tmp/e2e-vsol-pppoe-staging.log`.

## Bloqueo previo

El primer intento falló en `findByLocation` 404 por polígonos staging reescritos mal. Fix: [staging-place-srid-findbylocation-2026-09-15.md](./staging-place-srid-findbylocation-2026-09-15.md).
