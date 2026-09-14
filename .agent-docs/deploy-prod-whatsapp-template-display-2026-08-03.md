# Deploy producción — WhatsApp texto plantillas en chat (2026-08-03)

| Componente | Ref | Resultado |
|------------|-----|-----------|
| Backend | `1.0.3+00d8825` (`develop` / `00d8825`) | `./scripts/deploy.sh --deploy` OK |

Smoke: `GET https://api.gigafiberperu.cloud/ispadmin/` HTTP 200.

Incluye migración Flyway `V11` (`whatsapp_synced_template.body_text`), render de plantillas en hilo, sync BODY al arrancar/enviar y fallback legible.

Commit local en `develop` (1 ahead de `origin/develop`); push a remoto pendiente si se desea alinear repo.
