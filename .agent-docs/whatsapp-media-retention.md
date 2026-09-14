# Retención de archivos media WhatsApp

## Ubicación

| Entorno | Ruta |
|---------|------|
| Prod (Tomcat) | `/var/lib/gigafiber/whatsapp/media` (`WHATSAPP_MEDIA_STORAGE_DIR`) |
| Dev | `./data/whatsapp/media` |

Entrantes: raíz del directorio. Salientes operador: `media/outbound/`.

## Política (TTL)

| Clase | Retención | Excepciones |
|-------|-----------|-------------|
| Comprobante (imagen o PDF) | 730 días desde `created_at` del inbound | No purgar si CRM ≠ `RESOLVED`, comprobante posterior a `resolved_at`, `hasPendingReceipt`, o archivo &lt; `min-age-days` |
| Otro media inbound | 90 días después de `crm_conversation.resolved_at` | Si nunca resuelta: máx. 365 días desde `created_at` |
| Outbound enviado OK | 60 días desde `sent_at` (o `created_at`) | Mínimo 7 días de vida |
| Outbound fallido | 30 días desde `failed_at` (o `created_at`) | Mínimo 7 días de vida |

`hasPendingReceipt` usa la misma regla que la bandeja Comprobantes (`WhatsAppInboxViewPolicy` + último media imagen/PDF por teléfono).

Tras purga: se borra el archivo en disco, `media_stored_path` → NULL, `media_purged_at` → timestamp. El hilo conserva metadatos; la API expone `mediaExpired=true` y descarga devuelve HTTP 410.

## Configuración

Propiedades (`whatsapp.retention.*`):

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `enabled` | `true` | Activa el job nocturno |
| `payment-proof-days` | `730` | Comprobantes |
| `general-inbound-days` | `90` | Resto inbound post-resolve |
| `unresolved-inbound-max-days` | `365` | Tope inbound sin resolver |
| `outbound-days` | `60` | Media saliente OK |
| `outbound-failed-days` | `30` | Media saliente fallida |
| `min-age-days` | `7` | Edad mínima antes de cualquier purge |

Job: cron `0 30 3 * * *`, zona `America/Lima` (`WhatsAppMediaRetentionScheduler`).

## Backup (prod)

Incluir en respaldo del VPS el volumen/host path montado en Tomcat para `WHATSAPP_MEDIA_STORAGE_DIR`, con ventana ≥ 24 meses si se conservan comprobantes 730 días. Ver `.agent-docs/whatsapp-media-storage-backup.md`.

## Referencias

- `WhatsAppMediaRetentionPolicy`, `WhatsAppMediaRetentionService`
- Detección comprobante: `WhatsAppThreadMessageMapper.inboundIsPaymentProof`
