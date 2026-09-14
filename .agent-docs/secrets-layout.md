# Reorganización de secretos — ispadmin-backend

## Fuente de verdad

| Archivo | Contenido |
|---------|-----------|
| `application.properties` | Solo campos generales compartidos (server, JPA, face defaults, feature flags) |
| `application-dev.properties` | Secretos y overrides de desarrollo |
| `application-prod.properties` | Secretos y overrides de producción (muchos vía `${ENV:}`) |

`WEB-INF/classes/application*.properties` dejó de versionarse y `src/main/resources/app.json` fue eliminado.

## Secciones en profiles (dev / prod)

1. Firebase
2. Base de datos
3. Pagos (`custom.*`)
4. OLT (`olt.service.*`)
5. MikroTik (solo dev; N/A en prod)
6. Logging
7. Server / proxy (solo prod)
8. WhatsApp
9. Search / Meilisearch (host + api-key)
10. Observabilidad (API keys, paths, Jira, tracker, session)
11. Subscription overrides (si aplica)

## Secretos eliminados o limpiados

| Elemento | Motivo |
|----------|--------|
| `WEB-INF/classes/application.properties` | Duplicado obsoleto y conflictivo |
| `WEB-INF/classes/application-prod.properties` | Duplicado obsoleto y conflictivo |
| `src/main/resources/app.json` | Sample de Lyra sin consumidor en runtime |
| `spring.security.user.*` | Seguridad Spring deshabilitada en `pom.xml` |
| `face.login.djl-model-path` | No tenía consumidores |
| `face.login.djl-model-name` | No tenía consumidores |
| `ConfigConstants.kt` | OLT ya usa `olt.service.*` |
| `SpringSecurityConfig.java` | Archivo comentado y sin uso |
| Placeholders de secretos en `application.properties` | Movidos a profiles |

## Propiedad a consumidor

| Propiedad | Consumidor |
|-----------|------------|
| `custom.*` | `ServerConfiguration` |
| `olt.service.base-url` | `OltHttpClient` |
| `olt.service.api-key` | `OltHttpClient` |
| `mikrotik.connection.*` | `NetworkDeviceConnectionHelper` |
| `gcp.firebase.*` | `FirebaseProperties`, `FirebaseStorageService` |
| `whatsapp.*` | `WhatsAppProperties`, webhook y notificaciones |
| `search.meili.*` | `MeiliClientConfig` |
| `observability.api-keys.*` | `ObservabilityApiKeyFilter` |
| `observability.jira.*` | `JiraIssueTrackerAdapter`, `ObsTicketApplicationService` |
| `observability.tracker.*` | `IssueTrackerRegistry`, webhook controller |
| `observability.session.*` | `ObservabilitySessionTokenService` |
