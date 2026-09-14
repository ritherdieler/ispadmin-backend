# Backup: almacenamiento media WhatsApp (prod)

## Montaje Docker (confirmado VPS)

| Host (VPS) | Contenedor (Tomcat) |
|------------|---------------------|
| `/opt/gigafiber/data/whatsapp/media` | `/var/lib/gigafiber/whatsapp/media` |

Variable de entorno en prod: `WHATSAPP_MEDIA_STORAGE_DIR=/var/lib/gigafiber/whatsapp/media` (ruta **dentro** del contenedor).

Estructura:

- Entrantes: archivos en la raíz del directorio.
- Salientes operador: subcarpeta `outbound/`.

## Requisito de backup

La política de retención conserva comprobantes hasta **730 días** ([whatsapp-media-retention.md](./whatsapp-media-retention.md)). El backup del host path debe cubrir al menos esa ventana (ideal: **24 meses + margen**).

Incluir en respaldo periódico del VPS:

```
/opt/gigafiber/data/whatsapp/media/
```

No commitear contenido de esta carpeta en git.

## Restauración

1. Restaurar archivos en `/opt/gigafiber/data/whatsapp/media` preservando permisos de lectura para el usuario del contenedor Tomcat.
2. Verificar que `whatsapp_inbound_message.media_stored_path` / `whatsapp_message_log.media_stored_path` apunten a rutas absolutas válidas dentro del contenedor (`/var/lib/gigafiber/whatsapp/media/...`).
3. Registros con `media_purged_at` no tienen archivo; no esperar preview en backoffice.

## Verificación rápida

```bash
docker inspect tomcat9027 --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{"\n"}}{{end}}' | grep whatsapp
du -sh /opt/gigafiber/data/whatsapp/media
```
