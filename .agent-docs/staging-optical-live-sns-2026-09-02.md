# Staging: óptica live por N SNs (2026-09-02)

Tras el plan de desacople: Gateway ya no elige ONUs lab. Expone `POST /api/olt-gateway/onus/optical` y staging Health lo usa siempre.

## Gateway

- `OltGatewayOpticalPollService.poll(sns)`: máx. 32 SNs; `by-sn` + óptica (SNMP o SSH puntual); upsert `status_current` si hay `olt_mgr_onu`; `missing[]` si la OLT no tiene el SN.
- Cliente: `OltGatewayClient.pollOptical`.
- Eliminados `LabOpticalSshPollService`, scheduler y flags `olt.gateway.sync.lab-optical-ssh-*`.
- Gateway no importa `servicehealth.*`.

## Health

- `service.health.optical-pull-mode=samples` (prod, default): `GET /optical-samples`.
- `live-sns` (overlay staging `--with servicehealth,oltgateway`): SNs del scope lab → POST; ingesta directa.
- `optical-refresh` → el mismo POST con un SN (`RemoteActionService` → `pollAndIngest`).

## Tests

`./mvnw test -Dtest=OltGatewayOpticalPollServiceTest,OltGatewayControllerTest,OltGatewayClientTest,HealthOltPullServiceTest,RemoteActionPersistenceTest,WarSubsystemPackagingTest,SubsystemDependencyRulesTest,HealthWiringTest,OltGatewayOpticalSampleQueryTest`

Health puede importar la superficie HTTP del Gateway (`client`/`api`/`dto`/`exception`); no repos ni SSH.

## Deploy staging

```bash
./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic
```
