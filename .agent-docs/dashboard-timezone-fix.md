# Fix timezone dashboard (UTC vs America/Lima) — 2026-06-30

## Problema

El dashboard en producción mostraba KPIs de **julio** cuando en Lima aún era **30 de junio ~20:23**. Reiniciar el VPS o cambiar la timezone del host no corrigió el comportamiento.

## Causa raíz

El backend corre en **Tomcat dentro de Docker** (`tomcat9027`). Los contenedores usaban **UTC** por defecto:

| Componente | Antes | Después |
|------------|-------|---------|
| Host VPS | America/Lima | America/Lima |
| Tomcat (Docker) | UTC (01:31 del 01/jul) | America/Lima |
| MySQL (Docker) | UTC (`CURRENT_DATE = 2026-07-01`) | America/Lima |

A las 20:23 del 30/jun en Lima (= 01:23 UTC del 01/jul), `Calendar.getInstance()` en la JVM calculaba el mes **julio** para egresos, cancelaciones, reconexiones e instalaciones.

`spring.jackson.time-zone=America/Lima` solo afecta serialización JSON, **no** `Calendar.getInstance()`.

## Archivos clave (código)

| Archivo | Cambio |
|---------|--------|
| `src/main/kotlin/.../util/AppTimeZone.kt` | Zona centralizada, `calendar()`, `currentBillingPeriod()` |
| `src/main/kotlin/.../config/AppTimeZoneConfiguration.kt` | Init desde `app.timezone` al arrancar Spring |
| `src/main/kotlin/.../WispAdminApplication.kt` | `AppTimeZone.initialize()` antes de `runApplication` |
| `src/main/resources/application.properties` | `app.timezone`, `hibernate.jdbc.time_zone` |
| `src/main/kotlin/.../service/DashBoardService.kt` | `AppTimeZone.calendar()` / `zoneId()`; fechas de billing explícitas |
| `src/main/kotlin/.../repository/PaymentRepository.kt` | Queries con `:startDate` / `:endDate` (sin `CURRENT_DATE()`) |
| `src/main/kotlin/.../controller/ReportController.kt` | `AppTimeZone.calendar()` en reportes |
| `src/main/kotlin/.../util/DateTimeUtils.kt` | `toLocalDateTimeOrNull()` usa `AppTimeZone.zoneId()` |
| `scripts/docker/tomcat.Dockerfile` | `ENV TZ=America/Lima` |
| `scripts/deploy.sh` | `CATALINA_OPTS` incluye `-Duser.timezone=America/Lima` |
| `scripts/patch-docker-timezone.py` | Script one-shot para parchear `docker-compose.yml` en VPS |

## Config

```properties
app.timezone=America/Lima
spring.jackson.time-zone=${app.timezone}
spring.jpa.properties.hibernate.jdbc.time_zone=${app.timezone}
```

Docker Compose (servicios `tomcat` y `mysql`):

```yaml
environment:
  TZ: America/Lima
  CATALINA_OPTS: "-Duser.timezone=America/Lima ..."
```

## Flujo de fechas en el dashboard

- **KPIs calendario del mes** (egresos, cancelaciones, reconexiones, instalaciones): rango `[primer día del mes, último día del mes]` en `America/Lima` vía `AppTimeZone.calendar()`.
- **KPIs de pagos** (`grossRevenue`, `totalRaised`, `totalToCollect`, `totalDiscount`): periodo de **facturación anterior** (`currentBillingPeriod()` → mes anterior al calendario actual). Es lógica de negocio ISP, no bug de timezone.

## Bug corregido en `/dashboard/v2`

`createDashBoardV2` usaba `month.value` (1–12) con `getMonthName()` pensado para índices Calendar (0–11). Reemplazado por `spanishMonthShort()`.

## Verificación en VPS

```bash
docker exec tomcat9027 date
docker exec tomcat9027 printenv TZ
docker exec mysql8033 mysql -uroot -p -e "SELECT NOW(), CURRENT_DATE();"
curl -u 'user:pass' http://127.0.0.1:8080/ispadmin/dashboard/v2
```

Resultado esperado con hora nocturna Lima (antes de medianoche UTC):

- Tomcat: fecha/hora `-05` (America/Lima)
- KPIs de cancelaciones/reconexiones del **mes calendario actual**, no del mes UTC

Validado 2026-06-30 ~20:46 Lima:

```
Tomcat: Tue Jun 30 08:46:27 PM -05 2026
cancelledByUsers: 3
cancelledBySystem: 13
reconnections: 12
```

## Build

```bash
./mvnw compile -DskipTests
./mvnw clean package -DskipTests -Ddjl.linux
```

Build: `./mvnw compile -DskipTests` — OK (2026-06-30).

Deploy producción:

```bash
./scripts/deploy.sh --war-only
```

Tras `docker compose up` que recrea Tomcat, redeploy WAR y/o `./scripts/deploy.sh --setup` si faltan libs DJL en `/usr/local/tomcat/lib`.

## Pendiente opcional

- **Frontend** (`ispadmin-backoffice/src/pages/Dashboard.tsx`): etiquetas de gráficos con `new Date(item.date)` pueden desplazarse ±1 mes; no afecta KPIs numéricos.
- **Semántica pagos**: si el negocio quiere ingresos del mes calendario en curso (no mes de facturación anterior), cambiar `currentBillingPeriod()` y renombrar métodos `*ForCurrentMonth`.
