# Búsqueda de suscriptores (Meilisearch) — Backend

Buscador de suscriptores por nombre/apellido/DNI con tolerancia a errores de tipeo,
insensible a acentos/mayúsculas, con relevancia y paginación. Expuesto como un
endpoint único y respaldado por un **motor de búsqueda intercambiable** (arquitectura
de puertos y adaptadores), con **fallback a base de datos** si el motor falla.

## Contrato HTTP

- `GET /ispadmin/subscription/search?q=&status=&page=0&size=20`
  - `q` (opcional): término de búsqueda. Vacío = no filtra por término.
  - `status` (opcional): filtra por `serviceStatus` (`ACTIVE`, `CUT_OFF`, `SUSPENDED`, `CANCELLED`). Vacío = no filtra.
  - `page` (default `0`), `size` (default `20`).
  - Respuesta `200`:
    ```json
    { "items": [ /* SubscriptionDto completos */ ], "page": 0, "size": 20, "total": 42, "totalPages": 3 }
    ```
  - `items` son `SubscriptionDto` **completos** (el mismo DTO de `find/nameAndLastName`).
    El motor solo hace matching + ranking y devuelve IDs ordenados; el backend **hidrata**
    cada `SubscriptionDto` desde la BD respetando el orden de relevancia del motor.
- `POST /ispadmin/subscription/search/reindex` (admin): reindexa todas las suscripciones.
  Respuesta `200`: `{ "reindexed": <cantidad> }`.

### Endpoints deprecados (compatibilidad)

`GET /subscription/find/nameAndLastName` y `GET /subscription/fastSearch` se mantienen
marcados como `@Deprecated` hasta migrar todos los clientes (Android / backoffice).

## Arquitectura (puertos y adaptadores)

El dominio/aplicación depende de **interfaces neutrales** que no mencionan Meilisearch.
Cambiar de motor = crear un adaptador nuevo que implemente los 2 puertos y cambiar
`search.provider`; **no se toca** el controller, el servicio de aplicación ni los clientes.

```
search/
  api/                                     (puertos neutrales, sin infra)
    SearchEngine.kt                        search(SearchQuery): SearchPage<SearchHit>
    SearchIndexer.kt                       index / indexAll / delete / purge
    model/SearchQuery.kt                   term, filters, page, size
    model/SearchPage.kt                    SearchHit + SearchPage<T> (items, page, size, total, totalPages)
    model/SearchableDocument.kt            id + fields
  application/                             (orquestación, sin dependencias del motor)
    SubscriptionSearchService.kt           usa SearchEngine + fallback a adaptador DB, hidrata SubscriptionDto
    SubscriptionDocumentMapper.kt          Subscription -> SearchableDocument
    SubscriptionIndexingListener.kt        @TransactionalEventListener(AFTER_COMMIT) -> SearchIndexer
    SubscriptionSearchReindexer.kt         reindexAll() usado por bootstrap, scheduler y endpoint admin
    SearchReconciliationScheduler.kt       @Scheduled de reconciliación (reindex total)
    SubscriptionSearchEvents.kt            SubscriptionChangedEvent / SubscriptionDeletedEvent
  controller/
    SubscriptionSearchController.kt        GET /subscription/search, POST /subscription/search/reindex
  infrastructure/
    NoOpSearchIndexer.kt                   indexador nulo (activo cuando provider != meilisearch)
    db/DbSearchEngineAdapter.kt            SearchEngine sobre LIKE (fallback y provider=db)
    meili/                                 ÚNICO lugar con imports com.meilisearch.*
      MeiliClientConfig.kt                 beans Client + engine + indexer + bootstrap (condicionales)
      MeiliSearchEngineAdapter.kt          implements SearchEngine
      MeiliSearchIndexerAdapter.kt         implements SearchIndexer (tolerante a fallos)
      MeiliIndexBootstrap.kt               settings + reindex no bloqueante en ApplicationReadyEvent
```

### Selección de motor (beans)

- `MeiliClientConfig` está activo solo con `search.provider=meilisearch` (default) y
  `search.enabled=true`. Registra `Client`, `SearchEngine` (Meili, `@Primary`),
  `SearchIndexer` (Meili, `@Primary`) y el bootstrap.
- `DbSearchEngineAdapter` (bean `dbSearchEngineAdapter`) y `NoOpSearchIndexer` existen
  siempre. Con `provider=db` o `search.enabled=false` quedan como únicos beans, por lo que
  el sistema funciona sin infraestructura de Meilisearch.
- `SubscriptionSearchService` inyecta el `SearchEngine` primario + el adaptador DB por
  `@Qualifier("dbSearchEngineAdapter")` como fallback.

### Indexación desacoplada por eventos

Los write paths **no** dependen del buscador: publican eventos de dominio Spring
(`SubscriptionChangedEvent`) tras `repository.save(...)` / cancelación en
`SubscriptionController`, `ServiceCutManagerService` y `CancelledOnuReuseService`.
`SubscriptionIndexingListener` (`@TransactionalEventListener(AFTER_COMMIT)`) traduce esos
eventos a llamadas al puerto `SearchIndexer`. Si mañana se quita el buscador, se borra el
listener y nada más.

### Resiliencia

- Indexación tolerante a fallos: el adaptador Meili captura y loguea excepciones; nunca
  rompe el flujo de negocio.
- Búsqueda con fallback: si el motor principal lanza excepción, `SubscriptionSearchService`
  cae al `DbSearchEngineAdapter`.
- Reconciliación: `@Scheduled` (cada 30 min por defecto) reindexa todo como red de seguridad;
  también existe el endpoint admin de reindex.

### Bootstrap del índice (ApplicationReadyEvent)

- `searchableAttributes = [fullName, firstName, lastName, dni]`
- `filterableAttributes = [serviceStatus, installationType]`
- `sortableAttributes = [id]` (ranking por defecto = relevancia)
- Ejecuta un reindex total no bloqueante al arranque.

## Configuración (properties y variables de entorno)

Genéricas (agnósticas del motor) en `application.properties`:

| Property | Env | Default | Descripción |
|---|---|---|---|
| `search.enabled` | `SEARCH_ENABLED` | `true` | Apaga todo el buscador |
| `search.provider` | `SEARCH_PROVIDER` | `meilisearch` | `meilisearch` \| `db` |
| `search.index` | `SEARCH_INDEX` | `subscriptions` | Nombre del índice |
| `search.reconciliation.interval-ms` | `SEARCH_RECONCILIATION_INTERVAL_MS` | `1800000` | Intervalo del reindex programado |
| `search.reconciliation.initial-delay-ms` | `SEARCH_RECONCILIATION_INITIAL_DELAY_MS` | `1800000` | Retraso inicial del scheduler |

Específicas del adaptador Meili:

| Property | Env | Default | Descripción |
|---|---|---|---|
| `search.meili.host` | `MEILI_HOST` | `http://localhost:7700` | URL de Meilisearch |
| `search.meili.api-key` | `MEILI_MASTER_KEY` | *(vacío)* | Master key (solo en el backend) |

En `application-prod.properties` host y master key vienen **solo** por variables de entorno.
Las credenciales del motor nunca salen al cliente.

## Despliegue de Meilisearch

### Desarrollo (docker-compose)

`docker-compose.meilisearch.yml` levanta `getmeili/meilisearch:v1.9` con `MEILI_MASTER_KEY`
y volumen persistente `meili_data`:

```bash
MEILI_MASTER_KEY=masterKeyDevSoloLocal docker compose -f docker-compose.meilisearch.yml up -d
```

El backend en dev usa por defecto `search.meili.host=http://localhost:7700`. Exportar
`MEILI_MASTER_KEY` con la misma clave que el contenedor.

### Producción

- Levantar Meilisearch v1.9 (contenedor/servicio) accesible solo desde el backend.
- Configurar `MEILI_HOST` y `MEILI_MASTER_KEY` como variables de entorno del backend.
- Para operar sin motor temporalmente: `SEARCH_PROVIDER=db` (usa LIKE) o `SEARCH_ENABLED=false`.

## Dependencias

- `com.meilisearch.sdk:meilisearch-java:0.14.6` (última versión compatible con Java 8;
  las 0.16+ requieren JDK 17).
- `com.squareup.okhttp3:okhttp:4.12.0` (peer dependency requerida por meilisearch-java; solo v4.x).
- `io.mockk:mockk-jvm:1.13.11` (scope test).

## Pruebas (TDD)

- `SubscriptionSearchServiceTest`: paginación, filtro por estado hacia el puerto y ruta de fallback (mockea el **puerto** `SearchEngine`).
- `SubscriptionIndexingListenerTest`: indexa/elimina y tolerancia a fallos (mockea el **puerto** `SearchIndexer`).
- `MeiliSearchIndexerAdapterTest`: upsert/delete y tolerancia a fallos (mockea el `Client` de Meili).
- `SubscriptionSearchControllerTest`: forma paginada JSON y filtro por estado (MockMvc, mockea el servicio).
