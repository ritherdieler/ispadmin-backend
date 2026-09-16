# Deploy staging — accessMode alta + 360 (2026-09-15)

```bash
./scripts/deploy-disabled-modules-preflight.sh --env staging
# unpacked Gradle 8.13 (el hook intercepta ./gradlew)
gradle :core:war :core:tomcatLibs -Pdjl.linux
cp core/build/libs/ispadmin.war target/ispadmin-staging.war
./scripts/deploy.sh --war-only --env staging --with oltgateway,traffic,acs,servicehealth
```

Release `1.0.3+34dbbd3` (`cursor/remove-collection-gate-d0ce`). Tomcat-staging `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó.

Backoffice 360 (`cursor/360-accessmode-panel-d0ce` @ `a6b8f14`):

```bash
npm run build:staging
rsync --delete dist/ → /var/www/gigafiber/backoffice-staging/
```

Incluye gate de recolección quitado, directorio XOR por `accessMode`, y `POST /subscription` con `PPPOE_DYNAMIC` / `STATIC_IP`.

## E2E Android (IpsAdmin-android#8, cleanup skip)

| ONU | Flag | Id | accessMode | Identidad 360 | Wi-Fi |
|-----|------|----|------------|---------------|-------|
| VSOL `VSOL0031C0B6` | `--access-mode static` | `#13` | `STATIC_IP` | `IP=192.168.250.10` `ROUTER=8` (sin PPPoE) | `lab-vsol-e2e-24` / `lab-vsol-e2e-24 - 5G` pass `11111111` |
| ZTE `ZTEGDC47BFFD` | `--access-mode pppoe` | `#14` | `PPPOE_DYNAMIC` | (ver API; `gf*`) | `lab-zte-e2e-24` / `lab-zte-e2e-24 - 5G` pass `11111111` |

Ambos `E2E_FIBER_STAGING_ESPRESSO_OK`. `evaluated_at` presente. `pilot_enabled` ausente. Recolección lab on. Prod no se tocó.
