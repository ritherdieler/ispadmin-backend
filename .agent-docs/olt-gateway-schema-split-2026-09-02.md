# Gateway: esquemas MySQL propios (2026-09-02)

Tras el plan: las tablas `olt_mgr_*` salen del core (`ispadmin` / `ispadmin_staging` / `ispadmin_dev`) a bases MySQL hermanas.

| Perfil | Core | Gateway |
|--------|------|---------|
| prod | `ispadmin` | `prod_oltgateway` |
| staging | `ispadmin_staging` | `stg_oltgateway` |
| dev | `ispadmin_dev` | `dev_oltgateway` |

`olt_mgr_onu_optical_sample` es tabla Health y se queda en el core.

Health y NetDiag no importan `oltgateway`: cada uno tiene cliente HTTP propio contra `/api/olt-gateway`. No hay adapters en Gateway que implementen puertos de hermanos.

## Código

- Segundo PU: `OltGatewayJpaConfig` (`oltGatewayDataSource` / `oltGatewayEntityManagerFactory` / `oltGatewayTransactionManager`) si `olt.gateway.enabled=true`.
- EntityScan y `@EnableJpaRepositories` del core ya no incluyen `oltgateway`.
- Servicios Gateway usan `@Transactional("oltGatewayTransactionManager")`.
- JDBC: `olt.gateway.datasource.url=${OLT_GATEWAY_DATASOURCE_URL:…}`. User/pass reutilizan `spring.datasource.*`.

**No** definir `OLT_GATEWAY_DATASOURCE_URL` en `/opt/gigafiber/.env` compartido (pisaría ambos WAR). Catálogo: `vps-secrets-management.md`.

## Migración one-shot (datos existentes)

Hibernate `ddl-auto=update` crea tablas vacías en el schema nuevo; **no copia datos**. Si ya hay `olt_mgr_*` en el core, ejecutar **una vez** el SQL del perfil **antes** del WAR nuevo:

| Entorno | Script |
|---------|--------|
| local | `scripts/sql/migrate-olt-mgr-to-dev_oltgateway.sql` |
| staging | `scripts/sql/migrate-olt-mgr-to-stg_oltgateway.sql` |
| prod | `scripts/sql/migrate-olt-mgr-to-prod_oltgateway.sql` (solo con confirmación explícita) |

Ejemplo staging (VPS, contenedor MySQL):

```bash
docker exec -i mysql8033 mysql -uroot -p < scripts/sql/migrate-olt-mgr-to-stg_oltgateway.sql
```

No ejecutar dos veces (`RENAME TABLE` falla si el origen ya no existe).

## Tests

`./mvnw test -Dtest=SubsystemScanFilterTest,HealthWiringTest,OltGatewayDatasourcePropertiesFileTest,OltGatewayJpaConfigTest,OltGatewaySchemaMigrateScriptTest,OltMgrAssociationJpaTest`
