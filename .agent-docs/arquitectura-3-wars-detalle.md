# Arquitectura 3 WARs + Redis — detalle de cada elemento

Documento del canvas `arquitectura-3-wars`. Descarga gráfica: [arquitectura-3-wars.svg](./arquitectura-3-wars.svg) · [arquitectura-3-wars.png](./arquitectura-3-wars.png).

**Leyenda de líneas**

| Estilo | Significado |
|--------|-------------|
| Continua | HTTP / JWT / comando síncrono |
| Punteada | Redis Streams (`gigafiber.events`) — async interno |

---

## Clientes externos

### Android

| Campo | Valor |
|-------|--------|
| **Qué es** | App IpsAdmin (campo / técnicos). |
| **Qué hace** | Login, búsqueda, altas FIBER, 360, operaciones con JWT. |
| **Habla con** | Solo **Core WAR**. Nunca Traffic, Gateway ni Redis. |

### Backoffice

| Campo | Valor |
|-------|--------|
| **Qué es** | Web de operación (React). |
| **Qué hace** | Suscripciones, 360, NOC, CRM, tráfico vía facades del Core. |
| **Habla con** | Solo **Core WAR**. Nunca Traffic, Gateway ni Redis. |

---

## Contenedor

### Core WAR

| Campo | Valor |
|-------|--------|
| **Qué es** | Único Tomcat público (`ispadmin` / `ispadmin-staging`). Fachada JWT y orquestador. |
| **Qué hace** | Login, suscripciones, pagos, alta FIBER, CRM WhatsApp, BFF hacia Traffic/Gateway/ACS, y el diagnóstico 360. |
| **No es** | No hace poll MikroTik ni SSH a la OLT. |
| **Contiene** | Service-health, Snapshot 360, Consumer, BFF. |
| **Código** | `WispAdminApplication`, paquetes `wispadmin.*`, `servicehealth.*`, `events.*` |

---

## Rectángulos dentro del Core

### Service-health

| Campo | Valor |
|-------|--------|
| **Qué es** | Módulo de dominio (no WAR). Vive en el Core. |
| **Qué hace** | Atiende `GET /subscription/{id}/service-health` (vista 360). Lee snapshot/cache; si no hay dato fresco, pide evidencia por HTTP a Traffic/Gateway, evalúa y guarda la foto. |
| **Habla con** | Snapshot (lee/escribe); Traffic (HTTP miss). |
| **No habla con** | Redis ni Consumer directamente. |
| **Código** | `HealthSummaryQueryService`, `HealthEvidenceReader`, `DiagnosisEngine`, `ServiceHealthController` |

### Snapshot 360

| Campo | Valor |
|-------|--------|
| **Qué es** | Foto ya evaluada del estado técnico de una suscripción (`HealthSummary` en JSON). |
| **Qué hace** | Acelera el GET 360: si está fresca, se responde sin reconsultar Traffic/Gateway. |
| **Dónde vive** | Tabla `service_health_current` (+ cache Redis `health:360:{id}` con TTL). |
| **Quién escribe** | Service-health (tras evaluar) y Consumer (tras un evento). |
| **Código** | `HealthCurrent`, `HealthSnapshotIngestService` / persistencia del summary |

### Consumer

| Campo | Valor |
|-------|--------|
| **Qué es** | Proceso liviano en el Core que escucha el bus Redis. |
| **Qué hace** | `XREADGROUP` del stream `gigafiber.events` (grupo `snapshot-core`). Por cada evento de una suscripción/SN, reevalúa esa suscripción y **actualiza** el Snapshot. |
| **Habla con** | Redis (entra, punteada); Snapshot (sale, continua). |
| **Cuándo corre** | Solo si `gigafiber.redis.enabled=true` (staging). En prod suele estar off → no-op. |
| **Código** | `HealthSnapshotConsumer` |

### BFF / fachada

| Campo | Valor |
|-------|--------|
| **Qué es** | Capa de proxies HTTP del Core hacia subsistemas y ACS. |
| **Qué hace** | Expone a clientes (vía Core) series de tráfico, inventario ONU, NOC/alarmas, acciones TR-069, sin que el cliente hable con Traffic/Gateway. |
| **Habla con** | Gateway (HTTP); GenieACS (ACS/TR-069); Traffic (series vía facades). |
| **Código** | `SubscriptionTrafficFacadeController`, `OnuFacadeController` / clientes `oltclient`, `trafficclient`, flujos ACS |

---

## Bus e infraestructura

### Redis

| Campo | Valor |
|-------|--------|
| **Qué es** | Servicio interno (no API pública). Cache + Streams. |
| **Qué hace** | Stream `gigafiber.events` (hechos: tráfico, óptica, anomalías). Cache del 360 y hashes live opcionales. |
| **Quién escribe** | Traffic y Gateway (`XADD`). |
| **Quién lee** | Consumer del Core. |
| **No hace** | No sustituye HTTP para series históricas, inventario o comandos. |
| **Código / ops** | `EventBusPort`, `RedisStreamEventBus`; `docker-compose.redis.yml` / VPS hostname `redis` |

---

## WARs de subsistema

### Traffic WAR

| Campo | Valor |
|-------|--------|
| **Qué es** | Tomcat/proceso autónomo de telemetría de ancho de banda. |
| **Qué hace** | Poll a MikroTik (contadores), persiste samples/rollups, detecta anomalías, publica hechos al bus. Sirve HTTP de series al Core. |
| **Habla con** | MikroTik (abajo); Redis (`XADD` punteada); Core (HTTP directory + respuestas al miss/BFF). |
| **Eventos tipicos** | `traffic.latest`, `traffic.poll-run`, `traffic.anomaly-opened` / `cleared`. |
| **Código** | `TrafficApplication`, `SubscriptionTrafficPollService`, `TrafficAnomalyService` |

### Gateway WAR (OLT Gateway)

| Campo | Valor |
|-------|--------|
| **Qué es** | Tomcat/proceso autónomo de OLT/ONU. |
| **Qué hace** | SSH único a la OLT, inventario ONU, poll óptica/señal, sync SmartOLT, ingest de alarmas (NetDiag). Publica hechos al bus. |
| **Habla con** | OLT MA5608T (abajo); Redis (`XADD`); Core (HTTP inventario/óptica/NOC vía BFF). |
| **Eventos tipicos** | `onu.optical`, `onu.state`. |
| **Código** | `OltGatewayApplication`, `OltSignalPollService`, `LabOpticalSshPollService`, controllers gateway |

---

## Externos (fuera de Gigafiber WARs)

### GenieACS

| Campo | Valor |
|-------|--------|
| **Qué es** | ACS TR-069 (aprovisionamiento y lectura de CPE). |
| **Qué hace** | Wi‑Fi, WAN, parámetros del router del cliente; el Core habla con él en altas FIBER y paneles CPE. |
| **Habla con** | Solo el Core (BFF/ACS), no Traffic ni Gateway. |

### MikroTik

| Campo | Valor |
|-------|--------|
| **Qué es** | Router(s) de borde / CCR del ISP. |
| **Qué hace** | Contadores de tráfico por IP/cola que Traffic poll-ea. |
| **Habla con** | Solo Traffic WAR. |

### OLT MA5608T

| Campo | Valor |
|-------|--------|
| **Qué es** | OLT GPON Huawei del acceso fibra. |
| **Qué hace** | ONUs, óptica Rx/Tx, alarmas PON vía CLI/SSH (y SNMP donde aplique). |
| **Habla con** | Solo Gateway WAR. |

---

## Flujos resumidos

1. **GET 360 caliente:** Cliente → Core → Service-health → Snapshot (si fresco) → respuesta.  
2. **GET 360 miss:** Service-health → HTTP Traffic/Gateway → evalúa → escribe Snapshot → respuesta.  
3. **Actualización async:** Traffic/Gateway → Redis (`XADD`) → Consumer → actualiza Snapshot.  
4. **Histórico / inventario / NOC:** Cliente → Core BFF → Traffic o Gateway.  
5. **Alta / Wi‑Fi ACS:** Cliente → Core → GenieACS.

---

## Referencias

- Transporte: [subsistemas-desacople-transporte.md](./subsistemas-desacople-transporte.md)
- Redis + snapshot: [redis-snapshot-360-staging-2026-09-03.md](./redis-snapshot-360-staging-2026-09-03.md)
- Orden de dibujo: [diagramas-arquitectura-orden.md](./diagramas-arquitectura-orden.md)
- Canvas: `arquitectura-3-wars.canvas.tsx`
