# WhatsApp Business + HTTPS + Webhook — Producción GigaFiber

**Fecha de implementación**: 2026-07-04  
**Repositorio**: `ispadmin-backend`  
**VPS**: `212.85.13.47`  
**Dominio API**: `https://api.gigafiberperu.cloud`

---

## 1. Resumen ejecutivo

Esta entrega pone en producción:

1. **HTTPS** con Nginx + Let's Encrypt en el VPS, exponiendo el backend bajo `api.gigafiberperu.cloud`.
2. **Hardening del VPS**: Tomcat restringido a `127.0.0.1:8080` y MySQL a `127.0.0.1:3306` para que no sean accesibles desde internet.
3. **Webhook de WhatsApp Cloud API**: endpoint `GET/POST /ispadmin/whatsapp/webhook` con verificación Meta, firma HMAC SHA256, deduplicación y procesamiento completo de eventos.
4. **Captura de `wamid`**: los envíos salientes ahora persisten el ID de mensaje de Meta para correlacionar con eventos de entrega.
5. **Mensajes entrantes**: auto-respuesta contextual con detalle de deuda o mensaje de soporte.
6. **Migración de clientes**: Android, backoffice y asistencias apuntan a `https://api.gigafiberperu.cloud/ispadmin`.

---

## 2. Arquitectura

```
Internet
   │
   ├── HTTPS :443 ──► Nginx (host VPS)
   │                      │
   │              proxy_pass :8080
   │                      │
   │             Tomcat Docker (tomcat9027)
   │                      │
   │             Spring Boot /ispadmin
   │                      │
   │             MySQL Docker (mysql8033)
   │
   └── Meta Webhook ──► GET/POST /ispadmin/whatsapp/webhook
```

### Dominios y puertos

| Componente | Antes | Después |
|---|---|---|
| API backend | `http://212.85.13.47:8080/ispadmin` | `https://api.gigafiberperu.cloud/ispadmin` |
| Tomcat binding | `0.0.0.0:8080` (público) | `127.0.0.1:8080` (solo host) |
| MySQL binding | `0.0.0.0:3306` (público) | `127.0.0.1:3306` (solo host) |
| Nginx | No instalado | Activo, HTTPS con Certbot |
| Backoffice HTTPS | No configurado | Plantilla preparada (`backoffice.gigafiberperu.cloud`) |

---

## 3. Estado del VPS verificado pre-implementación

| Puerto | Estado previo | Acción |
|---|---|---|
| 80 | Libre | Nginx instalado y escuchando |
| 443 | Libre | Nginx + Certbot SSL |
| 8080 | `0.0.0.0:8080` público | Restringir a `127.0.0.1:8080` |
| 3306 | `0.0.0.0:3306` público (riesgo) | Restringir a `127.0.0.1:3306` |

No había Nginx ni Apache instalados: instalación limpia sin conflictos.  
Hibernate `ddl-auto=update` activo: nuevas tablas/columnas se crean al desplegar.

---

## 4. Registro de decisiones técnicas (ADR)

| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| Dominio `api.gigafiberperu.cloud` | `api.gigafiberperu.com` (en la guía original) | El dominio `.cloud` ya resuelve a la IP del VPS |
| SSL individual por subdominio + Certbot HTTP-01 | Wildcard `*.gigafiberperu.cloud` | HTTP-01 no requiere configurar DNS del proveedor; más simple para empezar |
| Terminación TLS en Nginx | SSL dentro de Spring/Tomcat | La guía lo recomienda explícitamente; Nginx es la práctica estándar |
| Tomcat restringido a `127.0.0.1:8080` | Mantenerlo público | Todo el tráfico HTTPS pasa por Nginx; `deploy.sh` usa `curl 127.0.0.1:8080` via SSH, sin romper nada |
| MySQL restringido a `127.0.0.1:3306` | Mantenerlo público | MySQL expuesto a internet es un riesgo innecesario; `application-prod.properties` ya usa `127.0.0.1:3306` |
| Webhook alcance completo (verify + HMAC + estados + entrantes) | Solo verify + POST basic | La guía pide trazabilidad completa; se implementa en una sola entrega |
| Auto-respuesta v1: texto contextual | Plantillas Meta (fuera de ventana 24h) | En ventana de 24h se puede usar texto libre; más simple y sin gestión adicional de plantillas |
| Backoffice predispuesto en `backoffice.gigafiberperu.cloud` | Mismo dominio con path `/backoffice` | Subdominios separados: CORS y certificados más limpios; no se activa hasta tener DNS |
| Secretos solo en VPS docker-compose / `.env` | Variables en `application-prod.properties` | Ningún secreto productivo en Git |
| `ddl-auto=update` sin scripts SQL | Flyway/Liquibase | Ya estaba activo globalmente; no se cambia la estrategia en esta entrega |

---

## 5. Infraestructura Nginx

### Archivos en el repositorio

```
scripts/nginx/
├── snippets/proxy-backend.conf         Headers HTTP reverse proxy
├── snippets/websocket-backend.conf     Upgrade WebSocket (/ispadmin/ws)
├── snippets/ssl-params.conf            Parámetros TLS compartidos
├── api.gigafiberperu.cloud.conf        Vhost activo (API + webhook)
├── backoffice.gigafiberperu.cloud.conf.example  Plantilla SPA (no activada)
└── README.md                           Guía de operación
```

### Instalar en el VPS

```bash
bash scripts/setup-nginx-ssl.sh
```

El script instala Nginx, despliega snippets y vhost, deshabilita `default`, inicia Nginx, instala Certbot y obtiene el certificado.

### Renovación SSL

Certbot configura renovación automática via systemd. Verificar:

```bash
certbot renew --dry-run
systemctl status snap.certbot.renew.timer
```

### Activar backoffice HTTPS (futuro)

Ver `scripts/nginx/README.md` para pasos completos.

---

## 6. Variables de entorno en producción

Configurar en el VPS en `/opt/gigafiber/.env` (nunca commitear):

| Variable | Descripción | Origen |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Fijo |
| `WHATSAPP_ACCESS_TOKEN` | Token de acceso Meta | Meta for Developers > WhatsApp > API Setup |
| `WHATSAPP_PHONE_NUMBER_ID` | ID del número de teléfono | Meta for Developers > WhatsApp > API Setup |
| `WHATSAPP_BUSINESS_ACCOUNT_ID` | ID de la cuenta de negocio | Meta for Developers > WhatsApp > API Setup |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Token creado por nosotros | Libre elección; debe coincidir en Meta Console |
| `WHATSAPP_APP_SECRET` | App Secret de Meta | Meta for Developers > App Settings > Basic > App Secret |

Referencia: `scripts/docker/env.prod.example`

Reiniciar Tomcat tras cambiar variables (sin recreate, para no perder el WAR):
```bash
cd /opt/gigafiber && docker compose restart tomcat
```

---

## 7. Webhook de WhatsApp Cloud API

### URL del webhook
```
https://api.gigafiberperu.cloud/ispadmin/whatsapp/webhook
```

### Endpoint GET — Verificación Meta
- Parámetros: `hub.mode`, `hub.verify_token`, `hub.challenge`
- Responde con `hub.challenge` en texto plano si `hub.mode=subscribe` y el token coincide.

### Endpoint POST — Recepción de eventos
- Valida firma `X-Hub-Signature-256` con HMAC-SHA256 y `WHATSAPP_APP_SECRET`.
- Responde `200 EVENT_RECEIVED` siempre (Meta requiere 200 rápido).
- Procesa asíncronamente por tipo de evento.
- **Deduplicación**: tabla `whatsapp_webhook_event` con `eventKey` único por `wamid`.

### Tipos de eventos procesados

| Tipo | Acción |
|---|---|
| `statuses` | Actualiza `deliveryStatus` en `whatsapp_message_log` (sent/delivered/read/failed) |
| `messages` (text) | Persiste en `whatsapp_inbound_message`, identifica cliente y envía auto-respuesta |
| `messages` (no-text) | Persiste con tipo `UNSUPPORTED`, sin respuesta |

### Registrar en Meta Developer Console
1. WhatsApp > Configuration > Webhook
2. Callback URL: `https://api.gigafiberperu.cloud/ispadmin/whatsapp/webhook`
3. Verify Token: valor de `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
4. Campo a suscribir: `messages`

---

## 8. Cambios en base de datos

Las tablas se crean/alteran automáticamente al desplegar (Hibernate `ddl-auto=update`).

### Tabla modificada: `whatsapp_message_log`

Columnas nuevas:
- `meta_message_id VARCHAR(500)` — ID de mensaje devuelto por Meta al enviar
- `delivery_status VARCHAR(255)` — sent/delivered/read/failed (via webhook)
- `delivery_status_at DATETIME` — timestamp del último estado recibido

### Tabla nueva: `whatsapp_inbound_message`

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | INT PK | Auto-generado |
| `meta_message_id` | VARCHAR(500) UNIQUE | ID de Meta, clave de deduplicación |
| `phone` | VARCHAR(255) | Número del remitente |
| `message_text` | VARCHAR(2000) | Texto del mensaje |
| `message_type` | VARCHAR(255) | text / UNSUPPORTED / etc. |
| `subscription_id` | INT | FK al cliente identificado (nullable) |
| `processed` | BOOLEAN | Si fue procesado |
| `reply_sent` | BOOLEAN | Si se envió auto-respuesta |
| `error_message` | VARCHAR(1000) | Error al responder (si aplica) |
| `created_at` | DATETIME | Timestamp de recepción |

### Tabla nueva: `whatsapp_webhook_event`

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | INT PK | Auto-generado |
| `event_key` | VARCHAR(500) UNIQUE | `status:<wamid>` o `message:<wamid>` |
| `event_type` | VARCHAR(255) | status / message |
| `payload_summary` | VARCHAR(4000) | Payload recortado para auditoría |
| `created_at` | DATETIME | Timestamp |

---

## 9. Endpoints operativos

### WhatsApp saliente (existentes)
```
POST /ispadmin/whatsapp/test-message
POST /ispadmin/whatsapp/test-template-message
POST /ispadmin/payment/{id}/send-whatsapp-reminder
POST /ispadmin/payment/send-whatsapp-reminders?limit=N
POST /ispadmin/payment/send-monthly-whatsapp-reminders?limit=N
```

### Auditoría — logs de envíos
```
GET /ispadmin/whatsapp/logs
GET /ispadmin/whatsapp/logs/payment/{paymentId}
```

### Auditoría — mensajes entrantes (nuevos)
```
GET /ispadmin/whatsapp/inbound-messages
GET /ispadmin/whatsapp/inbound-messages/subscription/{subscriptionId}
```

### Webhook
```
GET  /ispadmin/whatsapp/webhook   (verificación Meta)
POST /ispadmin/whatsapp/webhook   (recepción de eventos)
```

---

## 10. Migración de clientes

| Proyecto | Archivo modificado | URL anterior | URL nueva |
|---|---|---|---|
| Android | `presentation/build.gradle` (flavor prod) | `http://212.85.13.47:8080/ispadmin/` | `https://api.gigafiberperu.cloud/ispadmin/` |
| Backoffice | `src/services/config.ts` | `http://212.85.13.47:8080/ispadmin` | `https://api.gigafiberperu.cloud/ispadmin` |
| Asistencias | `asistencia-frontend/.env` | `http://212.85.13.47:8080/ispadmin` | `https://api.gigafiberperu.cloud/ispadmin` |

**Android**: requiere nuevo build y distribución del APK/AAB.  
**Backoffice**: rebuild SPA (`npm run build`) y redespliegue.  
**Asistencias**: rebuild (`npm run build`) y redespliegue.

---

## 11. Checklist de salida a producción

- [ ] DNS `api.gigafiberperu.cloud` resuelve a `212.85.13.47`
- [ ] `bash scripts/setup-nginx-ssl.sh` ejecutado en VPS como root
- [ ] `https://api.gigafiberperu.cloud/ispadmin/` responde HTTP 200/302
- [ ] Certificado SSL válido: `curl -I https://api.gigafiberperu.cloud/ispadmin/`
- [ ] Variables en `/opt/gigafiber/.env` configuradas con valores reales
- [ ] Contenedor Tomcat reiniciado tras cargar variables
- [ ] Puerto 8080 restringido a `127.0.0.1` en docker-compose
- [ ] Puerto 3306 restringido a `127.0.0.1` en docker-compose
- [ ] `curl -m 3 http://212.85.13.47:8080/` falla (no accesible externamente)
- [ ] Webhook registrado en Meta Console (URL + Verify Token + campo `messages`)
- [ ] Verificación Meta exitosa (check verde en console)
- [ ] Test plantilla con número controlado: `POST .../whatsapp/test-template-message`
- [ ] Recordatorio individual: `POST .../payment/{id}/send-whatsapp-reminder`
- [ ] Respuesta desde celular → mensaje entrante persistido + auto-respuesta recibida
- [ ] Estado `delivered`/`read` aparece en `GET .../whatsapp/logs`
- [ ] Lote mensual limitado: `POST .../payment/send-monthly-whatsapp-reminders?limit=3`
- [ ] Android, backoffice y asistencias funcionando con URL HTTPS
- [ ] Scheduler mensual activado solo después de confirmar todo lo anterior

---

## 12. Runbook de incidentes

### Token de WhatsApp vencido / inválido
Síntoma: logs con `status=FAILED`, `401 Unauthorized`, o `OAuthException` code `200`
(`Cannot call API for app … on behalf of user …` — suele indicar token sin scopes
`whatsapp_business_messaging` / `whatsapp_business_management`).
```
1. Generar System User token en Meta Business Settings (scopes WhatsApp)
2. Actualizar WHATSAPP_ACCESS_TOKEN en /opt/gigafiber/.env
3. cd /opt/gigafiber && docker compose restart tomcat
4. Probar: POST /ispadmin/whatsapp/test-message
```
No usar `--force-recreate` solo para rotar el token: el WAR vive en `webapps/` y se pierde.
Detalle: `.agent-docs/whatsapp-token-rotation-2026-07-30.md`

### Webhook no recibe eventos
Síntoma: Meta no envía notificaciones; `whatsapp_webhook_event` sin filas nuevas.
```
1. Verificar suscripción activa en Meta Console > WhatsApp > Configuration
2. Confirmar que HTTPS responde: curl -I https://api.gigafiberperu.cloud/ispadmin/whatsapp/webhook
3. Confirmar firma: revisar logs de Tomcat para errores "Firma invalida"
4. Comprobar WHATSAPP_APP_SECRET y WHATSAPP_WEBHOOK_VERIFY_TOKEN en `/opt/gigafiber/.env`
```

### Certificado SSL vencido
Síntoma: navegadores muestran error de certificado.
```
certbot renew
systemctl reload nginx
```

### Pausar scheduler mensual en caso de incidencia
```
# Agregar propiedad en application-prod.properties del VPS (o env var):
spring.task.scheduling.enabled=false
# O comentar la anotación @Scheduled en MonthlyWhatsAppReminderScheduler.kt y redesplegar
```

### Rotación del App Secret de Meta
```
1. Meta for Developers > App Settings > Basic > Reset App Secret
2. Actualizar WHATSAPP_APP_SECRET en /opt/gigafiber/.env
3. cd /opt/gigafiber && docker compose restart tomcat
4. Enviar un mensaje de prueba y verificar que el webhook lo procesa
```

---

## 13. Referencias

- Guía de producción original: `Guia_Produccion_WhatsApp_Business_GigaFiber.docx`
- [Meta WhatsApp Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api/)
- [Meta Graph API Webhooks](https://developers.facebook.com/docs/graph-api/webhooks/)
- [Certbot con Nginx](https://certbot.eff.org/instructions)
- [Spring Boot detrás de reverse proxy](https://docs.spring.io/spring-boot/how-to/webserver.html)
- Configuración Nginx del repo: `scripts/nginx/README.md`
- Variables de entorno: `scripts/docker/env.prod.example`
