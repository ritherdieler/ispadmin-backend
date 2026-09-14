# Construcción: subsistemas conmutables y colas de staging

Fecha: 2026-08-31

## Colas en MK2 compartido

- Propiedad `gigafiber.environment.tag=stg` en staging (vacía en prod).
- Nombre: `[stg] id:{id}, usuario:...`
- Comment: `env=stg` (más el tipo de instalación cuando se recrea).
- `SimpleQueueNameParser` devuelve `QueueOwner(envTag, subscriptionId)`.
- `reclaimOrphan`, `updateMikroTikQueue` y `recreateQueueForSubscription` solo tocan colas del mismo tag.
- `POST /subscription/generate-simple-queues` responde 409 si hay tag.
- `scripts/mk2-reconcile-queues.mjs` ignora colas etiquetadas.
- Purga: `node scripts/mk2-purge-staging-queues.mjs --tag stg` (dry-run). `--apply` borra. Sin `--tag` no borra.

## Pool IP

Segmento reservado `192.168.250.1/24` como único `ip_pool` de `ispadmin_staging` (host device 8). No solapa con `ispadmin.ip_pool`. Script: `scripts/sql/staging-ip-pool.sql`.

Con `gigafiber.environment.tag=stg`, `SubscriptionVlanRules` admite VLAN **100** sobre ese pool además del pool prod `192.168.30.0/24`. Detalle: `.agent-docs/staging-vlan100-pool-2026-09-01.md`.

## Subsistemas

Default de `./scripts/deploy.sh --env staging`: ningún módulo opcional en el WAR. `--with` para pedir uno.
