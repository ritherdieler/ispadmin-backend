# WhatsApp — Métricas de campaña (Fases 1, 5 y 8 backend)

Implementación backend (`ispadmin-backend`) de la parte backend del plan "Métricas campaña + mecanismos Meta". Alcance de este documento: solo las fases 1, 5 y 8. Las fases 2, 3, 4, 6 y 7 las ejecutan otros agentes en paralelo y se documentan aparte.

## Fase 1 — Filtros y semántica alineada con `overview()`

### `WhatsAppAnalyticsService`

- `campaigns(from, to, windowDays, templateCode = null, operatorUsername = null)`: ahora filtra los logs por `templateCode` (match exacto con `messageType`) y `operatorUsername` antes de agrupar por `campaignId`.
- `campaignDetail(campaignId, windowDays, templateCode = null, operatorUsername = null)`: acepta los mismos filtros opcionales (útil si una campaña mezcla plantillas/operadores) y ya **no** delega en `campaigns()` — calcula el resumen directamente sobre los logs de la campaña (`findByCampaignId`), evitando releer y re-agrupar todas las campañas del rango derivado (mejora de rendimiento).
- Nuevo helper privado `buildCampaignAnalytics(...)` compartido por `campaigns()` y `campaignDetail()` para no duplicar lógica.
- Semántica alineada con `overview()`:
  - `accepted`: mensajes con `status == SENT` (antes expuesto solo como `sent`).
  - `confirmed`: usa `isMetaConfirmed()` (status `SENT` + `deliveryStatus` no vacío, o `deliveredAt`/`readAt`/`failedAt` presentes). Antes no existía a nivel de campaña.
  - `failed`: ahora es `failedAt != null || deliveryStatus == "failed"` (antes solo miraba `failedAt`, perdiendo los fallos reportados únicamente vía `deliveryStatus`).
  - `deliveryRate` / `readRate`: denominador `confirmed` si `confirmed > 0`, si no `accepted` (igual que `overview()`).
  - `responseRate`: `responded / accepted`.
- `sent` se mantiene en el DTO (igual a `accepted`) por compatibilidad con consumidores existentes (CSV, frontend).

### DTOs (`dto/WhatsAppAnalyticsDtos.kt`)

`WhatsAppCampaignSummaryDto` y `WhatsAppCampaignDetailDto` ganan: `accepted`, `confirmed`, `deliveryRate`, `readRate`, `responseRate` (todas con default para no romper deserializadores existentes).

### `WhatsAppBackofficeController`

- `GET /whatsapp/analytics/campaigns`: nuevos query params `templateCode`, `operatorUsername`.
- `GET /whatsapp/analytics/campaigns/export`: mismos nuevos params, además de alias `from`/`to` (antes solo aceptaba `dateFrom`/`dateTo`).
- `GET /whatsapp/analytics/campaigns/{campaignId}`: nuevos query params opcionales `templateCode`, `operatorUsername`.

### Bug fix — `WhatsAppCsvExportService.exportCampaigns`

`WhatsAppCampaignSummaryDto.createdAt` es `String?` (ISO, vía `LocalDateTime.toString()`), pero el código llamaba `.format(formatter)` sobre él. Kotlin resuelve esa llamada contra la extensión `String.format(vararg args: Any?)` (estilo `String.format` de Java), **no** falla en compilación, pero tampoco reformatea la fecha: el CSV terminaba mostrando el string ISO crudo (`2026-08-04T10:15:30`) en vez de `yyyy-MM-dd HH:mm:ss`.

Fix: `formatCampaignCreatedAt()` parsea el ISO con `LocalDateTime.parse(...)` y sólo entonces aplica el `DateTimeFormatter`, con fallback al string original si el parseo falla.

### Tests

`WhatsAppAnalyticsServiceTest`: casos nuevos para `campaigns()`/`campaignDetail()` (antes sin cobertura) — agrupación, filtros por `templateCode`/`operatorUsername`, alineación `accepted`/`confirmed`/`failed`, campaña inexistente.

`WhatsAppCsvExportServiceTest` (nuevo): reproduce el bug de formateo y verifica el fix.

## Fase 5 — Costo estimado y ROI por campaña

- `WhatsAppAnalyticsService` recibe una nueva dependencia `WhatsAppMetaAnalyticsClient` (sin modificar ese archivo) para consultar `conversation_analytics` de Meta en el rango de fechas de la campaña/consulta.
- `estimatedCostPerCategory(from, to)`: llama `metaAnalyticsClient.fetchConversationAnalytics(...)`, parsea con `WhatsAppMetaAnalyticsParser.parseConversationAnalytics(...)` y calcula el **costo promedio por conversación** de cada categoría (`cost / conversationCount`), normalizando la categoría a mayúsculas. Si Meta no responde (API no configurada, error de red) o no hay costo reportado, el mapa queda vacío y el costo estimado es `0.0` (no rompe el flujo).
- `estimateCampaignCost(entries, costPerCategory)`: para cada campaña, toma los `WhatsAppMessageLog` con `billable == true` y `conversationId` no nulo, **deduplica por `conversationId`** (Meta cobra por conversación de 24h, no por mensaje) y suma el costo promedio de la categoría (`conversationCategory`) de cada conversación única.
- Nuevos campos en `WhatsAppCampaignAnalytics` / `WhatsAppCampaignSummaryDto` / `WhatsAppCampaignDetailDto`: `estimatedMetaCost` y `roi = conversionAmount - estimatedMetaCost`.

**⚠️ Es una estimación, no una reconciliación exacta de factura Meta.** Se basa en:
1. El costo promedio por categoría reportado por `conversation_analytics` en el rango consultado (no el costo exacto de cada conversación individual, que Meta no expone por conversación).
2. La categoría/`billable` que quedó registrada en `WhatsAppMessageLog` al procesar el webhook de `statuses` (puede no estar presente en logs antiguos o si Meta no envía el bloque `pricing`).

Para reconciliación exacta de facturación usar el reporte oficial de Meta Business Manager / WhatsApp Manager.

### Tests

`WhatsAppAnalyticsServiceTest`: casos con `WhatsAppMetaAnalyticsClient` mockeado devolviendo categorías/costos, verificando `estimatedMetaCost` y `roi` por campaña, incluyendo deduplicación por `conversationId` y comportamiento cuando Meta no está configurada (costo 0).

## Fase 8 — Serie temporal diaria (overview)

- Nuevo método `WhatsAppAnalyticsService.overviewSeries(from, to, templateCode = null)`: agrupa los logs del rango por día (`createdAt.toLocalDate()`) y devuelve, por día, `accepted`/`confirmed`/`failed` con la misma semántica que `overview()`/`campaigns()`.
- Nuevo endpoint `GET /whatsapp/analytics/overview/series` (`WhatsAppBackofficeController`), con los mismos parámetros de fecha que `/analytics/overview` (`dateFrom`/`dateTo`, alias `from`/`to`, `periodDays`, `templateCode`).
- DTO nuevo: `WhatsAppAnalyticsSeriesPointDto { date: String, accepted: Int, confirmed: Int, failed: Int }`.

### Tests

`WhatsAppAnalyticsServiceTest`: agrupación por día, orden ascendente por fecha, filtro por `templateCode`.

## Archivos tocados (backend, fases 1/5/8)

- `service/whatsapp/WhatsAppAnalyticsService.kt`
- `service/whatsapp/WhatsAppCsvExportService.kt`
- `dto/WhatsAppAnalyticsDtos.kt`
- `controller/WhatsAppBackofficeController.kt`
- `test/.../WhatsAppAnalyticsServiceTest.kt`
- `test/.../WhatsAppCsvExportServiceTest.kt` (nuevo)

No se tocaron `WhatsAppWebhookService.kt`, `WhatsAppTemplateMessageBody.kt`, `WhatsAppTemplateDeliveryService.kt`, `WhatsAppService.kt`, `WhatsAppMetaAnalyticsClient.kt` ni `WhatsAppAccountEventService.kt` (reservados a otras fases en paralelo); `WhatsAppMetaAnalyticsClient` solo se **inyecta** como dependencia de solo lectura en `WhatsAppAnalyticsService` para la Fase 5.
