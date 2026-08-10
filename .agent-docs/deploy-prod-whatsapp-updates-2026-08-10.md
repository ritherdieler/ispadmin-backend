# Deploy prod — WhatsApp updates (quick replies + comprobantes) — 2026-08-10

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+099a3a4` | `./scripts/deploy.sh --deploy` desde `develop` |
| Backoffice | `9ac4ead` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

Incluye merge de `feature/whatsapp-updates` (quick replies / composer UX) sobre comprobantes + `resolvedBy`, con migración Flyway `V17__whatsapp_quick_reply.sql` (renombrada para no chocar con `V16__crm_conversation_resolved_by`).

## Smoke

- https://api.gigafiberperu.cloud/ispadmin/ → HTTP 200
- https://api.gigafiberperu.cloud/ispadmin/actuator/health → HTTP 200
- https://backoffice.gigafiberperu.cloud/ → HTTP 200 (bundle `index-Df5wMjER.js`)
- `GET /whatsapp/quick-replies` sin token → HTTP 401 (endpoint vivo)
- Tomcat: `Started WispAdminApplication`
