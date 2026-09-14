# Diagnóstico técnico 360 local (suscripción 2335)

**Fecha:** 2026-09-06

## Causa del vacío en UI

1. Core local arrancó con `gigafiber.subsystems.servicehealth.enabled=false` → `GET /subscription/{id}/service-health` = **404** y banner «No se pudo consultar el resumen…».
2. ACS staging devolvía `lastInformAt=null` aunque GenieACS tenía `_lastInform` fresco → estado ACS `MISSING` / `INVALID` (skew Lima en `cpe_record.last_inform_at`).
3. Snapshot `service_health_current` (60 s) podía servir un resumen viejo tras corregir telemetría.

## Qué quedó funcionando (local)

| Pieza | Estado |
|-------|--------|
| Core `:8082` + service-health ON + pilot `2335` | OK |
| Gateway `:8080` | OK (OLT SSH puede fallar si VPN cae) |
| Túneles ACS `:8091` + GenieACS `:7557` | Requeridos |
| Resumen / Conexión / Diagnóstico / Detalles UI | Cargan sin error de resumen |
| ACS `last_inform` | `FRESH` tras hotfix VPS + código |
| Tráfico en 360 | Soft-empty (Traffic WAR no levantado; `traffic.client-enabled=false`) |
| Óptica RX | Sin dBm si OLT no alcanzable |

## Fixes de código (Core/ACS)

- `CpeFacadeService.telemetry`: enriquece `lastInformAt` desde GenieACS `_lastInform` y lo persiste.
- `HealthEvidenceReader`: ignora `lastInformAt` futuro (> now+60s) y cae a `acs_wifi_status_current`.
- `AcsTelemetryService`: no pisa `inform_at` con null / futuro inválido.

Test: `CpeFacadeServiceTest` (`telemetry enriches lastInformAt…`).

## Operación local

```bash
# Túneles
sshpass -e ssh -f -N -L 8091:127.0.0.1:8081 -L 7557:127.0.0.1:7557 root@$VPS

# Core flags mínimas service-health
--gigafiber.subsystems.servicehealth.enabled=true
--service.health.enabled=true
--service.health.pilot-subscription-ids=2335
--service.health.station-hmac-key=<≥32 bytes>
--olt.gateway.client-enabled=true
--olt.gateway.internal-base-url=http://127.0.0.1:8080/ispadmin
```

Vínculo ACS único: `subscription.tr069_device_id` debe ser **único** (no compartir device id con otra suscripción lab).

Deploy ACS staging pendiente para el enrich GenieACS en prod/staging WAR.
