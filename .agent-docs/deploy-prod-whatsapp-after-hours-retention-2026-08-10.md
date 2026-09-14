# Deploy prod — WhatsApp horario after-hours + retención media — 2026-08-10

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+bb24fc0` | `./scripts/deploy.sh --deploy` desde `develop` |

## Incluye

- Horario laboral real: Lun–Vie 08:00–17:30, Sáb 08:00–12:30 (`America/Lima`).
- Mensaje único fuera de horario para intervención humana (asesor, instalación, cierre diagnóstico, voucher/comprobante).
- Retención de media WhatsApp + Flyway `V18__whatsapp_media_retention.sql` (`media_purged_at`).

## Smoke

- https://api.gigafiberperu.cloud/ispadmin/ → HTTP 200
- https://api.gigafiberperu.cloud/ispadmin/actuator/health → HTTP 200
- Observability release registrado: `1.0.3+bb24fc0`

## Copy al cliente (fuera de horario)

Handoff:

```
✅ Tu caso fue registrado.
Fuera de horario laboral: sera atendido a la primera hora.
Horario: Lunes a viernes de 8:00 a.m. a 5:30 p.m.; sabados de 8:00 a.m. a 12:30 p.m.
```

Follow-up (voucher / comprobante): lead corto + misma frase de primera hora + una sola línea de horario.
No se repite el horario ni "primera hora" en párrafos distintos.
