# CRM Omnicanal WhatsApp — Fase 0: Seguridad y fundaciones

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Objetivo

Cerrar huecos de seguridad y deuda que bloquean escala antes de tiempo real, claim CRM y LLM.

## Autorización por rol

- Política: `CrmAccessPolicy` — roles permitidos `SECRETARY` y `ADMIN` (`User.UserType`).
- Enforcement en `PlatformAuthFilter` tras validar el bearer:
  - rutas `/whatsapp/**` (excepto `/whatsapp/webhook`) → 403 si el rol no está permitido;
  - mismo patrón preparado para futuros `/crm/**`;
  - sin token → 401 (comportamiento previo);
  - webhook Meta sigue público (firma HMAC propia).
- Tests: `CrmAccessPolicyTest`, `PlatformAuthFilterWhatsAppRoleTest`.

## Auditoría básica

- Entidad `WhatsAppAuditLog` / tabla `whatsapp_audit_log`.
- Servicio `WhatsAppAuditService` con acciones:
  - `ACCESS` — listado de conversaciones e hilo;
  - `REPLY` — respuesta de operador (guarda `textLength`, no el cuerpo ni tokens);
  - `MARK_READ` — mark-read / mark-all-read.
- Cableado en `WhatsAppBackofficeController` con `operatorUsername` desde `PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE`.
- Sanitización de detalles: redacta patrones `Bearer …` y `*token*` / `app_secret` / `authorization`.

## Fixes de escala

| Deuda | Cambio |
|-------|--------|
| N+1 service window en `listConversations` | `WhatsAppServiceWindowService.getServiceWindows(phones)` con `findAllById` en batch |
| Hilo carga todo y hace `take` | `getThread` usa `Pageable` (`findByPhoneOrderByCreatedAtDesc`) en inbound/outbound, merge y limita en BD |
| `metaMessageId` sin unique en logs | Unique nullable en `WhatsAppMessageLog.metaMessageId` + índices phone/created; SQL `V4__whatsapp_crm_fase0.sql` deduplica antes del índice |

## Migración SQL

Archivo: `src/main/resources/db/migration/V4__whatsapp_crm_fase0.sql`

Flyway no está activo en este proyecto (`ddl-auto=update`). Aplicar el SQL manualmente en prod/staging **antes** o al desplegar si hay riesgo de duplicados en `meta_message_id`. Hibernate creará/ajustará columnas e índices en entornos con `ddl-auto=update`; el script deja el estado explícito y seguro para MySQL.

## Documentación Cloud API

Capacidades/límites reales (media, ventana 24h, plantillas, estados): ver ampliación en [`whatsapp-meta-cloud-api.md`](./whatsapp-meta-cloud-api.md) sección «Límites operativos Cloud API (Fase 0)».

## Tests ejecutados

```text
CrmAccessPolicyTest
PlatformAuthFilterWhatsAppRoleTest
WhatsAppAuditServiceTest
WhatsAppServiceWindowServiceBatchTest
WhatsAppConversationQueryServiceTest
WhatsAppMessageLogUniqueMetaMessageIdTest
PlatformAuthFilterNetDiagExclusionTest
WhatsAppConversationServiceTest
WhatsAppBackofficeMessageServiceTest
```

Resultado: verde.

## Fuera de alcance (Fases 1+)

- STOMP `/topic/whatsapp`, `CrmEventLog`, catch-up
- `CrmConversation` claim/transfer/resolve
- Media saliente, plantillas en hilo, LLM, CSAT, métricas
