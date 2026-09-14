# CRM Omnicanal WhatsApp — Fase 1: Tiempo real

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Objetivo

Entregar eventos WhatsApp en tiempo real al backoffice vía STOMP, con catch-up tras reconexión y sin duplicados.

## Modelo

- Entidad `CrmEventLog` (`crm_event_log`): `id` secuencial, `eventType`, `payload` JSON (TEXT), `createdAt`.
- Migración explícita: `V5__whatsapp_crm_fase1_events.sql` (Flyway no activo; `ddl-auto=update` crea la tabla en local).

## Publisher STOMP

- Servicio `CrmEventPublisher`:
  - persiste el evento;
  - publica en `/topic/whatsapp` el DTO `CrmRealtimeEventDto` (`eventId`, `eventType`, `payload`, `createdAt` string).
- Tipos:
  - `MESSAGE_RECEIVED` — desde `WhatsAppInboundMessageService` tras procesar inbound;
  - `CONVERSATION_UPDATED` — mismo flujo (bandeja: preview, unread, etc.);
  - `MESSAGE_STATUS` — desde `WhatsAppWebhookService.processStatusEvent`.

Handshake/token: reutiliza el endpoint existente `/ws` (`PlatformWebSocketHandshakeInterceptor`).

## Catch-up

- `GET /whatsapp/events?sinceEventId=` → eventos con `id > sinceEventId` (máx. 500).
- Protegido por `PlatformAuthFilter` + `CrmAccessPolicy` (`SECRETARY|ADMIN`), igual que el resto de `/whatsapp/**` (excepto webhook).

## Tests

```text
CrmEventPublisherTest
WhatsAppEventsControllerTest
WhatsAppInboundMessageServiceTest (incluye publicación CRM)
WhatsAppWebhookServiceTest (incluye MESSAGE_STATUS)
WhatsAppWebhookSignatureSkipTest
```

Resultado: verde.

## Fuera de alcance (Fases 2+)

- Media saliente, LLM, CSAT, métricas

## Continuación

Fase 2 documentada en `crm-omnicanal-fase2-claim-estados.md`.
