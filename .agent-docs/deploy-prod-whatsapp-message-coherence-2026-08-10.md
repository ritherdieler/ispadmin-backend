# Deploy prod — coherencia mensajes bot WhatsApp — 2026-08-10

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+21daf98` | `./scripts/deploy.sh --deploy` + push `develop` |

## Incluye

- After-hours compacto (sin redundancia)
- ACK genérico fuera de horario sin “caso registrado”
- Soft-ack post-voucher silenciado fuera de horario
- Deuda / comprobante / handoff en usted; menú asesor “Le deriva…”

## Smoke

- https://api.gigafiberperu.cloud/ispadmin/ → HTTP 200
- https://api.gigafiberperu.cloud/ispadmin/actuator/health → `{"status":"UP"}`
- Observability release: `1.0.3+21daf98`
