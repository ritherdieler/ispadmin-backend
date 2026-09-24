# E2E Android FIBER en KVM4 staging — 2026-09-24

## Alcance

- Entorno: KVM4 `2.24.66.53`, contexto `/ispadmin-staging`.
- ONU de laboratorio: `ZTEGDC47BFFD`.
- Flujo ejercitado por la app Android: alta FIBER v1 actual, `PPPOE_DYNAMIC`.
- No se modificaron producción, DNS ni el VPS legado.
- La nueva alta quedó provisionada; no se ejecutó cleanup posterior.

## Cleanup previo

El cleanup duro eliminó la suscripción de laboratorio `#39`, el secret PPPoE `gf39`, la foto de fachada, las filas Core/Gateway/Traffic y las tareas/faults ACS. La verificación SQL terminó con:

```text
remaining_sub_id=0
remaining_sub_sn=0
CLEANUP_DONE
```

Los endpoints Gateway de borrado devolvieron `404` porque `tomcat-staging` estaba arrancado con `OLT_GATEWAY_ENABLED=false`. Se comprobó directamente en la OLT, mediante el comando ya catalogado `display ont info by-sn ZTEGDC47BFFD`, que la ONT no existía. Después apareció en autofind y el Core la devolvió en `GET /onu/unconfigured_onus`.

## Corrección operativa de KVM4

El servicio `tomcat-staging` tenía `OLT_GATEWAY_WRITES_ENABLED=true`, pero el componente Gateway estaba deshabilitado. Se cambió únicamente el bloque `tomcat-staging` de `/opt/gigafiber/docker-compose.yml` a:

```yaml
OLT_GATEWAY_ENABLED: "true"
```

Backup previo:

```text
/opt/gigafiber/docker-compose.yml.bak-olt-gateway-staging-20260924T190223
```

Se recreó solo `tomcat-staging` y se restauró `/opt/gigafiber/ispadmin-staging.war` dentro del contenedor. Verificación posterior:

```text
GET /ispadmin-staging/actuator/health -> {"status":"UP"}
GET /ispadmin-staging/api/olt-gateway/health -> oltReachable=true
```

## Ejecución Android

La app `dev` se conectó exclusivamente a KVM4 mediante:

```text
Android :8080 -> adb reverse :8082 -> proxy /ispadmin a /ispadmin-staging
              -> túnel SSH local :8080 -> KVM4 :8081
```

Test ejecutado:

```text
FiberRegisterFirstOnuE2ETest
```

Resultado:

```text
BUILD SUCCESSFUL in 2m 48s
1 test, 0 fallos
```

## Resultado funcional

| Dato | Valor |
|---|---|
| Suscripción | `#40` |
| DNI de laboratorio | `90276991` |
| Acceso | `PPPOE_DYNAMIC` |
| Usuario PPPoE | `gf40` |
| IP PPPoE activa | `10.64.47.246` |
| OLT | `COMPLETE` |
| TR-069 | `COMPLETE` |
| FSP | `0/1/6` |
| ONT-ID | `46` |
| Service-ports | VLAN `100` y VLAN `1000` |
| SSID 2.4 GHz | `zte-e2e-0924` |
| SSID 5 GHz | `zte-e2e-0924 - 5G` |

GenieACS confirmó valores frescos del alta:

- `WANPPPConnection.2.Username=gf40`.
- `WANPPPConnection.2.ExternalIPAddress=10.64.47.246` y sesión `gf40` activa en MikroTik.
- `WLANConfiguration.1.SSID=zte-e2e-0924`.
- `WLANConfiguration.5.SSID=zte-e2e-0924 - 5G`.
- Último Inform: `2026-09-24T19:14:08.988Z`.

La contraseña WiFi se entregó al operador en el cierre de la ejecución y no se persiste en este documento.

## Observación pendiente

El CPE conserva tags GenieACS heredados de la corrida anterior (`sub-39` y nombre anterior), y no apareció una fila `subscription_acs` para `#40`. Esto no impidió la provisión ni la validación real de PPPoE/WiFi, pero el vínculo y retag de metadatos debe regularizarse antes de considerar completos los datos de detalle/day-2 de la nueva suscripción.

El emulador `emulator-5554`, el túnel y el proxy quedaron activos al finalizar.
