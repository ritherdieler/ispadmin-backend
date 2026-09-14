# Configuración Nginx — GigaFiber

Configuración modular de Nginx para el VPS de producción (`212.85.13.47`).

## Estructura

```
scripts/nginx/
├── snippets/
│   ├── proxy-backend.conf              Headers comunes de reverse proxy
│   ├── websocket-backend.conf          Upgrade WebSocket (para /ispadmin/ws)
│   └── ssl-params.conf                 Parámetros TLS compartidos
├── api.gigafiberperu.cloud.conf        Vhost activo — reverse proxy al backend
├── backoffice.gigafiberperu.cloud.conf.example      Plantilla SPA (deshabilitada)
├── observability.gigafiberperu.cloud.conf.example   Plantilla SPA (deshabilitada)
├── gigafiberperu.cloud.conf.example                 Landing corporativo (apex + www)
└── README.md                           Este archivo
```

## Instalación en el VPS

Ejecutar desde la raíz del repo, por SSH en el VPS:

```bash
bash scripts/setup-nginx-ssl.sh
```

El script instala Nginx, copia los snippets y el vhost API, deshabilita el vhost
`default`, inicia Nginx y obtiene el certificado SSL con Certbot.

Requiere ejecutar como `root` y que `api.gigafiberperu.cloud` ya resuelva a la IP del VPS.

## Verificar SSL

```bash
curl -I https://api.gigafiberperu.cloud/ispadmin/
openssl s_client -connect api.gigafiberperu.cloud:443 -brief
```

## Renovación del certificado

Certbot configura renovación automática vía systemd timer. Para verificar:

```bash
certbot renew --dry-run
systemctl status snap.certbot.renew.timer
```

## Restringir puertos tras verificar HTTPS

Una vez confirmado que `https://api.gigafiberperu.cloud/ispadmin/` responde:

Editar `/opt/gigafiber/docker-compose.yml`:

```yaml
# Tomcat: de público a solo localhost
ports: ["127.0.0.1:8080:8080"]

# MySQL: de público a solo localhost
ports: ["127.0.0.1:3306:3306"]
```

```bash
cd /opt/gigafiber && docker compose up -d
```

Verificar que el puerto 8080 ya no es accesible desde internet:

```bash
# Debe fallar (timeout o connection refused):
curl -m 3 http://212.85.13.47:8080/
```

## Activar el backoffice en HTTPS (futuro)

Cuando se necesite exponer el backoffice en `backoffice.gigafiberperu.cloud`:

1. Crear registro DNS `backoffice.gigafiberperu.cloud` → `212.85.13.47`.

2. Build del SPA en local:
   ```bash
   cd ispadmin-backoffice
   npm run build
   ```

3. Subir `dist/` al VPS:
   ```bash
   rsync -avz dist/ root@212.85.13.47:/var/www/gigafiber/backoffice/
   ```

4. Activar la config en el VPS:
   ```bash
   cp /etc/nginx/sites-available/backoffice.gigafiberperu.cloud.conf.example \
      /etc/nginx/sites-available/backoffice.gigafiberperu.cloud.conf
   ln -s /etc/nginx/sites-available/backoffice.gigafiberperu.cloud.conf \
          /etc/nginx/sites-enabled/
   nginx -t && systemctl reload nginx
   ```

5. Obtener certificado:
   ```bash
   certbot --nginx -d backoffice.gigafiberperu.cloud
   ```

6. Actualizar `ispadmin-backoffice/src/services/config.ts`:
   ```typescript
   baseUrl: 'https://api.gigafiberperu.cloud/ispadmin',
   ```
   El WebSocket pasará automáticamente a `wss://api.gigafiberperu.cloud/ispadmin/ws`.

7. Verificar CORS: el backend ya incluye `https://backoffice.gigafiberperu.cloud`
   como origen permitido en `CorsConfig.kt`.

## Activar observability en HTTPS

El dashboard de observabilidad se sirve como SPA estático. La autorización ADMIN se
aplica a nivel de API mediante un token de sesión firmado (`X-Obs-Session`), por lo que
el vhost ya no usa basic-auth. El despliegue completo está automatizado en
`ispadmin-observability-web/scripts/deploy.sh` (build + rsync + vhost + certbot). Pasos
manuales equivalentes:

1. Crear registro DNS `observability.gigafiberperu.cloud` → `212.85.13.47`.

2. Build del SPA en local:
   ```bash
   cd ispadmin-observability-web
   npm run build
   ```

3. Subir `dist/` al VPS:
   ```bash
   rsync -avz dist/ root@212.85.13.47:/var/www/gigafiber/observability/
   ```

4. Activar la config en el VPS:
   ```bash
   cp /etc/nginx/sites-available/observability.gigafiberperu.cloud.conf.example \
      /etc/nginx/sites-available/observability.gigafiberperu.cloud.conf
   ln -s /etc/nginx/sites-available/observability.gigafiberperu.cloud.conf \
          /etc/nginx/sites-enabled/
   nginx -t && systemctl reload nginx
   ```

5. Obtener certificado:
   ```bash
   certbot --nginx -d observability.gigafiberperu.cloud
   ```

6. Asegurar `OBS_SESSION_SECRET` (aleatorio) y
   `OBS_DASHBOARD_BASE_URL=https://observability.gigafiberperu.cloud` en
   `/opt/gigafiber/.env` y recrear Tomcat (`docker compose up -d tomcat`).

7. Verificar CORS: el backend ya incluye `https://observability.gigafiberperu.cloud`
   como origen permitido en `CorsConfig.kt`. El WebSocket usa el endpoint dedicado
   `/ispadmin/ws/observability`, autorizado con el token de sesión ADMIN.

## Landing en el dominio raíz (gigafiberperu.cloud)

El SPA de marketing vive en el repo `landing/`. Despliegue automatizado:

```bash
cd landing
cp scripts/deploy.config.example scripts/deploy.config.local
chmod +x scripts/deploy.sh
./scripts/deploy.sh --full
```

Requisitos:

1. DNS `gigafiberperu.cloud` y `www.gigafiberperu.cloud` → `212.85.13.47` (no Hostinger CDN).
2. Web root en el VPS: `/var/www/gigafiber/landing/`.
3. Plantilla Nginx: `gigafiberperu.cloud.conf.example`.

Detalle y smoke tests: `landing/.agent-docs/deploy-prod-gigafiberperu-cloud.md`.

## Rollback

Si algo falla después de activar Nginx:

```bash
# Detener Nginx
systemctl stop nginx

# El tráfico vuelve a ir directo a Tomcat:8080 mientras 8080 siga público
# (no restringir 8080 hasta confirmar que HTTPS funciona)
```
