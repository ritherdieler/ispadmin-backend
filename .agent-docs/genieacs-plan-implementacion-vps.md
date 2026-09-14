# Plan de implementación GenieACS en VPS — Gigafiber

Plan operativo para desplegar el ACS TR-069 en un VPS propio. Complementa [genieacs-despliegue-gigafiber.md](./genieacs-despliegue-gigafiber.md).

**Duración estimada:** 3–5 días (PoC) + 2–4 semanas (piloto producción)

**Estado 2026-07-20:** stack Docker aislado desplegado en VPS (`genieacs-pilot`). TLS activo en `acs.gigafiberperu.cloud`. Pendiente: wizard UI, PoC ONU, NBI backend.

**Alcance fase 1:** VPS + GenieACS + TLS + PoC con 1 ONU TPLG/MSTC + integración mínima backend

---

## 0. Decisiones previas (día 0)

| Decisión | Valor recomendado |
|----------|-------------------|
| Hostname ACS | `acs.gigafiberperu.cloud` |
| VPS / IP | `212.85.13.47` (`srv1043610`) — compartido con `api`, `backoffice`, `observability` |
| DNS | **A** `acs` → `212.85.13.47` (TTL 14400) — **ya existe en el panel** |
| Recursos VPS | Verificar ≥ 4 vCPU y RAM libre ≥ 4 GB antes de instalar GenieACS + MongoDB |
| OS | Ubuntu 22.04 LTS |
| Región | Miami (`us-east-1`) o São Paulo (`sa-east-1`) |
| Instalación GenieACS | **Docker Compose** (`scripts/genieacs/docker-compose.genieacs.yml`) |
| Imagen | `drumsergio/genieacs:1.2.16.0` + `mongo:7.0` |
| Directorio VPS | `/opt/gigafiber/genieacs` |
| Puerto CWMP público | **443** (nginx TLS → genieacs-cwmp :7547) |
| MongoDB | Contenedor Docker (red interna, sin puerto público) |
| Credenciales ACS CPE | Usuario/contraseña por lote (`gigafiber-acs` + secret rotativo) |
| Periodic Inform | 3600 s en CPE provisionados |
| Primer lote ONU | 1 unidad PoC → 50 ONUs profile OLT 1 → TPLG/MSTC |

**Fuera de alcance fase 1:** VSOL bridge, firmware OTA masivo, alta disponibilidad multi-VPS.

---

## 1. Provisión del VPS (día 1 — mañana)

### 1.1 VPS existente (no crear uno nuevo)

GenieACS se instala en el **VPS ya operativo**:

| Subdominio | IP | Servicio |
|------------|-----|----------|
| `api.gigafiberperu.cloud` | 212.85.13.47 | Tomcat / ispadmin-backend |
| `backoffice.gigafiberperu.cloud` | 212.85.13.47 | SPA backoffice |
| `observability.gigafiberperu.cloud` | 212.85.13.47 | Panel observabilidad |
| **`acs.gigafiberperu.cloud`** | **212.85.13.47** | **GenieACS CWMP (nuevo)** |

Acceso SSH: `root@212.85.13.47` (ver `.agent-docs/deploy-flow.md`).

Antes de instalar, verificar recursos libres:

```bash
ssh root@212.85.13.47 'nproc; free -h; df -h /'
```

### 1.2 DNS — ya configurado

Registro actual en el panel (confirmado):

```
acs.gigafiberperu.cloud   A   212.85.13.47   TTL 14400
```

Verificar resolución:

```bash
dig +short acs.gigafiberperu.cloud
# debe devolver: 212.85.13.47
```

**Pendiente:** certificado TLS (`certbot --nginx -d acs.gigafiberperu.cloud`) y vhost Nginx (paso 4).

### 1.3 Acceso inicial

```bash
ssh root@212.85.13.47
sudo apt update && sudo apt upgrade -y
sudo timedatectl set-timezone America/Lima
```

### 1.4 Firewall

```bash
sudo apt install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from <IP_OFICINA>/32 to any port 22 proto tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

Puertos **no** abiertos al público: 7547, 7557, 7567, 27017.

---

## 2. Docker en el VPS (día 1 — tarde)

### 2.1 Instalar Docker (si no está)

```bash
ssh root@212.85.13.47
docker --version || curl -fsSL https://get.docker.com | sh
docker compose version
```

### 2.2 Desplegar stack desde el repo

En tu máquina local (repo `ispadmin-backend`):

```bash
chmod +x scripts/genieacs/deploy-genieacs.sh
./scripts/genieacs/deploy-genieacs.sh
```

O manualmente en el VPS:

```bash
mkdir -p /opt/gigafiber/genieacs
cd /opt/gigafiber/genieacs
# copiar docker-compose.yml y .env desde scripts/genieacs/
cp .env.example .env
nano .env   # GENIEACS_UI_JWT_SECRET obligatorio
docker compose pull
docker compose up -d
docker compose ps
```

Generar JWT secret:

```bash
openssl rand -hex 64
# pegar en GENIEACS_UI_JWT_SECRET dentro de .env
docker compose up -d --force-recreate genieacs
```

### 2.3 Verificar puertos locales (solo 127.0.0.1)

```bash
ss -tlnp | grep -E '7547|7557|7567|3000'
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:7547/
# esperado: 400 o 405 (GenieACS CWMP activo)
```

### 2.4 UI admin (túnel SSH, no exponer a internet)

```bash
ssh -L 7567:127.0.0.1:7567 root@212.85.13.47
# navegador: http://127.0.0.1:7567
```

Primer acceso: wizard de GenieACS → crear usuario admin.

### 2.5 Backup MongoDB (contenedor)

```bash
cd /opt/gigafiber/genieacs
docker compose exec -T mongo mongodump --archive --gzip --db=genieacs > backup/genieacs-$(date +%F).gz
```

Programar cron semanal en el VPS.

---

## 3. TLS y nginx (día 2 — mañana)

Nginx **ya está** en el VPS (`api`, `backoffice`, etc.). Solo agregar vhost ACS.

### 3.1 Copiar plantilla

Desde el repo:

```bash
scp scripts/genieacs/nginx/acs.gigafiberperu.cloud.conf.example \
  root@212.85.13.47:/etc/nginx/sites-available/acs.gigafiberperu.cloud.conf
```

En el VPS:

```bash
ln -sf /etc/nginx/sites-available/acs.gigafiberperu.cloud.conf /etc/nginx/sites-enabled/
nginx -t
```

### 3.2 Certificado Let's Encrypt

```bash
certbot --nginx -d acs.gigafiberperu.cloud --non-interactive --agree-tos --email admin@gigafiberperu.cloud --redirect
systemctl reload nginx
```

### 3.3 Verificación externa

```bash
curl -I https://acs.gigafiberperu.cloud/
dig +short acs.gigafiberperu.cloud
```

### 3.4 Renovación TLS

```bash
certbot renew --dry-run
```

---

## 4. Operación Docker (referencia rápida)

```bash
cd /opt/gigafiber/genieacs

docker compose ps
docker compose logs -f genieacs
docker compose restart genieacs
docker compose pull && docker compose up -d

docker compose exec genieacs sh -c 'ls -la /var/log/genieacs'
```

Actualizar imagen (mantenimiento):

```bash
cd /opt/gigafiber/genieacs
docker compose pull
docker compose up -d
docker compose ps
```

---

## 5. Configuración GenieACS — presets Gigafiber (día 2)

Acceder a UI admin → **Admin → Presets / Provisions**.

### 5.1 Preset bootstrap (todos los modelos)

Evento: `0 BOOT`, `1 BOOT`, `BOOTSTRAP`

Acciones mínimas:

- Registrar serial como `_id` del device
- Periodic Inform = 3600
- Connection Request URL apuntando al ACS

### 5.2 Presets por fabricante (fase PoC)

| Preset | Condición | Modelos |
|--------|-----------|---------|
| `tplg-router` | `_deviceId._OUI = 'TPLG'` o product class XC220/XX530 | TP-Link |
| `mstc-router` | `_deviceId._OUI = 'MSTC'` | MitraStar GPT |
| `huawei-hgu` | `_deviceId._OUI = 'HWTC'` | Huawei EG/HG |

En PoC: configurar **solo `tplg-router` o `mstc-router`** con parámetros validados en 1 unidad.

### 5.3 Credenciales ACS

Definir en preset o vía OLT OMCI:

- URL: `https://acs.gigafiberperu.cloud/`
- Username: `gigafiber-acs`
- Password: `<SECRET_ACS_CPE>` (guardar en vault / `.env` backend, no en repo)

---

## 6. Red OLT + VLAN gestión (día 2–3)

Prerrequisito para que las ONUs lleguen al ACS.

### 6.1 En la OLT (Huawei MA5608T)

1. Crear/usar **VLAN de gestión TR-069** (ej. VLAN 200).
2. Crear **line profile** con TR-069 habilitado (o usar profile 1 existente `SMARTOLT_FLEXIBLE_GPON`).
3. Crear **service profile con VEIP=1** para routers HGU (no usar Generic_1_V1 bridge).
4. Por ONU piloto:

```
pon-onu-mng gpon 0/{slot}/{port}:{ont-id}
  tr069-mgmt 1 acs https://acs.gigafiberperu.cloud/ validate basic username gigafiber-acs password <SECRET>
  tr069-mgmt 1 state unlock
```

Documentar comandos nuevos en `.agent-docs/olt-gateway-comandos-catalogo.md`.

### 6.2 En SmartOLT (opcional)

Crear perfil TR-069 apuntando a `https://acs.gigafiberperu.cloud/` para futuras autorizaciones.

### 6.3 Validación de conectividad

Desde una ONU/router de prueba (o laptop en VLAN mgmt):

```bash
curl -I https://acs.gigafiberperu.cloud/
```

Debe responder HTTP 400/405 (GenieACS CWMP), no timeout.

---

## 7. PoC con 1 ONU (día 3)

### 7.1 Selección

Elegir **1 ONU TPLG o MSTC** online, preferiblemente ya en line profile 10 o migrable a profile con VEIP.

SN ejemplo auditado:

- TPLG: `54504C472A561D98`
- MSTC: `4D5354430942F4B9`

### 7.2 Pasos

1. Reprovisionar ONU con srv-profile VEIP + line profile TR-069 Enable.
2. Aplicar `tr069-mgmt` en `pon-onu-mng`.
3. Reiniciar ONU.
4. Verificar en GenieACS UI que aparece el device (serial como ID).
5. Ejecutar tarea de prueba: reboot remoto o lectura parámetro WiFi.

### 7.3 Criterios de éxito PoC

- [ ] Device visible en GenieACS con estado online
- [ ] `display ont info by-sn` en OLT muestra `TR069 management: Enable`
- [ ] VPS CPU < 20%, RAM libre > 4 GB
- [ ] Logs CWMP sin errores TLS

---

## 8. Integración ispadmin-backend (día 4–5)

### 8.1 Configuración backend

Variables en `application-prod.properties` (o env):

```properties
genieacs.enabled=true
genieacs.nbi.base-url=http://<IP_PRIVADA_VPN>:7557
genieacs.nbi.username=
genieacs.nbi.password=
genieacs.cwmp.public-url=https://acs.gigafiberperu.cloud/
```

> NBI solo accesible vía VPN/túnel desde el servidor backend, no exponer 7557 a internet.

### 8.2 Módulo mínimo (fase 1)

| Componente | Responsabilidad |
|------------|-----------------|
| `GenieAcsClient` | HTTP client al NBI |
| `CpeDeviceEntity` | `subscriptionId`, `onuSn`, `genieacsDeviceId`, `lastInformAt` |
| `CpeManagementService` | reboot, getStatus, setWifiPassword |
| `CpeController` | Endpoints internos backoffice |

Endpoints sugeridos:

```
GET  /api/cpe/{subscriptionId}/status
POST /api/cpe/{subscriptionId}/reboot
POST /api/cpe/{subscriptionId}/wifi-password
```

### 8.3 Webhook (fase 2)

GenieACS → POST al backend cuando un CPE nuevo hace Inform, para vincular automáticamente con suscripción por SN.

---

## 9. Piloto producción — 50 ONUs (semana 2)

Objetivo: ONUs ya en OLT line profile 1 (`SMARTOLT_FLEXIBLE_GPON`, TR069 Enable).

| Paso | Acción |
|------|--------|
| 1 | Exportar lista SN de las 50 ONUs desde SmartOLT |
| 2 | Verificar srv-profile con VEIP en cada una |
| 3 | Aplicar `tr069-mgmt` masivo (script OLT o manual por lote) |
| 4 | Monitorear GenieACS 48 h |
| 5 | Habilitar acciones en backoffice solo para suscripciones del piloto |

Rollback: revertir `tr069-mgmt state lock` y line profile anterior por ONU.

---

## 10. Escalamiento TPLG + MSTC (semanas 3–4)

| Lote | CPE | Perfil OLT |
|------|-----|------------|
| 2 | ~150 TPLG | Nuevo line+srv profile TR-069/VEIP |
| 3 | ~60 MSTC | Mismo profile router |
| 4 | Huawei HGU selectivo | Validar modelo a modelo |

Excluir VSOL bridge (158 ONUs) hasta evaluación en lab.

---

## 11. Operación y monitoreo

### 11.1 Métricas VPS

| Métrica | Umbral alerta |
|---------|---------------|
| CPU | > 70% sostenido 15 min |
| RAM available | < 1.5 GB |
| Disco `/` | > 80% |
| genieacs-cwmp restarts | > 3 en 1 h |

Herramientas: `node_exporter` + Grafana, o monitoreo del proveedor VPS.

### 11.2 Backups

| Qué | Frecuencia |
|-----|------------|
| Snapshot VPS | Semanal |
| `docker compose exec mongo mongodump` | Diario |
| `/opt/gigafiber/genieacs/.env` | En vault, copia cifrada |

```bash
mongodump --db genieacs --out /backup/genieacs-$(date +%F)
```

### 11.3 Logs

```bash
journalctl -u genieacs-cwmp -f
tail -f /var/log/genieacs/genieacs-cwmp-access.log
```

---

## 12. Seguridad

| Control | Estado requerido |
|---------|------------------|
| TLS Let's Encrypt en CWMP | Obligatorio |
| UI JWT secret único | Obligatorio |
| SSH solo con key + IP allowlist | Obligatorio |
| MongoDB bind 127.0.0.1 | Obligatorio (piloto: sin puerto en host, red Docker interna) |
| NBI no expuesto a internet | Obligatorio |
| Secret CPE rotado trimestral | Recomendado |
| DEBUG desactivado en prod | Obligatorio |

---

## 13. Cronograma resumido

| Día | Entregable |
|-----|------------|
| D0 | VPS contratado, DNS configurado ✅ |
| D1 | MongoDB + GenieACS Docker aislado ✅ (2026-07-20) |
| D2 | TLS nginx + presets base — TLS ✅ / presets pendiente |
| D3 | PoC 1 ONU online en GenieACS |
| D4–D5 | Integración backend mínima |
| S2 | Piloto 50 ONUs |
| S3–S4 | Lotes TPLG/MSTC |

---

## 14. Riesgos y mitigación

| Riesgo | Mitigación |
|--------|------------|
| ONU bridge sin VEIP | Validar srv-profile antes de migrar |
| CPE no confía en certificado | Let's Encrypt, no self-signed |
| VSOL no soporta TR-069 útil | Excluir del rollout |
| Saturación VPS | Periodic Inform 3600s, no DEBUG |
| Backend no alcanza NBI | VPN site-to-site o túnel SSH persistente |

---

## 15. Referencias

- [GenieACS Docker (GeiserX)](https://github.com/GeiserX/genieacs-container)
- Compose Gigafiber: `scripts/genieacs/docker-compose.genieacs.yml`
- [GenieACS Environment Variables](https://docs.genieacs.com/en/latest/environment-variables.html)
- [genieacs-despliegue-gigafiber.md](./genieacs-despliegue-gigafiber.md)
- [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md)

---

## 16. Checklist final Go-Live

- [ ] VPS 4/8/80 operativo
- [ ] DNS `acs.gigafiberperu.cloud` resuelve
- [ ] HTTPS 443 responde
- [ ] Stack Docker `docker compose ps` healthy
- [ ] `.env` con JWT secret único
- [ ] UI admin accesible (VPN/IP restringida)
- [ ] Preset PoC configurado
- [ ] 1 ONU registrada y online
- [ ] OLT TR-069 Enable en piloto
- [ ] Backup mongodump probado
- [ ] Backend integración mínima desplegada
- [ ] Runbook de soporte documentado
