# Restore prod WAR tras recreate de Tomcat (staging)

**Fecha:** 2026-09-03 ~14:58 (hora Lima)  
**Causa:** un deploy `--env staging` recreó `tomcat9027` (~14:20) y subió solo los WAR de staging. `ispadmin.war` no quedó en `webapps`.

## Qué había en prod

Según git / `deploy-flow.md`, la última release de prod era:

| Campo | Valor |
|-------|--------|
| Commit | `83de8fe` (`hotfix/search-debt-hydrate`) |
| `APP_RELEASE` | `1.0.3+83de8fe` |
| Base previa | `4c1cc1f` (VLAN wireless→fibra) |
| Artefacto en host | `/opt/gigafiber/ispadmin.war` (mtime 2026-09-03 00:33, 241 MB) |

## Síntoma

- `/ispadmin` y `/ispadmin/actuator/health` → 404
- Staging vivo: `/ispadmin-staging`, `-traffic`, `-oltgateway`
- Env del contenedor seguía en `APP_RELEASE=1.0.3+83de8fe`

## Acción

Se copió el WAR que ya estaba en el host (no se reconstruyó):

```bash
docker cp /opt/gigafiber/ispadmin.war tomcat9027:/usr/local/tomcat/webapps/ispadmin.war
```

## Verificación

| Check | Resultado |
|-------|-----------|
| `http://127.0.0.1:8080/ispadmin/` | 200 |
| `http://127.0.0.1:8080/ispadmin/actuator/health` | 200 `{"status":"UP"}` |
| `https://api.gigafiberperu.cloud/ispadmin/` | 200 |
| Staging health | 200 |
| `APP_RELEASE` | `1.0.3+83de8fe` |
