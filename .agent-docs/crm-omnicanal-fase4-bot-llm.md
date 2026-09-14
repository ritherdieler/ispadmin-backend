# CRM Omnicanal WhatsApp — Fase 4: Bot 2.0 + LLM

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Objetivo

Configurar OpenAI desde el backoffice (secreto cifrado), clasificar intenciones con LLM + fallback a reglas, generar resumen al handoff, sugerir respuestas con confirmación humana, y endurecer transferencias (frustración / baja confianza / reintentos / fuera de horario).

## Modelo y config

Migración: `V7__whatsapp_crm_fase4_llm.sql`

| Pieza | Rol |
|-------|-----|
| `crm_integration_setting` | key/value; API key en `value_ciphertext` (AES-GCM) |
| `crm_conversation.handoff_summary` | Resumen para el agente al pasar a humano |
| `crm.llm.*` | `master-key`, bootstrap, modelo, umbral, retries, timeout |
| Env | `CRM_SECRETS_MASTER_KEY`, opcional `OPENAI_API_KEY` bootstrap |

## Endpoints

### OpenAI settings (solo ADMIN)

- `GET /crm/settings/openai` → `configured`, `maskedApiKey` (`sk-...xxxx`), `model`, `enabled`, metadatos. **Nunca** token completo.
- `PUT /crm/settings/openai` → `{ apiKey?, model?, enabled? }` (apiKey vacío borra; omitido conserva).
- `POST /crm/settings/openai/test` → prueba chat completions.

### Asistencia agente (`SECRETARY|ADMIN`)

- `POST /crm/conversations/{id}/suggest-reply` → sugerencia; `requiresConfirmation=true` (no envía).
- `POST /crm/conversations/{id}/pause-bot` / `resume-bot`

## Flujo LLM

```text
Inbound text → WhatsAppIntentClassifier
  → reglas (keywords/botones) si intent != UNKNOWN
  → LlmClient.classifyIntent si UNKNOWN y LLM enabled
  → umbral confidence (default 0.65)
  → fallback UNKNOWN + contador unknown_retries en chat_state.metadata
  → al alcanzar max retries → HUMAN_ESCALATION
Handoff → LlmClient.summarizeHandoff → CrmConversation.handoffSummary
Auditoría → WhatsAppAuditLog action=BOT_TRACE (intent/source/confidence)
```

`LlmClient` cachea runtime config vía `CrmOpenAiSettingsService` (invalidación al guardar). Sin key o disabled → solo reglas / fallbacks.

## Guardrails

- Prompts: no inventar datos; no exponer secretos; UNKNOWN si inseguro.
- GET/auditoría enmascaran / redactan API keys.
- Sugerencias nunca se autoenvían.

## Tests

```text
CrmSecretCipherTest
CrmOpenAiSettingsServiceTest
CrmOpenAiSettingsControllerTest
LlmClientTest
WhatsAppIntentClassifierTest
CrmAccessPolicyTest (canManageCrmSecrets)
CrmConversationServiceTest
WhatsAppHandoffServiceTest
WhatsAppInboundMessageServiceTest
```

## Fuera de alcance

Fase 3 media/QuickReply/plantillas en hilo; Fases 5–7 tickets/CSAT/métricas.
