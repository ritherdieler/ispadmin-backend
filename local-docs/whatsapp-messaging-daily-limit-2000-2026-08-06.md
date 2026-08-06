# WhatsApp — límite diario operativo 2000 (2026-08-06)

## Contexto

Graph API seguía devolviendo `messaging_limit_tier=TIER_250` (250/día) mientras el negocio ya opera con cupo de **2000** mensajes/día (uso real >250 sin rechazo de Meta).

## Cambio

| Clave | Valor |
|-------|--------|
| `whatsapp.messaging-daily-limit-override` | `2000` (dev + prod) |

- Si override `> 0`, `GET /whatsapp/account/health` usa ese número para `messagingLimit` y alertas de cupo.
- El tier crudo de Meta (`messagingLimitTier`) se sigue exponiendo sin alterar.

## Código

- `WhatsAppProperties.messagingDailyLimitOverride`
- `WhatsAppMessagingLimitTiers.resolveDailyLimit`
- `WhatsAppAccountEventService.getAccountHealth`
