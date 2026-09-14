# Deploy prod — humanizar copy bot WhatsApp — 2026-08-10

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+f7f6fae` | `./scripts/deploy.sh --deploy` + push `develop` |

## Incluye

- Auto-replies humanizados (usted, horario en oración)
- Handoff menú asesor: **solicitud** (no caso)
- Handoff soporte/diagnóstico: **caso**
- Handoff instalación: solicitud de instalación
- ACK in-hours coherente (ya no “seleccione una opción”)

## Smoke

- https://api.gigafiberperu.cloud/ispadmin/ → HTTP 200
- Observability release: `1.0.3+f7f6fae`
