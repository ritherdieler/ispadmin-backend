# CRM Omnicanal WhatsApp — Fase 6: CSAT e inconformidades

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Migración

| Versión | Archivo |
|---------|---------|
| V10 | `V10__whatsapp_crm_fase6_csat.sql` (`csat_survey`, `csat_follow_up`) |

## Qué se añadió

| Pieza | Rol |
|-------|-----|
| `CsatSurvey` | Encuesta única por `ticketId` (`SCHEDULED/SENT/ANSWERED/EXPIRED/FAILED`) |
| `CsatFollowUp` | Seguimiento automático si score ≤ umbral (default 2); unique por survey |
| `CsatSurveyService` | Programar al cierre, envío, captura webhook, panel, reopen manual |
| `CsatSurveyScheduler` | Procesa envíos/reintentos/expiración (~60 s) |
| `CrmCsatController` | `GET /crm/csat/summary|surveys|follow-ups`, `PUT follow-ups/{id}`, `POST .../reopen-ticket` |
| Hooks | `AssistanceTicketController` (RESOLVED/CLOSED) + inbound WhatsApp |

## Envío

1. Dentro de ventana 24h: lista interactiva 1–5 (`INTERACTIVE`).
2. Fuera de ventana: plantilla Meta configurable `crm.csat.template-name` (default `csat_survey_v1`, idioma `es_PE`) con parámetro nombrado `ticket_id`.
3. **Requisito Meta:** la plantilla debe estar **APPROVED** en Business Manager antes de producción.
4. Reintentos controlados (`max-retries`, `retry-minutes`); no se crea segunda encuesta.
5. Expiración por `expire-hours` (default 72).

## Captura e inconformidades

- Score vía `button_reply` / `list_reply` id `csat_s_{surveyId}_{1-5}`.
- Idempotencia de captura por `capture_idempotency_key` (= meta message id).
- Comentario opcional: texto libre en ventana `comment-window-minutes` tras responder.
- Score ≤ `low-score-threshold`: evento `CSAT_LOW_SCORE_ALERT` + `CsatFollowUp` + lista de motivos (`PUNTUALIDAD`, `TRATO`, `NO_RESUELTO`, `CALIDAD`, `INCUMPLIMIENTO_VISITA`, `OTRO`).
- **No reabre el ticket automáticamente.** Reapertura solo con `POST /crm/csat/follow-ups/{id}/reopen-ticket`.

## Configuración

```properties
crm.csat.enabled=true
crm.csat.template-name=csat_survey_v1
crm.csat.template-language=es_PE
crm.csat.expire-hours=72
crm.csat.max-retries=3
crm.csat.retry-minutes=60
crm.csat.delay-minutes=5
crm.csat.low-score-threshold=2
```

## Panel

KPIs: promedio CSAT, tasa respuesta, enviadas/respondidas/expiradas; desglose por técnico/zona/tipo con `sampleSize`; evolución diaria; motivos; lista de follow-ups.

## Tests

```text
CsatSurveyServiceTest
CrmCsatControllerTest
WhatsAppInboundMessageServiceTest (mock CSAT)
```

## Fuera de alcance

Fase 7 (métricas operativas: primera respuesta, abandono, SLA formal, etc.).

## Riesgos para Fase 7

1. CSAT y métricas operativas comparten tickets/conversaciones: evitar doble conteo de cierres/reaperturas en dashboards.
2. Plantilla Meta CSAT fuera de ventana sigue siendo dependencia operativa; métricas de “notificaciones fallidas” deberían cruzarse con `FAILED/EXPIRED`.
3. Follow-ups abiertos pueden inflar pendientes de agente si Fase 7 no los separa de la cola CRM.
4. `placeName` como proxy de zona es heurístico; métricas por zona geográfica real pueden requerir join a `Place`.
