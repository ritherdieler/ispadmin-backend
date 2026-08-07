# Deploy prod — develop WhatsApp mutations (2026-08-07)

| Campo | Valor |
|-------|--------|
| Versión | `1.0.3+42dc522` |
| Rama | `develop` |
| Comando | `./mvnw clean test` (881 tests OK) → `./scripts/deploy.sh --deploy` |
| Tomcat | `tomcat9027` |
| Smoke | `GET /ispadmin/` → HTTP 200 |

## Contenido relevante

- Merge `feature/whatsapp-assignee-thread-templates` (editar/eliminar/reacciones, `V14__whatsapp_message_reactions_edits.sql`).
- Commit de tests: mocks handoff CRM (`queryVariants` teléfono) y expectativa SSH fast-fail.

## Notas

- El script de deploy exige working tree limpio; los fixes de test van en `42dc522`.
