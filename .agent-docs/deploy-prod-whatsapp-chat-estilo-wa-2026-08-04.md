# Deploy prod — Chat WhatsApp estilo WA — 2026-08-04

## Backend (`ispadmin-backend`)

| Campo | Valor |
|-------|-------|
| Rama / commit | `develop` / `ea41a2e` |
| Release | `1.0.3+ea41a2e` |
| Deploy | `./scripts/deploy.sh --deploy` OK |
| Tomcat | `tomcat9027` respondió HTTP 200 en `/ispadmin/` |
| Nota local | Primer intento falló por disco lleno (`No space left on device` en repackage); se liberó espacio y se reintentó |

### Incluye

- `GET /whatsapp/conversations/{phone}/thread?limit&before` (página con `hasMore` / `nextBefore`)
- `Content-Disposition: inline` para media image/audio

## Backoffice (`ispadmin-backoffice`)

| Campo | Valor |
|-------|-------|
| Rama / commit | `develop` / `2e2c286` |
| Build | `npm run build -- --mode production` OK |
| Deploy | `rsync -avz --delete dist/ root@212.85.13.47:/var/www/gigafiber/backoffice/` OK |
| Chunk chat | `WhatsAppConversationsTab-DMt0EUC6.js` |

## Smoke post-deploy

| URL | Esperado |
|-----|----------|
| `https://api.gigafiberperu.cloud/ispadmin/` | 200 |
| `https://backoffice.gigafiberperu.cloud/` | 200 |
| `https://backoffice.gigafiberperu.cloud/assets/WhatsAppConversationsTab-DMt0EUC6.js` | 200 |

## Verificación manual sugerida

1. Backoffice → WhatsApp → Conversaciones: abrir un hilo, wallpaper/burbujas, ticks, composer con emojis.
2. Scroll hacia arriba: carga de mensajes antiguos (`hasMore`).
3. Imagen/audio: preview/reproducción inline (no solo descarga).
