# Fix migración duplicada V16 tras merge whatsapp-updates — 2026-08-10

Tras integrar `feature/whatsapp-updates` en `develop`, coexistían:

- `V16__crm_conversation_resolved_by.sql`
- `V16__whatsapp_quick_reply.sql`

Flyway no admite dos scripts con la misma versión. Se renombró quick replies a `V17__whatsapp_quick_reply.sql`.
