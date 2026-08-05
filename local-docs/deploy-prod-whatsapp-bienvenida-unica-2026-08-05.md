# Deploy producción — bienvenida WhatsApp única (2026-08-05)

## Resumen

| Componente | Release | Resultado |
|------------|---------|-----------|
| Backend | `1.0.3+6555123` (`6555123`) | `./scripts/deploy.sh --deploy` OK, Tomcat `tomcat9027` |

## Cambio

- Eliminado el segundo disparo de bienvenida en `SubscriptionController`.
- Un solo envío vía `WhatsAppWelcomeRegistrationListener` + `SubscriptionRegisteredEvent` (after commit).

## Comando

```bash
cd ispadmin-backend
./scripts/deploy.sh --deploy
```

## Smoke post-deploy

| URL | HTTP |
|-----|------|
| https://api.gigafiberperu.cloud/ispadmin/ | 200 |

## Verificación operativa

Tras el próximo alta con celular válido, en prod:

```sql
SELECT subscription_id, COUNT(*) FROM whatsapp_message_log
WHERE message_type = 'WELCOME_CUSTOMER' AND status = 'SENT' AND subscription_id = ?
GROUP BY subscription_id;
```

Debe devolver `1` fila por suscripción.
