# Deploy prod: WhatsApp batch concurrency (2026-08-06)

| Componente | Valor |
|------------|--------|
| Release | `1.0.3+cdeb607` (`cdeb607`) |
| Comando | `./scripts/deploy.sh --deploy` |
| Tomcat | `tomcat9027` recreado |
| HTTP | `GET /ispadmin/` → 200 |
| Observability | Deploy event registrado |

## Cambio desplegado

- Envío masivo backoffice con `whatsapp.backoffice.batch-concurrency=8`
- Fallos parciales por destinatario sin abortar el lote
- Detalle `clientName` en respuesta de `POST /whatsapp/messages/selected`
