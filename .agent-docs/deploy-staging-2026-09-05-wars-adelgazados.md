# Deploy staging 2026-09-05 (WARs adelgazados)

Comando:

```bash
./scripts/deploy.sh --deploy --env staging --with oltgateway,traffic,acs
```

Primer intento abortó: `run_rsync` no es recursivo y saltó el directorio `models/` (`skipping directory`). `docker cp` no encontró `/opt/gigafiber/models/face_feature.zip`. Fix: rsync de cada zip/onnx. Redeploy OK.

## WARs

| WAR | Bytes | Contexto |
|-----|------:|----------|
| `ispadmin-staging.war` | 132192672 | Core (~126 MB; modelos fuera) |
| `ispadmin-staging-acs.war` | 67349530 | ACS (~64 MB) |
| `ispadmin-staging-traffic.war` | 67549625 | Traffic (~64 MB) |
| `ispadmin-staging-oltgateway.war` | 74130339 | Gateway (~71 MB) |

Modelos en host y contenedor: `/opt/gigafiber/models/` (`face_feature.zip`, `ultranet.zip`, `arcface_w600k_mbf.onnx`). Volumen compose `:ro` ya anotado (el contenedor actual los recibió por `docker cp`; el mount aplica en el próximo recreate).

Prod `ispadmin.war` restaurado tras el deploy.

## Verificación

- Suite Maven: 1677 tests, 0 failures, 4 skipped
- `GET /ispadmin-staging/` → HTTP 200
- Release tag del script: `1.0.3+df83610` (working tree sucio; staging no registra OBS)

Detalle de empaquetado: [wars-adelgazados-models-fs.md](./wars-adelgazados-models-fs.md).
