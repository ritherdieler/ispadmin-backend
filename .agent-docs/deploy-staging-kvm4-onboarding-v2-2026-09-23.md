# Despliegue staging KVM4 — Onboarding v2

**Fecha:** 2026-09-23  
**Destino:** KVM4 `2.24.66.53`, contenedor `tomcat-staging`  
**Alcance:** solo staging del VPS nuevo. No se tocó `212.85.13.47`, DNS ni el Tomcat de producción.

## Artefacto

- WAR: `ispadmin-staging.war`
- Tamaño: `136243335` bytes
- SHA-256: `7cb5a3953e5bff0a3f1ebda8a0986ee94dc92aa77031e394e38be611c5b636a5`
- Empaque comprobado localmente: Core, ACS, OLT Gateway y Traffic incluidos en el único WAR de staging.

El despliegue se ejecutó con:

```bash
DEPLOY_VPS_HOST=2.24.66.53 ./scripts/deploy.sh --war-only --env staging
```

`DEPLOY_VPS_HOST` evita reutilizar por accidente el destino configurado en `deploy.config.local`; no cambia DNS ni la configuración persistente del host habitual.

## Verificaciones realizadas

| Comprobación | Resultado |
| --- | --- |
| `tomcat-staging` | Recreado; `tomcat9027` no fue reiniciado |
| `GET /ispadmin-staging/actuator/health` local | `200`, `{"status":"UP"}` |
| HTTPS con SNI a KVM4 | `GET /ispadmin-staging/` → `200` |
| Migración Core | `ispadmin_staging` hasta `v57` |
| Migración ACS | `stg_acs` aplicó `V6__onboarding_v2_task.sql` |
| Migración OLT Gateway | `stg_oltgateway` aplicó `V2__provisioning_v2_onu_operation.sql` |

Durante el arranque hubo timeouts transitorios de Traffic contra el mismo Tomcat antes de que el contexto terminara de iniciar. El proceso terminó correctamente y el health-check quedó `UP`.

## Habilitación controlada de v2

Se creó un overlay privado, con permisos `0600`, en `/opt/gigafiber/.env.staging-v2`, referenciado únicamente por el servicio `tomcat-staging`. Producción no hereda estas variables.

```text
PROVISIONING_V2_ENABLED=true
PROVISIONING_V2_WORKER_ENABLED=true
PROVISIONING_V2_TR069_PROFILE_ID=2
PROVISIONING_V2_ACS_BASELINE_KEY=<secreto generado en KVM4>
GENIEACS_V2_PUBLISH_ENABLED=true
```

Al iniciar staging, GenieACS aceptó por HTTP 200 la publicación de `gf-onboarding-v2-pppoe`, `gf-onboarding-v2-wifi` y `gf-onboarding-v2-compensate`. No se modificó ninguna ONU, WAN u OLT durante esta habilitación.

## Siguiente paso controlado

La consulta read-only contra GenieACS, la OLT configurada y la lista de autofind no encontró `HWTC9F4BE990`. No se inició una operación v2: hacerlo sin la ONU física detectada sería una prueba incompleta y podría dejar una reserva sin contacto TR-069.

Cuando el técnico conecte/encienda la ONU, verificar primero que aparezca simultáneamente en autofind de la OLT y en GenieACS con modelo y firmware. Recién entonces se puede iniciar el alta v2 controlada.
