# Contexto del abonado en Service Health

## Contrato

`GET /subscription/{id}/service-health` conserva sus campos anteriores y añade:

```json
{
  "subscriber": {
    "display_name": "Nombre completo o razón social",
    "client_type": "PERSON"
  },
  "service_context": {
    "service_status": "ACTIVE",
    "plan_name": "Fibra 500 Mbps",
    "ip": "10.0.0.5"
  }
}
```

El nombre se resuelve con prioridad por tipo de cliente, fallback cruzado y el valor final `Cliente sin nombre registrado`. La consulta usa `ServiceHealthSubscriptionView`; no materializa el grafo EAGER de `Subscription`.

## Despliegue y compatibilidad

Desplegar este backend antes del backoffice. El cambio es aditivo: los constructores y campos previos de `HealthSummary` se conservan y los campos nuevos son opcionales para consumidores antiguos.

No se añadieron variables de entorno, secretos ni comandos OLT.

## Verificación

- `./mvnw -Dtest=ServiceHealthSubscriptionContextReaderTest,HealthSummaryContractTest test`
- Cubre personas, empresas, nombres parciales/vacíos, contrato JSON `snake_case` y compatibilidad del DTO anterior.

