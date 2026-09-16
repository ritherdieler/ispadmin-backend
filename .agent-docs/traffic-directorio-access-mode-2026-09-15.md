# Directorio de tráfico por `accessMode` (2026-09-15)

El Core elige la identidad que ve el Traffic WAR. El medio FIBER/WIRELESS no cambia el poller.

| `accessMode` | Directorio emite | Cola MikroTik |
|--------------|------------------|---------------|
| `PPPOE_DYNAMIC` | solo `pppoeUsername` (`ip` vacío) | `<pppoe-{user}>` |
| `STATIC_IP` | solo `ip` | `target=IP/32` |
| `PPPOE_FIXED` | solo `ip` | `target=IP/32` |

Código: `TrafficDirectoryService.toEntry`, queries `findForTrafficPolling` / `findTrafficTargetsAfter`. Tests: `TrafficDirectoryServiceTest`.

360: `IdentityService.snapshot` incluye `PPPOE` para que el panel de consumo no bloquee altas sin IP ([service-health-identity-pppoe-2026-09-15.md](./service-health-identity-pppoe-2026-09-15.md)).

Política: [traffic-recoleccion-politica.md](./traffic-recoleccion-politica.md). Desacople WAR: [traffic-war-desacople-2026-09-03.md](./traffic-war-desacople-2026-09-03.md).

## Verificación 2026-09-15

- Unitario: FIBER PPPoE, FIBER static con username residual, WIRELESS static, `PPPOE_FIXED`, huecos sin identidad.
- Staging (solo 2 altas): `#5` y `#6` `PPPOE_DYNAMIC` / FIBER / `gf5`·`gf6` / `ip=null`. 360 `TRAFFIC`/`mbps` **FRESH**. `GET …/traffic/latest` **502** `UPSTREAM_AUTHENTICATION_FAILED` (Traffic WAR no alcanzable por el BFF). Collector en health **MISSING**. Prestaging local `:8082` caído.
- Staging sin `STATIC_IP` (`access-migration/eligible` = 0).
- Prod (collector encendido, `GET …/traffic/latest` 200): WIRELESS `#2064` y FIBER con IP `#2351` tienen `polledAt` del día (camino cola simple por IP). El DTO público no expone `accessMode`.

El directorio nuevo **no está desplegado** en staging ni prod; el live usa el WAR actual (username si viene en el DTO; estas altas PPPoE no tienen IP y las estáticas no tienen username).

## Prestaging local (STATIC_IP)

Overlay `local-prestaging`: `gigafiber.subsystems.traffic.enabled=true`, `traffic.client-enabled=true`, URLs loopback `:8082 /ispadmin`. `gigafiber.scheduling.enabled=false` — poll manual.

```bash
./scripts/run-local-prestaging.sh start
./scripts/prestaging-static-ip-traffic-lab.sh all
```

WIRELESS + MK2 id 8. Alta lab **#12** (`STATIC_IP`, IP `192.168.30.10`, `pppoeUsername` null). El directorio emite solo IP.

Poll: `POST /api/traffic/v1/admin/poll` necesita `X-Traffic-Key` (y, en el WAR que aún no recarga el filtro, también JWT). MK2 REST (`/queue/simple/print`) está cortando el handshake; `mikrotikProvisionStatus=PENDING`. `GET /subscription/12/traffic/latest` BFF 502 hasta reiniciar el WAR con la exclusión `/api/traffic` del `PlatformAuthFilter`.
