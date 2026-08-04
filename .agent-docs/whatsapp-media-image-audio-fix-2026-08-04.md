# Fix media imagen/audio en chat WhatsApp — 2026-08-04

## Problema (prod)

En Conversaciones, imagen entrante se veía como “Documento” y audio solo como texto `[audio]` sin reproductor.

## Causas

1. **Audio**: `WhatsAppInboundPayloadParser` no parseaba `audio`/`sticker`/`video` → sin `mediaId` ni descarga local (ej. inbound `188` en prod: `message_type=audio`, sin path).
2. **Imagen en tiempo real**: evento `MESSAGE_RECEIVED` no incluía `mediaMimeType`; el front solo clasificaba por MIME → caía a tarjeta Documento. Fallback por `messageType` ausente.

## Cambios

### Backend

- Parser: `audio`, `sticker`, `video`.
- Payload realtime: `mediaMimeType`.
- Extensiones de almacenamiento para audio/video; `Content-Disposition` ignora parámetros `; codecs=…`.

### Backoffice

- `resolveThreadMediaKind` / `resolveThreadMediaMime` (MIME + `messageType` + blob).
- Realtime propaga `mediaMimeType`.
- No muestra `[image]`/`[audio]` cuando hay media sin caption.

## Persistencia en VPS

- Volumen: host `/opt/gigafiber/data/whatsapp/media` → contenedor `/var/lib/gigafiber/whatsapp/media`
- Env: `WHATSAPP_MEDIA_STORAGE_DIR` (también en `application-prod.properties`)
- Imagen inbound `191` re-descargada desde Meta tras el wipe del filesystem efímero del contenedor

## Nota sobre mensajes ya recibidos

Audios antiguos sin `media_id`/`media_stored_path` (ej. inbound `188`) no se pueden reproducir. Hay que reenviar el audio tras el deploy.
