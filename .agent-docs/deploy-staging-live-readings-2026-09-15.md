# Deploy staging — lecturas en vivo (2026-09-15)

```bash
FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs,servicehealth
```

Desde `~/gigafiber/ispadmin-backend` rama `cursor/vista-360-live-readings-b5b0` @ `f8cb1f8`. Tomcat `tomcat-staging` `:8081` `/ispadmin-staging/` HTTP 200. Prod no se tocó.

## Objeto leído vs correcto

| | Objeto MK2 | Resultado en API |
|---|---|---|
| Incorrecto (~19:16Z) | `/queue/simple` `*9` `id:6, usuario:CINTIA ESCOBAL` (`192.168.30.23`) | `source=QUEUE`, bps 0/104/560 siempre up=down, `rxBytes` ~106361467542 |
| Residual, no usar | `/queue/simple` `*89C` `<pppoe-gf6>` `rate` | no es throughput de sesión |
| Correcto (este deploy) | `/interface` `pppoe-in` `<pppoe-gf6>` + `/interface/monitor-traffic once` | `source=PPPOE`, `pppoe=gf6` |

`PPPOE_DYNAMIC` no consulta `/queue/simple`.

## Validación #6 `pppoe:gf6` (~19:31Z, 6 polls ADMIN)

`GET https://api.gigafiberperu.cloud/ispadmin-staging/subscription/6/live-readings`

| poll | downloadBps | uploadBps | rxBytes | txBytes |
|------|-------------|-----------|---------|---------|
| 1 | 151808 (0.152 Mbps) | 59704 | 3301718518 | 1151923980 |
| 2 | 166544 (0.167 Mbps) | 341184 | 3301911548 | 1152032842 |
| 3 | 167896 (0.168 Mbps) | 279064 | 3302054133 | 1152168959 |
| 4 | 639048 (0.639 Mbps) | 97024 | 3302167890 | 1152253530 |
| 5 | 21122856 (21.123 Mbps) | 334792 | 3305265529 | 1152354138 |
| 6 | 159008 (0.159 Mbps) | 123048 | 3305340603 | 1152427695 |

`always_up_eq_down=false`. Δ rxBytes +3622085 / 15 s ≈ 1.93 Mbps. Pico instantáneo 21.1 Mbps (YouTube). `rxBytes` ~3.30 GB de `<pppoe-gf6>`, no los 106 GB de Cintia.

Backoffice: `VITE_API_BASE_URL=https://api.gigafiberperu.cloud/ispadmin-staging` ([PR #2](https://github.com/ritherdieler/ispadmin-backoffice/pull/2) poll 2s).
