# WhatsApp Meta Cloud API — Webhooks, Analytics e Insights

Guía de integración backend (ispadmin-backend) con Meta WhatsApp Cloud API para el hub de backoffice.

## Credenciales y configuración

Variables en `application-*.properties` / entorno:

| Variable | Uso |
|----------|-----|
| `whatsapp.access-token` | Token permanente de System User |
| `whatsapp.phone-number-id` | Phone Number ID del número comercial |
| `whatsapp.business-account-id` | WABA ID |
| `whatsapp.webhook-verify-token` | Token de verificación GET del webhook |
| `whatsapp.app-secret` | App Secret para validar `X-Hub-Signature-256` |

## Webhook

### URL

```text
POST/GET https://api.gigafiberperu.cloud/ispadmin/whatsapp/webhook
```

### Campos suscritos recomendados

| Campo Meta | Procesamiento backend |
|------------|----------------------|
| `messages` | Estados (`statuses`) + mensajes entrantes |
| `message_template_status_update` | Plantillas pausadas/deshabilitadas |
| `message_template_quality_update` | Calidad GREEN/YELLOW/RED |
| `phone_number_quality_update` | Calidad del número |
| `account_alerts` | Alertas operativas de cuenta |
| `template_category_update` | Cambios de categoría |
| `business_capability_update` | Cambios de límites de la cuenta (ej. `max_daily_conversation_per_phone`) — evento consultable en health/alerts |
| `user_preferences` | Opt-out/opt-in de mensajes de marketing por número (`wa_id`, `category`, `stop`/`resume`) |

**Nota operativa (`business_capability_update` y `user_preferences`):** ambos campos deben suscribirse manualmente
en Meta App Dashboard → WhatsApp → Configuration → Webhook fields (no se activan solo con el código del backend).
Sin esta suscripción manual, Meta nunca envía estos webhooks aunque el endpoint ya sepa procesarlos.

### Firma

El controller valida `X-Hub-Signature-256` con HMAC SHA-256 del body usando `whatsapp.app-secret`.

## Analytics Meta (Insights)

Cliente: `WhatsAppMetaAnalyticsClient`  
Parser estructurado: `WhatsAppMetaAnalyticsParser`

### Endpoints backoffice

| Endpoint | Fuente Meta | Respuesta frontend |
|----------|-------------|-------------------|
| `GET /whatsapp/analytics/meta/templates` | `template_analytics` | `[{ templateId, templateName, sent, delivered, read, clicked }]` |
| `GET /whatsapp/analytics/meta/conversations` | `conversation_analytics` | `{ categories[], totalCost, currency }` |
| `GET /whatsapp/analytics/meta/pricing` | `pricing_analytics` | `{ tiers[] }` |

Parámetros de fecha aceptados: `dateFrom`, `dateTo` (alias `from`, `to`).

### template_analytics y template_id

Meta exige IDs numéricos de plantilla, no nombres. El backend:

1. Sincroniza plantillas con `POST /whatsapp/templates/sync` → persiste `metaTemplateId` en `whatsapp_synced_template`.
2. Resuelve nombres internos (`payment_reminder_gigaperu`, etc.) a IDs antes de llamar a Meta.
3. Devuelve datos agregados listos para `WhatsAppTemplateClicksPanel`.

**Importante:** `clicked` solo está disponible en plantillas con botones URL o Quick Reply y ventana de 7 días desde el envío.

## Sincronización de plantillas

```http
POST /ispadmin/whatsapp/templates/sync
```

Respuesta alineada con frontend:

```json
{
  "synced": 4,
  "created": 1,
  "updated": 3,
  "errors": []
}
```

Tabla `whatsapp_synced_template`:

| Columna | Descripción |
|---------|-------------|
| `meta_template_id` (PK) | ID Meta — requerido para `template_analytics` |
| `name` | Nombre Meta (`payment_reminder_gigaperu`) |
| `status` | APPROVED, PAUSED, etc. |
| `quality_score` | GREEN / YELLOW / RED |
| `category` | UTILITY, MARKETING, etc. |

## Botones en plantillas Meta (URL / Quick Reply)

Para habilitar tracking de clicks en `template_analytics`:

1. En Meta Business Manager → WhatsApp Manager → Message templates → Editar plantilla.
2. Agregar componente **Buttons**:
   - **URL**: enlace externo (ej. portal de pagos). Meta reporta clicks por URL.
   - **Quick Reply**: respuestas predefinidas. Meta reporta clicks por botón.
3. Máximo 3 botones por plantilla (según categoría).
4. Tras aprobar, ejecutar `POST /whatsapp/templates/sync` para refrescar IDs y estado.
5. Verificar en hub → Métricas → panel "Clicks en plantillas (Meta)".

### Ejemplo payload plantilla con botón URL

```json
{
  "name": "payment_reminder_gigaperu",
  "language": "es_PE",
  "category": "UTILITY",
  "components": [
    { "type": "BODY", "text": "Hola {{1}}, su factura es {{2}}." },
    {
      "type": "BUTTONS",
      "buttons": [
        { "type": "URL", "text": "Pagar ahora", "url": "https://gigafiberperu.cloud/pagar" }
      ]
    }
  ]
}
```

## Salud de cuenta y alertas operativas

`GET /whatsapp/account/health` incluye:

- `qualityScore`, `phoneQuality`
- `messagingLimitTier` (código crudo de Meta, ej. `TIER_250`)
- `messagingLimit` (límite numérico diario resuelto para el tier vigente, ej. `250`)
- `messagingUsedToday` (mensajes con status `SENT` en las últimas 24h, proxy de conversaciones iniciadas por negocio)
- `pausedTemplates[]` con `{ code, name, reason }`
- `alerts[]` generadas por:
  - Calidad YELLOW / RED en plantillas
  - Tasa de fallo ≥ 20% en últimas 24h (≥ 10 envíos)
  - `MESSAGING_LIMIT_NEAR` (WARNING): uso ≥ 80% del límite diario vigente
  - `MESSAGING_LIMIT_REACHED` (CRITICAL): uso ≥ límite diario vigente

### Messaging limit tier y quality rating (Fase 2 — Fábrica 2026-08-04)

**Contexto confirmado por el usuario:** el número comercial está hoy en el tier **`TIER_250`** (250 conversaciones
iniciadas por negocio cada 24h). Al completar la **verificación de negocio (Business Verification)** en Meta
Business Manager, Meta amplía el límite a **2.000/día** (`TIER_2K`). Este cambio de tier es un **trámite externo,
no depende del código**; mientras Meta no confirme el nuevo tier vía la API, la alerta de uso sigue usando **250**
como límite vigente (resuelto dinámicamente a partir del valor real que devuelve Meta, no hardcodeado). Esto
explica la causa raíz de los envíos fallidos/no confirmados del 3 de agosto cuando una campaña superó las 250
conversaciones/día.

**Endpoint Meta usado:**

```http
GET https://graph.facebook.com/{api-version}/{phone-number-id}?fields=messaging_limit_tier,quality_rating
```

- Cliente: `WhatsAppMetaAnalyticsClient.fetchPhoneNumberHealth()`.
- Parsing puro y testeable: `WhatsAppMetaAnalyticsClient.parseMessagingLimitTier(node)` /
  `parseQualityRating(node)`.
- **Caché corta de 5 minutos** en memoria (por instancia de la app) para no golpear Meta en cada request de
  health; implementada en `fetchWithCache(now, fetcher)` (testeable con `Instant` inyectado, sin mocks de red).
- Consumido por `WhatsAppAccountEventService.getAccountHealth()`, que reemplaza el `null` hardcodeado anterior
  (línea ~82-83) por el valor real de Meta.

**Valores de tier soportados** (`WhatsAppMessagingLimitTiers.dailyLimitFor`):

| Tier Meta | Límite diario (conversaciones iniciadas por negocio) |
|-----------|-------------------------------------------------------|
| `TIER_50` | 50 |
| `TIER_250` | **250 (vigente hoy)** |
| `TIER_1K` | 1.000 |
| `TIER_2K` | **2.000 (tras completar Business Verification)** |
| `TIER_10K` | 10.000 |
| `TIER_100K` | 100.000 |

Tiers no reconocidos devuelven `messagingLimit = null` (sin alerta de uso, ya que no hay límite numérico
conocido contra el cual comparar).

**Cálculo de uso vs. límite:** `messagingUsedToday` cuenta mensajes con `status = SENT` en `whatsapp_message_log`
creados en las últimas 24h (mismo query ya usado para la tasa de fallo, sin duplicar consultas a BD). Se compara
contra `messagingLimit` para decidir las alertas `MESSAGING_LIMIT_NEAR` / `MESSAGING_LIMIT_REACHED` descritas
arriba.

**Frontend (`WhatsAppAccountHealthBanner.tsx`):**

- Muestra el límite en texto legible: `"Límite actual: 250 conversaciones/día"` en vez del código crudo
  `TIER_250` (mapeo por `messagingLimit` numérico, ya resuelto en backend).
- Muestra uso vs. límite: `"184/250 usadas hoy"`.
- Advertencia visual (WARNING, texto ámbar) cuando el uso alcanza ≥ 80% del límite.
- Advertencia crítica (texto rojo) cuando el uso alcanza o supera el límite.
- Prop opcional `pendingBulkRecipients` para que una futura pantalla de lanzamiento de campaña masiva pase el
  tamaño del envío pendiente; si excede el remanente del día (`límite - usado`), se muestra advertencia
  "El envío masivo pendiente (...) excedería el límite restante del día". **Nota:** esta prop no está aún
  conectada desde `WhatsAppMetricsTab.tsx` (fuera de alcance de esta fase, coordinar con la fase que integre el
  flujo de lanzamiento de campañas).
- Cuando Meta confirme el cambio a `TIER_2K` (verificación de negocio completada), el banner reflejará
  automáticamente "Límite actual: 2.000 conversaciones/día" sin cambios de código, ya que el valor viene en vivo
  de Meta (con caché de 5 min) y el mapeo de tiers ya contempla `TIER_2K`.

## Export CSV (Fase 4)

| Endpoint | Contenido |
|----------|-----------|
| `GET /whatsapp/logs/export` | Historial de envíos + atribución |
| `GET /whatsapp/analytics/campaigns/export` | Campañas + `conversionAmount` (acepta `templateCode`, `operatorUsername`, alias `from`/`to`) |
| `GET /whatsapp/inbound-messages/export` | Mensajes entrantes |

Detalle de filtros nuevos, semántica `accepted`/`confirmed`/`failed` por campaña, `estimatedMetaCost`/`roi` y serie temporal diaria: [`whatsapp-campaign-metrics-fases-1-5-8.md`](./whatsapp-campaign-metrics-fases-1-5-8.md).

## Límites operativos Cloud API (Fase 0)

Referencia operativa para CRM (no asumir capacidades no soportadas por Cloud API Business).

### Ventana de servicio 24h

- Se abre cuando el usuario escribe o llama al número business; cada mensaje/llamada del usuario la reinicia a 24h.
- **Dentro de la ventana:** mensajes de servicio free-form (texto, media, interactive, etc.).
- **Fuera de la ventana:** solo plantillas preaprobadas (`template`). Error típico Graph: `131047`.
- En backend: `WhatsAppServiceWindowService` (24h desde último inbound) y el composer del hub bloquea reply libre si `serviceWindowActive=false`.

### Plantillas

- Obligatorias fuera de la ventana 24h y para campañas proactivas.
- Deben estar `APPROVED` en el WABA; categorías `UTILITY` / `MARKETING` / `AUTHENTICATION`.
- Review puede tardar minutos–24h (AUTHENTICATION suele ser más rápido).
- Pausas/calidad: webhooks `message_template_status_update` / `message_template_quality_update`.
- Límite de creación: ~100 plantillas/hora por WABA; cupos totales dependen de verificación del portfolio.

### Media (tamaños y tipos soportados)

| Tipo | MIME / formatos | Máx. tamaño |
|------|-----------------|-------------|
| Imagen | `image/jpeg`, `image/png` | 5 MB |
| Video | `video/mp4`, `video/3gpp` | 16 MB |
| Audio | aac, amr, mp3, m4a, ogg (OPUS) | 16 MB |
| Documento | pdf, doc(x), xls(x), ppt(x), txt, … | 100 MB |
| Sticker | `image/webp` estático / animado | 100 KB / 500 KB |

- Tope absoluto de descarga entrante en Cloud API: **100 MB** (webhook error `131052` si el cliente envía más).
- Fase 3 habilita **media saliente** (imagen/documento/audio) con validación de tipo/tamaño (`WhatsAppMediaConstraints`) → upload Graph `/media` → send message.
- Stickers (`image/webp`), reacciones y typing indicators **no** están soportados como acciones de agente: Cloud API Business no expone typing; stickers/reacciones no se envían desde el hub.
- Reply-to saliente: campo `context.message_id` (wamid) en texto/media; no aplica a plantillas.
- Fuera de ventana 24h: solo plantillas aprobadas desde el hilo (`POST /whatsapp/conversations/{phone}/template`).

### Estados de mensaje (webhooks `statuses`)

| Status Meta | Significado |
|-------------|-------------|
| `sent` | Aceptado por WhatsApp / en camino al dispositivo |
| `delivered` | Entregado al dispositivo del usuario |
| `read` | Leído (si el usuario tiene receipts) |
| `failed` | Falló (incluye código/título en el webhook) |

Persistidos en `WhatsAppMessageLog` (`deliveryStatus`, timestamps `sentAt`/`deliveredAt`/`readAt`/`failedAt`). Idempotencia de eventos: `WhatsAppWebhookEvent.eventKey` unique.

### Implicaciones para fases siguientes

- CSAT y notificaciones proactivas (Fase 6) requieren plantillas aprobadas con antelación.
- Reply libre del operador (Fases 1–3) solo dentro de ventana 24h; fuera → UI debe ofrecer plantilla.
- Media saliente (Fase 3) debe validar tipo/tamaño de esta tabla antes de llamar a Meta.

## Atribución precisa de envíos: biz_opaque_callback_data (Fase 4 — 2026-08-04)

**Problema resuelto:** hasta ahora el `WhatsAppMessageLog` solo se ligaba al webhook de `statuses` por `wamid`
(`metaMessageId`). Si Meta demora en devolver el `wamid` en la respuesta del envío (timeout, reintento, etc.), el
webhook de status llega sin forma de encontrar el log correspondiente.

**Solución:** `WhatsAppTemplateDeliveryService.deliverTemplate` genera un `UUID` propio (`callbackToken`) **antes**
de llamar a Meta, lo envía en el campo `biz_opaque_callback_data` del body de envío, y lo persiste en
`WhatsAppMessageLog.callbackId` junto con el resultado del envío (éxito o fallo).

- Migración `V12__whatsapp_message_log_callback_id.sql` agrega `callback_id VARCHAR(64)` indexado a
  `whatsapp_message_log`.
- `WhatsAppMessageLogRepository.findByCallbackId(callbackId)` — nuevo lookup.
- `WhatsAppWebhookService.processStatusEvent`: primero busca por `findByMetaMessageId(wamid)`; si no encuentra,
  lee `biz_opaque_callback_data` del status y busca por `findByCallbackId(it)`. Si el log encontrado por `wamid`
  no tenía `callbackId` guardado (por ejemplo porque el log se creó antes de esta fase), lo rellena (`backfill`)
  con el valor recibido en el webhook.
- `biz_opaque_callback_data` se serializa como `String?` con `@JsonInclude(NON_NULL)` en
  `WhatsAppTemplateMessageBody`, `WhatsAppTextMessageBody` y `WhatsAppMediaMessageBody` — nunca se envía el campo
  si es `null`.
- `WhatsAppService.sendTemplateMessageWithMetaResponse` / `sendTemplateMessage` aceptan `callbackToken: String? = null`.

**Fallback:** si Meta no ecoa `biz_opaque_callback_data` en el status (webhooks antiguos o algún edge case), el
lookup por `wamid` sigue funcionando igual que antes; el `callbackId` es un mecanismo adicional, no un reemplazo.

## Cambios de límites de cuenta: business_capability_update (Fase 3 — 2026-08-04)

`WhatsAppWebhookService.processManagementFields` delega `business_capability_update` a
`WhatsAppAccountEventService.recordManagementEvent(field, value)`, igual que el resto de campos de gestión
(`message_template_status_update`, `account_alerts`, etc.). El payload típico incluye
`max_daily_conversation_per_phone` / `max_phone_numbers_per_business`; queda persistido como evento consultable
en el mismo mecanismo de health/alerts que ya usan los demás webhooks de gestión.

Ver nota operativa de suscripción manual del campo arriba (sección Webhook).

## Botones dinámicos en plantillas — Quick Reply URL (Fase 6 — 2026-08-04)

Además del componente `BUTTONS` estático descrito arriba (creado en Meta Business Manager), el backend soporta
**parámetros dinámicos de botón URL** por plantilla:

- `WhatsAppTemplateCatalog`: cada `WhatsAppTemplateDefinition` puede declarar `buttonParameter:
  WhatsAppTemplateButtonDef?` con `index`, `subType` (`"url"`) y `source` (`TemplateParameterSource`, incluye
  `PAYMENT_ID` para enlaces de pago con el id de la factura como sufijo dinámico).
- `TemplateParameterResolver.resolveButtonParameter(...)` resuelve el parámetro dinámico igual que los parámetros
  de `BODY`, devolviendo `null` si la plantilla no declara botón.
- `WhatsAppTemplateDeliveryService.deliverTemplate` arma el `WhatsAppTemplateButtonParameter` (si aplica) y lo pasa
  a `WhatsAppService.sendTemplateMessageWithMetaResponse`, que agrega un componente `{"type":"button","sub_type":
  "url","index":"N","parameters":[...]}` al body de Meta.
- `WhatsAppTemplateComponent` ahora incluye `sub_type`/`index` opcionales (`@JsonInclude(NON_NULL)`), solo se
  serializan cuando el componente es de tipo `button`.
- Inbound: `WhatsAppInboundPayloadParser` maneja `type = "button"` (click en botón de plantilla desde el
  celular del cliente), extrayendo `buttonReplyId` (`button.payload`) y `buttonReplyTitle` (`button.text`) igual
  que las respuestas interactivas (`button_reply`/`list_reply`).

**Nota operativa obligatoria:** cualquier plantilla con botón dinámico debe estar **APPROVED** en Meta Business
Manager (con el componente `BUTTONS` ya creado y aprobado ahí) antes de poder activarse en el catálogo backend
(`buttonParameter` en `WhatsAppTemplateCatalog`). El backend no crea ni edita botones en Meta, solo rellena el
parámetro dinámico de un botón URL ya existente y aprobado.

Ninguna de las 4 plantillas actuales del catálogo (`payment_reminder_gigaperu`, `payment_validation_gigaperu`,
`service_cut_notice_gigaperu`, `welcome_customer_gigaperu`) tiene `buttonParameter` configurado todavía; la
infraestructura queda lista para cuando se apruebe una plantilla con botón URL dinámico en Meta.

## Opt-out de marketing: user_preferences (Fase 7 — 2026-08-04)

Meta notifica cuando un usuario responde "STOP"/similar (o vuelve a aceptar) mensajes de marketing vía el webhook
`user_preferences`. El backend:

- `WhatsAppTemplateCatalog`: cada `WhatsAppTemplateDefinition` tiene `category: WhatsAppTemplateCategory`
  (`MARKETING` / `UTILITY` / `AUTHENTICATION`), por defecto `UTILITY`. Ninguna plantilla actual es `MARKETING`.
- Migración `V13__whatsapp_marketing_optout.sql` crea `whatsapp_marketing_optout` (`phone` único, `category`,
  `status` `OPTED_OUT`/`RESUMED`) + `WhatsAppMarketingOptOutRepository`.
- `WhatsAppWebhookService.processUserPreferencesField` itera `value.user_preferences[]`, extrae `wa_id`,
  `category` y `value` (`stop`/`resume`), y crea/actualiza el registro de opt-out por teléfono (dígitos del
  `wa_id`, formato internacional tal cual lo envía Meta).
- **Gate de envío:** `WhatsAppTemplateDeliveryService.deliverTemplate`, si la plantilla es `MARKETING`, normaliza
  el teléfono destino a formato internacional y consulta `WhatsAppMarketingOptOutRepository.findByPhone(...)`. Si
  está `OPTED_OUT`, persiste el `WhatsAppMessageLog` con `status = SKIPPED` y `errorMessage` con el motivo, y
  lanza `IllegalStateException` (mismo patrón que "ya se envió hoy") para que `WhatsAppBackofficeMessageService`
  y demás orquestadores lo reporten como omitido sin intentar el envío a Meta.
- **v1 sin UI dedicada:** no hay pantalla en el backoffice para ver/gestionar opt-outs manualmente; solo el gate
  de envío y la persistencia vía webhook.

Ver nota operativa de suscripción manual del campo `user_preferences` arriba (sección Webhook).

## Migraciones Flyway relevantes a estas fases

| Migración | Contenido |
|-----------|-----------|
| `V12__whatsapp_message_log_callback_id.sql` | `callback_id VARCHAR(64)` + índice en `whatsapp_message_log` (Fase 4) |
| `V13__whatsapp_marketing_optout.sql` | Tabla `whatsapp_marketing_optout` (Fase 7) |

## Referencias

- [Meta — Webhooks](https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks)
- [Meta — Media](https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media)
- [Meta — Send messages / service window](https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-messages)
- [Meta — Message templates](https://developers.facebook.com/docs/whatsapp/business-management-api/message-templates)
- [Meta — Template analytics](https://developers.facebook.com/docs/whatsapp/business-management-api/analytics)
- Operación recordatorios: `WHATSAPP_REMINDERS_README.md`
- UX hub frontend: `ispadmin-backoffice/.agent-docs/whatsapp-hub-ux.md`
- Fase 0 CRM: [`crm-omnicanal-fase0-seguridad-fundaciones.md`](./crm-omnicanal-fase0-seguridad-fundaciones.md)
