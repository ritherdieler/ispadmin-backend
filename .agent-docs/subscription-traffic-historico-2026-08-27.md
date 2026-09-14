# Histórico de consumo por suscripción (MikroTik)

Fecha: 2026-08-27 (actualizado 2026-08-28)

**Supersedido en poll/retención.** El as-built vigente es [01-implementacion-analitica-consumo-ancho-banda.md](./01-implementacion-analitica-consumo-ancho-banda.md) (poll 1 min, RAW 72 h, 5 min / 1 h / 1 d). Los intervalos de 5 min y retenciones de este archivo son históricos.

## Objetivo

Persistir consumo de datos por cliente desde **RouterOS 7 REST** (`/queue/simple`), con capas de retención, visualización clara en backoffice y análisis agregado de red.

## Fuente de datos

| Campo REST | Uso |
|------------|-----|
| `target` | IP del cliente (`subscription.ip/32`) |
| `bytes` | Contador acumulado up/down |
| `rate` | Throughput instantáneo up/down |

Poll batch cada **5 minutos**, **una llamada REST por `hostDevice`**.

## Estados incluidos

- `ACTIVE`, `CUT_OFF`, `SUSPENDED`
- Requiere `ip` + `hostDevice`

## Tablas

| Tabla | Retención default |
|-------|-------------------|
| `subscription_traffic_sample` | 14 días |
| `subscription_traffic_hourly` | 90 días |
| `subscription_traffic_daily` | 1095 días |
| `subscription_traffic_monthly` | indefinido | columna **`month_key`** (YYYY-MM); evita palabra reservada MySQL `YEAR` en `year_month` |
| `subscription_traffic_counter_state` | 1 fila/suscripción |
| `network_traffic_hour_of_day` | 400 días |

## Configuración

```properties
traffic.poll.enabled=true
traffic.poll.interval-ms=300000
traffic.poll.initial-delay-ms=120000
traffic.poll.bucket-minutes=5
traffic.retention.raw-days=14
traffic.retention.hourly-days=90
traffic.retention.daily-days=1095
traffic.retention.network-hour-days=400
```

## API por suscripción

| Método | Ruta |
|--------|------|
| GET | `/subscription/{id}/traffic?granularity=sample\|hourly\|daily&from=&to=` |
| GET | `/subscription/{id}/traffic/latest` |
| GET | `/subscription/{id}/traffic/summary?month=YYYY-MM` |
| GET | `/subscription/{id}/traffic/today` |
| GET | `/subscription/{id}/traffic/day?date=YYYY-MM-DD` |

## API análisis de red

| Método | Ruta |
|--------|------|
| GET | `/traffic/network/hourly-profile?months=3\|6\|12` |
| GET | `/traffic/network/daily-trend?months=3\|6\|12` |
| GET | `/traffic/network/insights?months=3\|6\|12` |

## UI backoffice

- Panel en detalle de suscripción: chips **Hoy / 7d / 30d / 90d** + selector de día
- Gráficos **Recharts** (área para periodos, barras para intraday)
- Copy en lenguaje llano: «Bajó este mes», «Hoy bajó», «Velocidad habitual»
- Página **`/traffic-analytics`**: hora pico de red, tendencia 3/6/12 meses
- Menú **Gestión de Servicios → Análisis de consumo**

## Notas operativas

- Primer poll solo establece **baseline** (sin sample); el delta aparece desde el segundo poll.
- Si el router reinicia (`uptime` baja), se marca `counter_reset` y no se suma delta negativo.
- Job nocturno agrega hourly → `network_traffic_hour_of_day` antes del purge.
- Perfil horario 6–12 meses usa tabla acumulada de red; 3 meses puede usar hourly directo.

## Código principal

- `com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService`
- `com.dscorp.wispadmin.traffic.service.SubscriptionTrafficQueryService`
- `com.dscorp.wispadmin.traffic.service.NetworkTrafficAnalyticsService`
- `com.dscorp.wispadmin.traffic.controller.NetworkTrafficAnalyticsController`
- `ispadmin-backoffice/src/components/subscriptions/SubscriptionTrafficPanel.tsx`
- `ispadmin-backoffice/src/pages/TrafficAnalytics.tsx`
- `ispadmin-backoffice/src/lib/trafficChartFormat.ts`

## Registro Spring

El paquete `com.dscorp.wispadmin.traffic` debe estar en `scanBasePackages`, `@EntityScan` y `@EnableJpaRepositories` de `WispAdminApplication.kt`.

## Demo local (seed)

```bash
sh mvnw test -Dtest=TrafficLocalSeedIntegrationTest
```

Inserta ~7 días de samples sintéticos para suscripción `#1`. Ver: `/subscriptions?subscriptionId=1`.
