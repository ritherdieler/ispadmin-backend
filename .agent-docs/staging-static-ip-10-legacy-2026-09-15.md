# Staging #10 — WIRELESS STATIC_IP para recolección legacy (2026-09-15)

Alta lab para validar clientes que siguen midiendo por cola simple `/32`, no por PPPoE.

## Suscripción

| Campo | Valor |
|-------|--------|
| id | **10** |
| Tipo | WIRELESS `STATIC_IP` |
| IP | `192.168.250.10` |
| Host | MK2 id 8 |
| PPPoE | ninguno |
| MK | `COMPLETE` |
| DNI | `99520879` LAB STATICIP |

Directorio tráfico: `ip` sí, `pppoeUsername` no, `routerHint=8`.

## Recolección

Poll admin escribió sample por **IP** (`client_ip=192.168.250.10`, `subscription_id` NULL). Cola `[stg] id:10, usuario:LAB STATICIP…`. `GET /subscription/10/traffic/latest` aún no ve bucket (el join por `subscription_id` no pega el sample legacy). El 360 en vivo debe resolver por accessMode XOR (IP).

## Backoffice local → staging

Vite ya corre `http://127.0.0.1:3000` `--mode staging` → `https://api.gigafiberperu.cloud/ispadmin-staging`.

360: `http://127.0.0.1:3000/subscriptions/10/service-health`
