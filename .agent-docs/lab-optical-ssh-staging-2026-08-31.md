# Óptica SSH lab (staging) — 2026-08-31

## Qué hace (actualizado 2026-09-02)

Staging no enciende el poll SNMP full-OLT (`olt.gateway.sync.signal-enabled=false`). Health pide óptica de N seriales al Gateway:

`POST /api/olt-gateway/onus/optical` `{ "sns": [...] }`

Los SNs salen del scope Health (`subscription_acs.lab=1` + tag `stg`). Gateway no elige labs.

| Pieza | Comportamiento |
|-------|----------------|
| Scheduler | `HealthOltPullService` modo `live-sns` (~90 s) |
| CLI | `display ont info by-sn` + `display ont optical-info {port} {ontId}` (o SNMP si on) |
| Ingest | Respuesta del POST → `OltHistoryService`; upsert `status_current` si hay `olt_mgr_onu` |
| On-demand | `POST /subscription/{id}/service-health/optical-refresh` → mismo POST con 1 SN |

## Overlay

```bash
./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic
```

Hornea `service.health.optical-pull-mode=live-sns` y deja `signal-enabled=false`.

Eliminados `olt.gateway.sync.lab-optical-ssh-*`.

## Relacionado

- [staging-360-fuentes-olt-acs-2026-08-31.md](./staging-360-fuentes-olt-acs-2026-08-31.md)
- [staging-health-optical-pull-2026-09-02.md](./staging-health-optical-pull-2026-09-02.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)
