# E2E Android VSOL → Diagnóstico 360 staging — 2026-09-03

Alta FIBER/TR-069 desde IpsAdmin `stagingDebug` (Espresso) con la ONU lab VSOL que informa al ACS. La suscripción **queda viva** (sin hard cleanup) para inspeccionar 360.

## Resultado

| Campo | Valor |
|-------|--------|
| Suscripción | **#2360** |
| Cliente | EEEFIBER PRUEBA · DNI `98415203` |
| SN SmartOLT | `VSOL0031C0B6` |
| Serial ACS | `12345B4641531C0B6` |
| Device GenieACS | `B46415-V2804AX15T-12345B4641531C0B6` |
| IP pool | `192.168.250.20` (`wanIpCache` ACS = misma IP, no `192.168.255.x`) |
| Host MK | id 8 (CCR2) |
| OLT / MK / TR-069 | `COMPLETE` / `COMPLETE` / **`COMPLETE`** |
| `subscription_acs.lab` | `true` (tag GenieACS `lab`) |
| 360 API | GPON **ONLINE**, RX **-19.46 dBm**, internet ACTIVE, ACS FRESH |
| Ping MK2 | timeout 100 % (no bloquea COMPLETE ni 360) |

UI 360 local: `http://127.0.0.1:3010/subscriptions/2360/service-health` (`vite --mode staging --port 3010`).

## WiFi del alta

| Banda | SSID | Contraseña |
|-------|------|------------|
| 2.4 GHz | `lab-vsol-e2e-24` | `LabVsolWifi24!` |
| 5 GHz | `lab-vsol-e2e-24 - 5G` | `LabVsolWifi24!` |

## Cómo se corrió

```bash
cd "IpsAdmin-android app"
SKIP_POST_CLEANUP=1 \
E2E_DNI=98415203 \
E2E_ONU_SN=VSOL0031C0B6 \
E2E_WIFI_SSID=lab-vsol-e2e-24 \
E2E_WIFI_PASS='LabVsolWifi24!' \
./scripts/e2e_register_fiber_staging_espresso.sh
```

Espresso `FiberRegisterFirstOnuE2ETest`: BUILD SUCCESSFUL. El wrapper salió 1 solo por ping MK; post-cleanup omitido (`SKIP_POST_CLEANUP=1`).

## Bloqueos que hubo que destrabar (staging)

1. **#2329** (LAB VSOL, ACS serial en `fiber_onu_sn`) ocupaba la ONU. Cleanup `--id 2329 --force` (el heurístico lab no matchea `LAB`/`VSOL`). SmartOLT no borra por serial ACS; el SN vivo es `VSOL0031C0B6`.
2. **`ispadmin-staging-oltgateway.war`** no estaba en Tomcat (el CRM proxyeaba a `/ispadmin-staging-oltgateway` → 500 en `/onu/unconfigured_onus`). Se copió el WAR ya construido (`/opt/gigafiber/ispadmin-staging-oltgateway.war`, 2026-09-02) a `webapps/`. Un recreate de Tomcat **sin** ese WAR vuelve a romper el dropdown Android.
3. Inventario `stg_oltgateway.olt_mgr_onu` id 7627 (`VSOL0031C0B6`, `gigafiber-ma5608t_1_6_10`) filtraba la ONU del autofind. Se borró para el alta; tras authorize se reinsertó id **7952** para que 360/óptica resuelvan SN.
4. **Actualizar óptica** en UI: primer intento falló (inventario ausente). Reintento API → 429 (cooldown 10 min; **ya eliminado** para refrescos manuales — ver `service-health-manual-refresh-no-cooldown-2026-09-03.md`). ACS en 360 sí quedó **Correcto**.

## GPON / óptica (2026-09-03 noche)

El gateway **sí recopilaba** la VSOL en `stg_oltgateway`:

| Campo | Valor |
|-------|--------|
| Inventario | `olt_mgr_onu` id **7952**, FSP `1/6/10`, `gigafiber-ma5608t_1_6_10` |
| SNMP `status_current` | Rx **-19.46**, Tx **32.80**, OLT Rx **-24.21** dBm, categoría `good`, distancia 420 m |
| SNMP `run_state` | `offline` (último poll ~01:32 Lima) |
| SSH `GET /onus/by-sn/VSOL0031C0B6` | `administrative_status=online` |

360 no mostraba señal porque el CRM no resolvía inventario HTTP (`HealthOnuPort` no era bean: `@ConditionalOnBean(HealthOltGatewayHttpClient)` en el `@Component` se evalúa antes de crear el cliente). El ingest óptico también abortaba sin ese puerto; además `findByOlt` solo ve las primeras 200 de 804 ONUs (VSOL queda fuera).

Hotfix staging (exploded WAR CRM, sin recreate Tomcat):

- `HealthOnuPortBridgeConfig` (`@Bean HealthOnuPort` → `HealthOnuAdapter`)
- `OltHistoryService`: lookup `findByOltBoardPortOnu` si la ONU no está en la página de `findByOlt` (código en esta rama + class en el WAR)
- `service.health.optical-pull-mode=samples` (snapshot `GET /optical-samples`, no live-sns)
- `WEB-INF/web.xml` válido mínimo (un `touch` de archivo vacío tumbó el contexto)

Tras reload: identidad `PON=2:1:6`, `ONU_ID=7952`; summary `onu_rx_dbm=-19.46` FRESH; `gpon=ONLINE`. Refrescar 360: `http://127.0.0.1:3010/subscriptions/2360/service-health`.

Un recreate de Tomcat **sin** copiar de nuevo `ispadmin-staging-oltgateway.war` y estas clases del CRM vuelve a romper alta Android y 360 GPON.

## Wi‑Fi / estaciones ACS (2026-09-03)

360 vacío en RSSI/estaciones no era mapeo VSOL: GenieACS tenía `TotalAssociations=0` en cache hasta un GPV fresco. Con el Mac en `lab-vsol-e2e-24`, tras GPV: WLAN5 = 1, `X_HW_RSSI` + SNR persistidos.

Cadencia automática staging: **3 min** (`acs-wifi-sample-target-seconds=180`, `acs-gpv-cooldown-seconds=180`). Ver `staging-acs-wifi-sample-cadence-3min.md`.

## Cleanup posterior (solo si se pide)

```bash
./scripts/tr069-e2e-hard-cleanup.sh --env staging --id 2360 --sn VSOL0031C0B6
```

Ajustar filas `stg_oltgateway.olt_mgr_onu` (`VSOL0031C0B6`) si el script aún no apunta a ese schema en esta rama.
