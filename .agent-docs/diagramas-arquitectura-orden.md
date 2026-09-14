# Diagramas de arquitectura — orden y sin cruces

Convención obligatoria para Mermaid, canvas Cursor y esquemas ASCII en Gigafiber.

**Regla Cursor:** `gigafiber/.cursor/rules/diagramas-ordenados.mdc` (`alwaysApply: true`).

**Ejemplo canónico:** canvas `arquitectura-4-wars-gateway-acs` (Core + Gateway + ACS + Traffic). El Core no habla con ACS WAR ni GenieACS.

## Objetivo

Que el usuario pueda leer el grafo de un vistazo: cada flecha tiene un carril, ninguna línea atraviesa otra caja “de paso”, y módulos embebidos se ven dentro de su WAR.

## Carriles (plantilla 3 WARs + Redis)

| Carril | Contenido | Estilo |
|--------|-----------|--------|
| Horizontal top | Service-health → Snapshot → BFF → GenieACS | continua |
| Centro vertical | Traffic/Gateway → Redis → Consumer → Snapshot | punteada (Redis) + continua (actualiza) |
| Izquierda | Service-health → Traffic (HTTP miss) | continua, codo fuera del Core |
| Derecha | BFF → Gateway | continua, codo fuera del Core |
| Abajo | Traffic → MikroTik · Gateway → OLT | continua |

## Reglas de dibujo

1. **Capas**: colocar nodos por rango (clientes / core / bus / WARs / dispositivos). No mezclar rangos en la misma fila sin motivo.
2. **Codos, no diagonales** por el medio del diagrama. Preferir `H` luego `V` (o al revés) en SVG/canvas.
3. **Misma columna solo si la flecha es entre vecinos directos** (p. ej. Consumer bajo Snapshot y la flecha es Consumer→Snapshot). Si hay una caja intermedia, no bajar “a través”.
4. **Service-health** (y análogos): no dibujar salida hacia abajo si debajo está el Consumer. Salidas: derecha (snapshot) y/o carril lateral (HTTP miss).
5. **Punteada = async**: Redis Streams / colas. Nunca usar punteada para HTTP miss.
6. **Contenedor WAR**: border alrededor de módulos internos (service-health, consumer, BFF). El título del contenedor es el WAR.
7. **Sin clientes** si el pedido es “solo backend / solo WARs”; no saturar el grafo.
8. **Checklist antes de entregar**:
   - [ ] ¿Alguna línea cruza otra?
   - [ ] ¿Alguna línea atraviesa el rectángulo de una caja que no es origen ni destino?
   - [ ] ¿Cada línea corresponde a un flujo real del código/docs?
   - [ ] ¿Está etiquetado el tipo (HTTP / XADD / eventos / ACS)?

## Vocabulario del mapa 360

| Término | Qué es |
|---------|--------|
| Snapshot 360 | Foto `HealthSummary` en `service_health_current` (+ cache Redis) |
| Consumer | `HealthSnapshotConsumer` en el Core; lee `gigafiber.events` |
| Service-health | Módulo en el Core; no es WAR aparte |
| Línea punteada | Redis Streams interno; no es API pública |

## Dónde actualizar

Al cambiar la arquitectura desplegada (WARs, Redis, BFF), actualizar el canvas `arquitectura-4-wars-gateway-acs` **y** respetar esta convención en cualquier Mermaid nuevo en `.agent-docs/`.
