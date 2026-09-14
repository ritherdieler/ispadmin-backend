# Observabilidad — Integración Jira

Configuración de la integración manual/asistida con Jira Cloud para el hub de observabilidad.

> Nota: Jira es ahora el **primer adaptador** de una abstracción neutral de issue tracker
> (`IssueTrackerPort`). La creación de tickets, los webhooks entrantes y el mapeo de estados
> ya no son específicos de Jira. Ver `observability-tracker-abstraction.md` para la arquitectura
> agnóstica, el namespace `observability.tracker.*` y los webhooks entrantes (sincronización 2 vías).

## Instancia configurada (dev)

- Base URL: `https://gigafiberperu.atlassian.net`
- Cuenta: Sergio Carrillo Diestra (`dieler.tk.s@gmail.com`)
- Proyecto: `KAN` — "Sistemas Gigafiber" (software)
- Tipo de issue: `Tarea`
  - Importante: el proyecto `KAN` NO tiene el tipo `Bug`. Tipos disponibles: `Epic`, `Subtask`, `Tarea`, `Historia`.
- Dashboard base URL: `http://localhost:5175`

## Mapeo de prioridad por severidad

Prioridades disponibles en la instancia: `Highest`, `High`, `Medium`, `Low`, `Lowest`.

| Severidad | Prioridad Jira |
|-----------|----------------|
| fatal     | Highest        |
| error     | High           |
| warning   | Medium         |
| info      | Low            |

## Propiedades (application-dev.properties)

```properties
observability.jira.enabled=true
observability.jira.base-url=https://gigafiberperu.atlassian.net
observability.jira.email=dieler.tk.s@gmail.com
observability.jira.api-token=<API_TOKEN>
observability.jira.project-key=KAN
observability.jira.issue-type=Tarea
observability.jira.dashboard-base-url=http://localhost:5175
observability.jira.priority-by-severity.fatal=Highest
observability.jira.priority-by-severity.error=High
observability.jira.priority-by-severity.warning=Medium
observability.jira.priority-by-severity.info=Low
```

Autenticación: Basic auth `email:api-token` (Base64) contra Jira Cloud REST API v3.

## Cómo obtener/rotar el API token

1. Ir a https://id.atlassian.com/manage-profile/security/api-tokens
2. "Create API token", copiar el valor (solo se muestra una vez).
3. Actualizar `observability.jira.api-token`.

## Verificación

- Endpoint de prueba del backend: `POST /observability/jira/test` (usa `myself`, devuelve `{ok, message, accountName}`).
- Verificación directa contra la API (auth + proyectos):
  - `GET /rest/api/3/myself`
  - `GET /rest/api/3/project/search`
  - `GET /rest/api/3/project/KAN` (tipos de issue)
  - `GET /rest/api/3/priority` (prioridades)
- Prueba end-to-end realizada: creación de `KAN-51` (tipo `Tarea`, prioridad `Medium`) y borrado (`DELETE` → HTTP 204). Correcta.

## Notas de seguridad

- El API token es un secreto: no debe versionarse en `application.properties` de producción; usar variable de entorno / secreto del entorno.
- En producción configurar `observability.jira.dashboard-base-url` con la URL pública del dashboard.
