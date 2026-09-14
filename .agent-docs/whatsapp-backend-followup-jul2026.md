# Construccion — WhatsApp backend follow-up (Jul 2026)

## Alcance

Correccion de tests `WhatsApp*`, alineacion API con frontend hub, Fases 4–6.

## Resultado tests

```bash
./mvnw test '-Dtest=WhatsApp*'
# Tests run: 30, Failures: 0, Errors: 0
```

## Cambios principales

- Tests: MockK en `WhatsAppWebhookServiceTest`, matchers Kotlin en security/welcome/delivery
- `WhatsAppBackofficeQueryService` — filtros logs/inbound alineados con frontend
- DTOs frontend: overview, campaigns, conversion, account health, inbound, logs
- `WhatsAppMetaAnalyticsParser` — respuestas Meta estructuradas (no raw JSON)
- `WhatsAppCsvExportService` — export CSV logs/campaigns/inbound
- Alertas operativas en `GET /account/health` (calidad YELLOW/RED, fallos 24h)
- `whatsapp_synced_template.meta_template_id` usado en `template_analytics`
- Documentacion: `.agent-docs/whatsapp-meta-cloud-api.md`, README y hub UX

## Archivos nuevos

- `service/whatsapp/WhatsAppBackofficeQueryService.kt`
- `service/whatsapp/WhatsAppMetaAnalyticsParser.kt`
- `service/whatsapp/WhatsAppCsvExportService.kt`
- `test/.../WhatsAppMetaAnalyticsParserTest.kt`
- `.agent-docs/whatsapp-meta-cloud-api.md`

## Gaps conocidos

- `messagingLimit` / `messagingLimitTier` en health: Meta no expuesto aun (null)
- Filtro `templateCode` en overview: pendiente filtrar logs antes de agregar
- Frontend export CSV: endpoints listos; UI de descarga no cableada aun
