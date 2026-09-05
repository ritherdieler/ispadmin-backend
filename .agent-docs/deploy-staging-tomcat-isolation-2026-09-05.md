# Deploy staging Tomcat aislado — 2026-09-05

## Resultado

| Paso | Estado |
|------|--------|
| `tomcat-staging` :8081 | OK (alta compose + nginx `gigafiber_backend_staging`) |
| Core `/ispadmin-staging/` | HTTP 200 |
| Traffic / Gateway / ACS (1er intento) | Falló: CNFE `MySQL56InnoDBSpatialDialect` (satélites sin `hibernate-spatial`) |
| Fix dialecto + redeploy `--only oltgateway,traffic,acs` | HTTP 200 en los 3 `/actuator/health` |
| Quitar `ispadmin-staging*` de `tomcat9027` | Hecho |

## Comandos

```bash
./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth,netdiag --only core,oltgateway,traffic,acs
# tras fix:
./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth,netdiag --only oltgateway,traffic,acs
```

## Fix

`application-traffic|acs|oltgateway.properties`: `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL57Dialect`.

Detalle aislamiento: [staging-tomcat-isolation-war-selectivo.md](./staging-tomcat-isolation-war-selectivo.md).
