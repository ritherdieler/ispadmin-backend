# Staging: pool 192.168.250 compatible con VLAN 100

Fecha: 2026-09-01

## Contexto

- Pool staging: `192.168.250.1/24` (id 250, host `network_device` 8).
- La app Android registra FIBER con **VLAN 100** por defecto.
- Prod exige VLAN 100 + segmento `192.168.30.x` o `192.168.31.x` (`SubscriptionVlanRules`).

El registro FIBER en staging fallaba con HTTP 500: `resolveVlan()` rechazaba el pool `192.168.250.x` antes de autorizar la ONU.

## Cambio

En perfil staging (`gigafiber.environment.tag=stg`), VLAN **100** acepta:

| Pool | Entorno |
|------|---------|
| `192.168.30.0/24` | prod y staging |
| `192.168.31.0/24` | prod y staging (capacidad extra MK2; ver `mk2-pool-31-prod-2026-09-05.md`) |
| `192.168.250.0/24` | solo staging (`tag=stg`) |

Implementación: `SubscriptionVlanRules.assertPoolAligned(..., environmentTag)` y `FiberInstallationStrategy` inyecta `GigafiberEnvironmentProperties`.

Prod sin tag `stg` acepta VLAN 100 solo en `192.168.30.x` / `192.168.31.x`.

## Verificación

```bash
cd ispadmin-backend
./mvnw -q test -Dtest=SubscriptionVlanRulesTest,FiberInstallationStrategyTest
```

Tras desplegar staging, repetir `POST /ispadmin-staging/subscription` FIBER con `vlan: "100"` y comprobar respuesta 200 (suscripción creada, provisión OLT/TR-069 según estado del lab).

## SQL / datos

No cambia el segmento del pool; solo la regla de backend. Script existente: `scripts/sql/staging-ip-pool.sql`.
