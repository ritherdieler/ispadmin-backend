# Deploy prod — fix media imagen/audio WhatsApp — 2026-08-04

| Repo | Commit / release | Método |
|------|------------------|--------|
| Backend | `62ccab8` → `1.0.3+62ccab8` | `./scripts/deploy.sh --deploy` OK |
| Backoffice | `d5d3489` | build + rsync OK (`WhatsAppConversationsTab-BhNEHf-P.js`) |

Smoke: API `/ispadmin/` 200, health UP, chunk chat 200.

Detalle técnico: `.agent-docs/whatsapp-media-image-audio-fix-2026-08-04.md`.
