# Fix cierre ACS → Core (`tr069ProvisionStatus`) — 2026-09-04

## Síntoma

Alta FIBER vía Gateway dejaba `oltProvisionStatus=COMPLETE` y `tr069ProvisionStatus=PENDING` hasta timeout del e2e Android. Gateway sí llamaba ACS (`POST …/cpe/provision` 200).

## Causas

1. **Gateway no publicaba Redis**: perfiles activos `"oltgateway","prod"` → `application-prod` pisaba Redis a off → `NoOpEventBus`. Stream `gigafiber.events` con `XLEN=0`.
2. **Core sin consumidor CPE**: `servicehealth` excluido del WAR staging (`--with` sin `servicehealth`) → no había `HealthSnapshotConsumer` / flag service.
3. **ACS sin perfiles**: `Tr069ModelProfiles.resolve` solo usaba `dynamicResolver`, nunca registrado en el WAR ACS → `FAILED` con “Modelo ONU sin perfil TR-069 (F6600R)” aunque `ispadmin_staging.tr069_model_profile` sí tenía F6600R.

## Correcciones

| Área | Cambio |
|------|--------|
| Gateway Redis | `application-oltgateway` fuerza Redis on; bake reemplaza `gigafiber.redis.enabled=true` en `application-prod` del WAR gateway; perfiles programáticos `prod,oltgateway` |
| Core | `CpeProvisionFlagService` + `CpeProvisioningEventConsumer` en `wispadmin` (grupo Redis `cpe-provision-core`, independiente de servicehealth) |
| Progress pull | `GET …/registration-progress` llama `refreshTr069FromGateway` (pull del estado en memoria del Gateway) |
| ACS perfiles | Registry carga `tr069_model_profile` desde `acs.profiles.catalog` (staging bake: `ispadmin_staging`) y registra el resolver dinámico |
| Unconfigured ONUs | Con `olt.gateway.client-enabled=true`, `RealOltService` lista autofind vía Gateway (no SmartOLT) y normaliza SN (`ZTEG-DC47BFFD` → `ZTEGDC47BFFD`) |
| Soft-delete SN | `deleteOnu` libera el unique `sn` con sufijo `#del#{id}`; `authorizeOnu` libera tombstones soft-deleted antes de insertar |
| Delete OLT real | `undo service-port port 0/{slot}/{port} ont {ontId}` **antes** de `ont delete`; CLI debe confirmar `success: ≥1` o lanza |

## Verificación local (sin deploy)

```bash
OLT_WRITE_LIVE=true ./mvnw -Dtest=OltGatewayDeleteLiveSmokeTest,OltGatewayCommandServiceTest test
```

Smoke live (2026-09-04): authorize `ZTEGDC47BFFE` en `0/1/1` ont 94 → delete vía `OltGatewayCommandService` → `display ont info by-sn` = *The required ONT does not exist*.

## Verificación staging

- Unit: `CpeProvisionFlagServiceTest`, `CpeProvisioningEventConsumerTest`, `Tr069ModelProfileRegistryTest`, `SubscriptionProvisionServiceTest#refreshTr069FromGateway…`, `OnuSerialNormalizerTest`, `OltManagerFacadeTest` (delete/authorize soft-deleted), `OltGatewayCommandServiceTest`
- Deploy: `./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs`
- Post-deploy: e2e hasta `tr069=COMPLETE`
