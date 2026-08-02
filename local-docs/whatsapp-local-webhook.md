# WhatsApp — prueba local del webhook

## Objetivo

Probar las auto-respuestas nuevas contra tu backend en `localhost:8080` (BD `ispadmin_dev`), no contra producción.

## 1. Reiniciar backend

Perfil: `dev,local` (ya en `application.properties`).

Confirma en consola Spring que cargó:
- `whatsapp.webhook-signature-required=false`
- `whatsapp.webhook-verify-token=gigafiber_whatsapp_verify_2026`

## 2. Opción A — Simular sin Meta (más rápido)

```powershell
cd C:\Users\EDWIN\OneDrive\Documentos\wispadministrator-main
powershell -ExecutionPolicy Bypass -File scripts\whatsapp-simulate-inbound.ps1 -Text "cuanto es mi deuda" -Phone "51902354183"
powershell -ExecutionPolicy Bypass -File scripts\whatsapp-simulate-inbound.ps1 -ButtonId "ver_deuda" -Phone "51902354183"
powershell -ExecutionPolicy Bypass -File scripts\whatsapp-simulate-inbound.ps1 -Text "ok" -Phone "51902354183"
powershell -ExecutionPolicy Bypass -File scripts\whatsapp-simulate-inbound.ps1 -Text "tengo el internet lento" -Phone "51902354183"
```

Revisa:
- Logs del backend (`[DEUDA]`, `[ACK]`, `[AVERIA]`, `[MENU]`)
- Tabla `whatsapp_message_log` status `AUTO_REPLY`
- WhatsApp del número `902354183` (si Meta acepta el envío outbound)

## 3. Opción B — WhatsApp real vía túnel

```powershell
powershell -ExecutionPolicy Bypass -File scripts\whatsapp-local-tunnel.ps1
```

Copia la URL `https://....trycloudflare.com`.

En [Meta for Developers](https://developers.facebook.com/) → tu app → **WhatsApp** → **Configuration**:

| Campo | Valor |
|-------|--------|
| Callback URL | `https://TU-URL.trycloudflare.com/ispadmin/whatsapp/webhook` |
| Verify token | `gigafiber_whatsapp_verify_2026` |

Suscribe el campo `messages`. Guarda. Meta debe verificar con HTTP 200.

Luego escribe al número de Cobranza desde el celular de prueba.

## 4. Al terminar (muy importante)

Restaura el Callback de producción:

`https://api.gigafiberperu.cloud/ispadmin/whatsapp/webhook`

Si no lo haces, los clientes reales dejan de alimentar el backend de prod.

## Notas

- `application-local.properties` está en `.gitignore` (no se sube).
- Con `webhook-signature-required=false` solo en local: Meta/scripts pueden POST sin App Secret.
- En producción deja la firma exigida (`true` / default).
