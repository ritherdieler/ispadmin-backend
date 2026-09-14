# Deploy producción — CRM WhatsApp omnicanal (2026-08-02)

## Backend

- Comando: `./scripts/deploy.sh --deploy`
- Release: **1.0.3+7952b9d**
- Commit: `7952b9d`
- Tomcat: `tomcat9027`, `APP_RELEASE` en `/opt/gigafiber/.env`
- Smoke: `GET https://api.gigafiberperu.cloud/ispadmin/` → HTTP 200

## Post-deploy 2026-08-02 (tarde)

- Añadido `CRM_SECRETS_MASTER_KEY` en `/opt/gigafiber/.env` (backup `.env.bak.*`).
- Tomcat recreado; redeploy WAR con `./scripts/deploy.sh --war-only` si aplica.

## Backoffice

Ver `ispadmin-backoffice/.agent-docs/deploy-prod-crm-whatsapp-2026-08-02.md`.
