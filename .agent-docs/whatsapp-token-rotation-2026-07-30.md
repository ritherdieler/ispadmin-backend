# Rotación token WhatsApp — 2026-07-30

## Contexto

Prod fallaba al enviar mensajes con:

```text
OAuthException code 200
Cannot call API for app 4544850425759733 on behalf of user 122114877243385371
```

El token anterior era System User válido de la app correcta, pero con `scopes: []`.

## Acción

1. Generar nuevo System User token con scopes WhatsApp.
2. Actualizar:
   - Dev: `src/main/resources/application-local.properties` (gitignored)
   - Prod: `/opt/gigafiber/.env` → `WHATSAPP_ACCESS_TOKEN`
3. Recargar Tomcat (`docker compose up -d --force-recreate tomcat`) y restaurar WAR desde `/tmp/ispadmin.war` (el recreate no conserva el WAR en `webapps/`).

## Validación Meta (prod)

| Check | Resultado |
|-------|-----------|
| `debug_token` `is_valid` | `true` |
| `type` | `SYSTEM_USER` |
| `app_id` | `4544850425759733` (GigafiberPeru) |
| scopes | incluye `whatsapp_business_messaging` y `whatsapp_business_management` |
| `GET /{phone-number-id}` | `200` — `+51 984 224 137` / `GigaFiberPeru-Mensajes` |
| `GET /ispadmin/` en VPS | `200` |

## Nota operativa

Para rotar solo el token en prod **sin perder el WAR**, preferir:

```bash
# editar /opt/gigafiber/.env
cd /opt/gigafiber && docker compose restart tomcat
```

Usar `--force-recreate` solo si también se vuelve a hacer `docker cp` del WAR.
