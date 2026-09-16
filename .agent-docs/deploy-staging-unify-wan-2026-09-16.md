# Deploy staging — unify WAN XOR + 360 (2026-09-16)

Prod no se tocó. Recolección lab no se apagó. No se borraron ONUs lab / #14 / #15.

## Rama desplegada

`cursor/staging-unify-360-d0ce` @ `7a3d0f8` (release `1.0.3+7a3d0f8`). Worktree: `ispadmin-backend-staging-unify`. Merge no rebase.

## Qué se juntó

| Tema | Rama / commit | ¿Ya estaba en unify? |
|------|---------------|----------------------|
| WAN XOR leftover (`gf-static-wan2-poc` / `gf-pppoe-wan2-poc`: borra IP.3 + PPP internet, no toca TR-069/mgmt) | `cursor/wan-internet-replace-0cb6` `f960472` + doc e2e `#14` `708f75f` | No → merge `ebeb5e3` |
| live-readings PPPoE vs QUEUE | `cursor/vista-360-live-readings-b5b0` `f8cb1f8` … `f5419e0` | Sí (ancestro de `f0f9075`) |
| accessMode / alta FIBER + gate recolección quitado | `f0f9075` / `34dbbd3` | Sí |
| Docs unify 360 previos | `a1f828b` `07ca1ba` | Sí |

No se incluyeron commits ajenos no pusheados: `cursor/auto-ip-vlan-provisioning-f644` (ahead 1), `feature/Mapa-digital` (ahead 2).

## Tests previos

```text
/tmp/g813 :acs:test --tests NamedCpeProvisionerTest --tests GenieAcsNamedProvisionBootstrapTest
/tmp/g813 :core:test --tests GenieAcsVirtualParametersTest
node --test scripts/genieacs/provisions/test/gf-static-wan2-poc.test.js \
            scripts/genieacs/provisions/test/gf-pppoe-wan2-poc.test.js
```

Gradle ACS+Core PASS. Node 19/19 PASS. `--deploy` volvió a correr `./gradlew test` completo: PASS.

## Comando

```bash
./scripts/deploy-disabled-modules-preflight.sh --env staging
# unpacked Gradle 8.13 (el hook intercepta ./gradlew)
/tmp/g813 :core:war :core:tomcatLibs -Pdjl.linux
cp core/build/libs/ispadmin.war target/ispadmin-staging.war
./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth
```

Preflight: cadena FIBER/TR-069 completa. WAR único Core incluye `acs.jar` (`gf-static-wan2-poc.js` + `gf-pppoe-wan2-poc.js`).

## WARs / destino

| Pieza | Destino | Resultado |
|-------|---------|-----------|
| Core WAR (in-process ACS, OLT Gateway, Traffic, service-health) | `tomcat-staging` `:8081` `/ispadmin-staging/` | HTTP 200 |
| Sibling `/ispadmin-staging-acs` | no se publica (single WAR) | 404 |

## Smoke (público)

| Check | Resultado |
|-------|-----------|
| `GET https://api.gigafiberperu.cloud/ispadmin-staging/` | 200 `Hello World!` |
| `GET …/ispadmin-staging/api/acs/v1/health` | 200 `{"status":"UP"}` |
| `POST …/ispadmin-staging/users/login` `dscorp` | 200 ADMIN `id=1` `verified=true` |

API: `https://api.gigafiberperu.cloud/ispadmin-staging/`
