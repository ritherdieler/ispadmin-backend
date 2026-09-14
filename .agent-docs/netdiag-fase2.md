# NetDiag Fase 2 — Mantenimiento, webhook LLM y correlación RADIUS

Construcción completada el **2026-07-26**.

## Alcance

| Área | Entregable |
|------|------------|
| Ventanas de mantenimiento | CRUD API + supresión WhatsApp durante intervalo activo |
| Silenciar incidente | `POST /incidents/{id}/silence` + campo `silencedUntil` |
| Webhook LLM | POST async de `diagnostic-json` al abrir incidentes **P0** |
| Impacto RADIUS/PPP | Bloque `radiusImpact` en bundles LLM vía `WispAdminRadiusImpactAdapter` |
| UI NOC | Panel mantenimiento + botón silenciar 1h en detalle |

## API nueva (requiere `X-Netdiag-Key`)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/netdiag/maintenance-windows` | Lista ventanas |
| POST | `/api/netdiag/maintenance-windows` | Crea ventana (`title`, `startsAt`, `endsAt`, `targetId?`) |
| DELETE | `/api/netdiag/maintenance-windows/{id}` | Elimina ventana |
| POST | `/api/netdiag/incidents/{id}/silence` | Silencia notificaciones (`durationMinutes` o `until`) |

Estados de incidente ampliados: `SILENCED`.

## Propiedades

```properties
net.diag.llm.webhook-enabled=${NET_DIAG_LLM_WEBHOOK_ENABLED:false}
net.diag.llm.webhook-url=${NET_DIAG_LLM_WEBHOOK_URL:}
net.diag.llm.webhook-timeout-ms=5000
```

## Comportamiento

- `WhatsAppOpsNotifier` no envía si hay ventana activa (global o por `targetId`) o si `silencedUntil > now`.
- `AlertEvaluator` dispara `NetDiagLlmWebhookService.notifyIncidentOpened` al abrir incidente.
- Webhook registra eventos `LLM_WEBHOOK_SENT` / `LLM_WEBHOOK_FAILED` en timeline.
- `radiusImpact` usa suscripciones activas ISP + `pppActiveCount` del último `probe_run` del target.
- Targets **GPON** (`monitor_config.kind` = `pon` / `olt`): bundles enriquecidos sin probe/RADIUS; ver [netdiag-llm-gpon-context.md](./netdiag-llm-gpon-context.md).

## Frontend (ispadmin-backoffice, rama `feature/netdiag-noc`)

- `NocMaintenancePanel` en `/noc`
- `NocIncidentDetail`: acción **Silenciar 1h**
- Servicios: `silenceIncident`, `listMaintenanceWindows`, `createMaintenanceWindow`, `deleteMaintenanceWindow`

## Tests locales (2026-07-26)

```bash
# Backend NetDiag + radius adapter
./mvnw test -Dtest='com.dscorp.wispadmin.netdiag.**.*Test,com.dscorp.wispadmin.wispadmin.adapter.WispAdminRadiusImpactAdapterTest'

# Suite completa backend
./mvnw test

# Backoffice
cd ../ispadmin-backoffice && npm test -- --run
```

Resultado: **74** tests NetDiag backend, **~418** suite backend, **190** tests backoffice (Vitest).

## Validación live MK1

Desde Mac (2026-07-26):

- `:8728` API clásica OK
- `:443` REST nativo OK con cert `netdiag-rest-mk1` en `www-ssl` (autofirmado CA `netdiag-ca`, 2026-07-26)
- Habilitación: `scripts/mk1-enable-www-ssl.py` (SSH CLI; la API `:8728` no aplica cambios de certificado en ROS 7.23)
- Queries REST usan cuerpo `{".query":["campo=valor"]}` (no `?campo` en JSON)
- Dev: `router.os.client.rest.verify-ssl=true`, truststore `classpath:routeros-mk-truststore.jks` (CA `netdiag-ca`, password dev `changeit`), `net.diag.mikrotik.fallback-classic=true`
- Regenerar truststore: `scripts/mk1-export-truststore.sh`
- Classic API en RouterOS **≥ 7.18** devuelve `!empty` en queries sin resultados; se usa `com.github.GideonLeGrange:mikrotik-java` (PR #90 / commit `f34e6c49`) en lugar de `me.legrange:mikrotik:3.0.7`
- Script ops: `scripts/mk1-enable-www-ssl.py` (certificado CA + server; requiere permisos en `/certificate`)

```bash
export ROUTEROS_MK1_USER=... ROUTEROS_MK1_PASSWORD=...
./mvnw test -Plive-mk1 -Dtest=LegrangeClassicAdapterTest,RouterOs7RestAdapterTest,RouterOsRestClassicFallbackAdapterLiveTest
python3 scripts/mk1-enable-www-ssl.py   # idempotente; requiere SSH :22 + sshpass
```

## Siguiente (fuera de alcance Fase 2)

Mejoras opcionales del roadmap original: topología visual, histórico bandwidth, correlación avanzada padre-hijo, ArchUnit para límites de módulo.
