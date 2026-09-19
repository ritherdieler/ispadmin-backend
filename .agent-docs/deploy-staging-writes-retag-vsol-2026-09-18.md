# Staging writes OLT + retag VSOL cliente (2026-09-18)

`olt.gateway.writes.enabled=true` en `application-staging.properties`. `tomcat-staging` fija `OLT_GATEWAY_WRITES_ENABLED=true` en compose (no toca el `.env` compartido ni prod). Inventario staging solo tenía ONUs lab: se copió `VSOL00872649` desde `prod_oltgateway` para el ensure-mgmt.

## Prueba no-lab (1 CPE)

| Campo | Valor |
|-------|--------|
| DeviceId | `B46415-V2804AX15T-12345B46415872649` |
| SN OLT | `VSOL00872649` (`0/1/1` ONT 91) |
| Tags | sin `lab` |
| OLT SP | VLAN 100 + 1 + **1000** (ensure-mgmt HTTP 200) |
| WCD.2 TR-069 | VLAN **1000**, DHCP **`10.20.0.161`**, CR `http://10.20.0.161:7547/tr069` |
| WCD.1 | VLAN **1**, `192.168.211.145` **sin cambio** |

Primer enqueue: `too_many_commits` (GPN `WCD.*` + WANIP.* + WANPPP 1..8). Provision: `{path:1}`, sin sondeo 1..8, WAN IP primero. Reintento NBI HTTP 200, `SetParameterValues` x2, `Script: gf-tr069-vlan1000 end`. No se tocó otro CPE.

```bash
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth
CORE_BASE=http://127.0.0.1:8081/ispadmin-staging GENIEACS_NBI_URL=http://127.0.0.1:7557 \
  ./scripts/genieacs/retag-tr069-vlan1000.sh \
  --device-id B46415-V2804AX15T-12345B46415872649 --sn VSOL00872649
```

`ensure-mgmt` sí hizo `ont modify … ont-lineprofile-id 12` y cortó internet VLAN 1. Restore: [restore-vsol-vlan1-keep1000-2026-09-18.md](./restore-vsol-vlan1-keep1000-2026-09-18.md).
