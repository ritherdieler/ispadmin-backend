# Lab staging #2329 — cableado tráfico MikroTik (2026-08-31)

Relacionado: [lab-usuario-prueba-cableado-2329.md](./lab-usuario-prueba-cableado-2329.md), [staging-360-fuentes-olt-acs-2026-08-31.md](./staging-360-fuentes-olt-acs-2026-08-31.md).

## Objetivo

Habilitar recolección de consumo (poll `/queue/simple`) para la suscripción lab **#2329** en staging, usando el router **Mikrotik CCR 2** (`network_device.id = 8`, API `38.224.231.4`).

## Estado aplicado (2026-08-31 ~23:21 Lima)

| Pieza | Valor |
|-------|--------|
| `subscription.host_device_id` | **8** |
| `subscription.plan_id` | **1** (`lab-basico`, 200/200 Mbps, `FIBER`) |
| `subscription.ip` | `192.168.88.99` (sin `ip_pool_id`; válido para cola manual) |
| Cola MK | `[stg] id:2329, usuario:LAB VSOL, lugar:, nap:, plan:lab-basico, tipo:FIBER` |
| Target cola | `192.168.88.99/32` |
| `max-limit` | `200M/200M` |
| Comment | `env=stg FIBER` |
| Suscripciones elegibles staging | **1** (`findForTrafficPolling`) |
| Primera muestra | `BASELINE` en `subscription_traffic_sample` (poll 23:21) |

## SQL aplicado (staging `ispadmin_staging`)

```sql
INSERT INTO plan (id, name, price, download_speed, upload_speed, type, is_active)
VALUES (1, 'lab-basico', 0, 200, 200, 'FIBER', 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  download_speed = VALUES(download_speed),
  upload_speed = VALUES(upload_speed),
  type = VALUES(type),
  is_active = VALUES(is_active);

UPDATE subscription
SET host_device_id = 8, plan_id = 1
WHERE id = 2329;
```

## Cola en RouterOS (REST)

Prefijo `[stg]` obligatorio con `gigafiber.environment.tag=stg`.

```bash
# En el VPS (credenciales API en network_device id=8; no documentar password)
QUEUE='[stg] id:2329, usuario:LAB VSOL, lugar:, nap:, plan:lab-basico, tipo:FIBER'
curl -sk -u 'gigafiber2023:***' -X PUT 'https://38.224.231.4/rest/queue/simple' \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"${QUEUE}\",\"target\":\"192.168.88.99\",\"max-limit\":\"200M/200M\",\"comment\":\"env=stg FIBER\"}"
```

Alternativa vía app: reprovisión MikroTik de la suscripción con WAR staging (`QueueManagerService.recreateQueueForSubscription`).

## Deploy requerido

El módulo **traffic** y scheduling deben estar activos en el WAR staging:

```bash
./scripts/deploy.sh --env staging --with servicehealth,traffic
```

Sin `traffic` + `gigafiber.scheduling.enabled=true` no corre `SubscriptionTrafficPollScheduler` (~60 s).

## Verificación

### 1. Elegibilidad

```sql
SELECT id, ip, host_device_id, plan_id
FROM subscription WHERE id = 2329;
-- host_device_id=8, plan_id=1, ip=192.168.88.99
```

### 2. Cola en el router

```bash
curl -sk -u 'gigafiber2023:***' \
  'https://38.224.231.4/rest/queue/simple?target=192.168.88.99'
```

### 3. Muestras en BD

```sql
SELECT bucket_start, sample_status, avg_mbps_down, avg_mbps_up, queue_name
FROM subscription_traffic_sample
WHERE subscription_id = 2329
ORDER BY bucket_start DESC
LIMIT 5;
```

- Primera fila tras cablear: `BASELINE` (sin Mbps; normal).
- Segunda poll (~1 min): `OK` con `avg_mbps_*` si hay tráfico en el CPE.

### 4. Logs Tomcat staging

```bash
docker logs tomcat9027 2>&1 | grep 'Traffic poll' | tail -5
# Esperado: matched=1 samples=1 (solo #2329 en staging)
```

### 5. UI

Backoffice staging → `/subscriptions/2329/service-health` o detalle de suscripción:

- Panel **Consumo**: deja de mostrar *“no tiene IP o dispositivo host”*.
- 360 → fuente `TRAFFIC / mbps` pasa a **Reciente** tras el segundo poll con `OK`.

`POST /ispadmin-staging/traffic/poll` requiere **Bearer** (PlatformAuthFilter); el scheduler no.

## Recrear desde cero

1. Ejecutar SQL de plan + `host_device_id`.
2. Crear cola `[stg]` (REST o reprovisión).
3. Confirmar deploy `--with traffic`.
4. Esperar 1–2 ciclos de poll o revisar logs.

## Qué no tocar

- No añadir #2329 a `SERVICE_HEALTH_PILOT_*` de prod.
- No crear cola sin prefijo `[stg]` desde staging.
- `ip_pool_id` puede seguir NULL (IP fija de lab fuera del pool `192.168.250.0/24`).
