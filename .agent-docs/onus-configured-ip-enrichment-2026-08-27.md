# ONUs configuradas — IP de servicio

Fecha: 2026-08-27

## Cambio

`GET /onu/configured` enriquece `ipAddress` cuando el inventario OLT (`olt_mgr_onu.ip_address`) viene vacío:

1. Preferir IP ya guardada en inventario.
2. Si falta, buscar suscripción **ACTIVE** con `fiberOnu.sn` (proyección SN+IP; evita `EntityNotFoundException` por ONU huérfana).
3. Si aún falta, cruzar por nombre completo (`firstName + lastName` ≈ `olt_mgr` name).

Implementación: `OnuService.enrichWithSubscriptionIps` +
`SubscriptionRepository.findActiveIpSnPairsByFiberOnuSnIn` /
`findActiveIpNamePairsByFullNameIn`.

## UI

Backoffice `/onus/configured`: columna **IP** (tras VLAN). Ver `ispadmin-backoffice/.agent-docs/onus-configured-smartolt-parity-2026-08-27.md`.
