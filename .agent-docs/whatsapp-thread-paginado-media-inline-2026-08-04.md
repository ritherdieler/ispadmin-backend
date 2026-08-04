# WhatsApp thread paginado + media inline (2026-08-04)

## Contrato

`GET /whatsapp/conversations/{phone}/thread`

| Query | Default | Descripción |
|-------|---------|-------------|
| `limit` | `50` | Tamaño de página (1–200) |
| `before` | — | ISO `LocalDateTime`; mensajes con `createdAt < before` |
| `dateFrom` / `dateTo` (o `from` / `to`) | — | Filtro opcional de rango |

Respuesta `WhatsAppThreadPageDto`:

```json
{
  "messages": [ /* WhatsAppThreadMessageDto orden ascendente */ ],
  "hasMore": true,
  "nextBefore": "2026-08-04T10:00:00"
}
```

Sin `before`: últimos `limit` mensajes. Con `before`: página anterior. `hasMore` se calcula pidiendo `limit + 1` a inbound/outbound y mergeando.

Implementación: `WhatsAppConversationQueryService.getThread`, repos `findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc`.

## Media inline

`WhatsAppMediaContentDisposition.forMimeType`:

- `image/*` y `audio/*` → `Content-Disposition: inline; filename="…"`
- resto (PDF, docs) → `attachment`

Usado en descarga inbound/outbound del backoffice controller para preview/reproducción en el navegador.

## Tests

- `WhatsAppConversationQueryServiceTest` — merge, pageable, `before`/`hasMore`
- `WhatsAppMediaContentDispositionTest`
