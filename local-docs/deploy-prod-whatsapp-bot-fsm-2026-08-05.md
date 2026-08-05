# Deploy producción — bot WhatsApp menú + FSM + auto-resume 30m (2026-08-05)

## Resumen

| Componente | Release | Resultado |
|------------|---------|-----------|
| Backend | `1.0.3+c21c23d` (`c21c23d`) | `./scripts/deploy.sh --deploy` OK, Tomcat `tomcat9027` |

## Cambios incluidos

- Menú principal en lista con 4 opciones; label **Registrar pago** (`enviar_comprobante`).
- Flujo `AWAITING_PAYMENT_PROOF` (espera imagen/PDF + recordatorio).
- `WhatsAppConversationStateMachine` como tabla de transiciones del bot.
- Auto-reanudación tras espera de asesor: **30 minutos** (`advisorWaitTimeoutMinutes`).
- Validación Meta, footer, typing, selección por texto en menús pendientes.
- Comprobante global (imagen/PDF) aunque el bot esté pausado.

## Commits

- `c27b0d4` feat(whatsapp): menú Registrar pago, máquina de estados y auto-resume 30m
- `c21c23d` docs: indexar docs del bot WhatsApp en agent-docs

## Comando

```bash
cd ispadmin-backend
./scripts/deploy.sh --deploy
```

## Smoke post-deploy

| URL | HTTP |
|-----|------|
| https://api.gigafiberperu.cloud/ispadmin/ | 200 |

Log del script: `Application is responding (HTTP 200)`. Registro de release en Observability: warning no fatal.

## Verificación operativa sugerida

1. Escribir al bot de producción → debe abrir lista con **Registrar pago**.
2. Elegir **Registrar pago** → pedir foto/PDF; enviar imagen → ack de comprobante.
3. Pedir asesor, esperar >30 min y volver a escribir → bot debe reanudarse con menú.
