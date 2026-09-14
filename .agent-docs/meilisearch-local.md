# Meilisearch en local

## Arranque

```bash
./scripts/meili-local.sh up
./scripts/meili-local.sh status
./scripts/meili-local.sh ui          # abre http://localhost:7700
```

Parar:

```bash
./scripts/meili-local.sh down
```

Compose: [`docker-compose.meilisearch.yml`](../docker-compose.meilisearch.yml)  
Imagen: `getmeili/meilisearch:v1.9` · puerto `7700` · `MEILI_ENV=development` (mini-dashboard activo).

## Credenciales (dev)

| Variable | Valor por defecto |
|----------|-------------------|
| Host | `http://localhost:7700` |
| Master key | `masterKeyDevSoloLocal` |

Alineado en `application-dev.properties`:

```properties
search.enabled=true
search.provider=meilisearch
search.index=subscriptions
search.meili.host=http://localhost:7700
search.meili.api-key=masterKeyDevSoloLocal
```

## Probar con el backend

1. Levantar Meili: `./scripts/meili-local.sh up`
2. Backend: `./run-dev.sh` (profile `dev`)
3. Al arrancar, `MeiliIndexBootstrap` reindexa si el índice está vacío
4. Reindex manual (con auth de la API como cualquier endpoint protegido):

```bash
curl -X POST 'http://localhost:8080/ispadmin/subscription/search/reindex'
```

5. Buscar:

```bash
curl 'http://localhost:8080/ispadmin/subscription/search?q=nombre&page=0&size=20'
```

## Mini-dashboard

Abrir [http://localhost:7700](http://localhost:7700). Si pide API key, usar `masterKeyDevSoloLocal`.

Sirve para explorar el índice `subscriptions` y probar queries; no es un panel de rendimiento.
