# GenieACS — despliegue y sizing para Gigafiber (~760 ONUs)

Documento de referencia para desplegar un ACS TR-069 propio. GenieACS es **self-hosted**: se instala en un **VPS o VM tuya** (AWS, Hetzner, DigitalOcean, VPS local, etc.). No existe servicio cloud oficial de GenieACS.

Contexto Gigafiber (auditoría OLT, jul 2026):

- ~760 ONUs en OLT
- ~50 ONUs (6.6%) en line profile con `TR069 management: Enable`
- ~706 ONUs con TR-069 desactivado en OLT; perfiles sin VEIP
- Candidatos reales a TR-069 tras reprovisionar: TPLG (~253), MSTC (~60), Huawei HGU (~280 parcial)
- VSOL bridge (~158): no prioritario para TR-069

---

## Arquitectura recomendada

### Fase 1 — PoC (Docker en VPS existente)

Todo en `/opt/gigafiber/genieacs` con Docker Compose:

```
Internet
   │
   ▼
[Nginx host :443]  acs.gigafiberperu.cloud
   │
   ▼
127.0.0.1:7547 ──► [Docker] genieacs (drumsergio/genieacs:1.2.16.0)
                         │
                         ▼
                   [Docker] mongo:7.0  (red interna, sin puerto público)
```

Archivos en repo: `scripts/genieacs/docker-compose.genieacs.yml`, `.env.example`, `deploy-genieacs.sh`, `nginx/acs.gigafiberperu.cloud.conf.example`.

### Fase 2 — Producción inicial (~760–1500 CPE TR-069)

Misma VM única es suficiente si:

- Periodic Inform ≥ 3600 s (1 hora) en CPE provisionados
- Provisionamiento mayormente en boot / connection request, no polling agresivo
- Sin retención masiva de logs/debug en disco

### Fase 3 — Escalar (>2000 CPE o alta frecuencia de Inform)

Separar MongoDB en otra VM o usar MongoDB Atlas / replica set:

```
[VPS ACS app]          [VPS MongoDB]
 genieacs-cwmp  ──────► MongoDB :27017
 genieacs-nbi           (4 vCPU / 8–16 GB RAM)
 genieacs-ui
 nginx + TLS
```

---

## Sizing por escenario

| Escenario | CPE TR-069 activos | vCPU | RAM | Disco (SSD) | MongoDB |
|-----------|-------------------|------|-----|-------------|---------|
| **PoC / lab** | 1–20 | 2 | 4 GB | 40 GB | Misma VM |
| **Producción Gigafiber (arranque)** | 50–400 | 4 | 8 GB | 80 GB | Misma VM |
| **Parque objetivo** | 400–1500 | 4–8 | 16 GB | 100 GB | Misma VM o VM dedicada |
| **Crecimiento** | 1500–5000 | 8 | 16–32 GB | 150 GB+ | VM dedicada recomendada |

Referencia comunidad GenieACS: ~5k CPE con Inform cada 30–60 min suele ir bien con **4 vCPU / 8 GB RAM / ~30 GB disco** en una sola VM.

Para Gigafiber, con arranque conservador (~50–300 CPE), sobra margen con **4 vCPU / 8 GB / 80 GB SSD**.

### ¿MongoDB en la misma VM o aparte?

| Criterio | Misma VM | VM separada |
|----------|----------|-------------|
| CPE TR-069 | **≤ 1500** | **> 1500** o carga alta |
| Presupuesto | Menor (1 VPS) | Mayor (2 VPS) |
| Operación | Más simple | Backups/escala independiente |
| Gigafiber fase 1–2 | **Recomendado** | Opcional |

**Conclusión Gigafiber:** empezar con **todo en un VPS** (4 vCPU, 8 GB, 80 GB SSD). Separar MongoDB solo si superan ~1500 CPE en TR-069 o el Inform periódico es muy frecuente (< 15 min).

---

## VPS recomendado (especificaciones concretas)

### Opción mínima PoC

| Parámetro | Valor |
|-----------|-------|
| Tipo | VPS general purpose |
| vCPU | 2 |
| RAM | 4 GB |
| Disco | 40 GB SSD |
| OS | Ubuntu 22.04 LTS o Debian 12 |
| IP | 1 pública IPv4 |
| Swap | 2 GB (opcional en PoC) |

### Opción producción Gigafiber (recomendada)

| Parámetro | Valor |
|-----------|-------|
| Tipo | VPS general purpose (no burstable barato) |
| vCPU | **4** |
| RAM | **8 GB** |
| Disco | **80 GB SSD** |
| OS | Ubuntu 22.04 LTS |
| Red | IP pública + posibilidad de reglas firewall |
| Backups | Snapshot semanal del VPS |

Ejemplos de instancias equivalentes (referencia, no obligatorio):

- AWS: `t3.medium` o `t3a.medium`
- Hetzner: CPX31 (4 vCPU / 8 GB)
- DigitalOcean: Premium 4 GB / 8 GB droplet (4 vCPU)
- Vultr: 4 vCPU / 8 GB

Ubicación: preferir región con **baja latencia hacia Perú** (us-east-1 Miami, sa-east-1 São Paulo) o VPS en Perú/LATAM si hay buen peering hacia clientes.

---

## Red y DNS (obligatorio)

| Elemento | Recomendación |
|----------|---------------|
| Hostname | `acs.gigafiberperu.cloud` |
| VPS / IP | `212.85.13.47` (`srv1043610`, mismo host que `api`, `backoffice`, `observability`) |
| DNS | Registro **A** `acs` → `212.85.13.47` (TTL 14400) — **ya configurado** |
| Puerto CWMP | 443 (TLS vía nginx) o 7547 |
| Certificado | Let's Encrypt; evitar self-signed en CPE |
| Firewall VPS | Abrir 443 (CWMP) desde internet; **cerrar** 7557/7567/27017 al público |
| NBI / UI | Solo IP oficina, VPN o túnel SSH |
| VLAN gestión OLT | VLAN dedicada para tráfico TR-069 de ONUs hacia internet/ACS |
| OLT | Line profile con `tr069-management enable` + srv-profile con VEIP |

---

## Variables GenieACS a tunear (760 CPE)

| Variable | Valor sugerido | Nota |
|----------|----------------|------|
| `CWMP_WORKER_PROCESSES` | `2`–`4` | PoC: 2; prod 760: 4 |
| `MAX_CONCURRENT_REQUESTS` | `50`–`100` | Subir solo si hay colas |
| Periodic Inform en CPE | **3600 s** (1 h) | No usar 2–5 min salvo unprovisioned |
| Connection Request | STUN o IP pública ACS | Para acciones on-demand desde backoffice |

Evitar `DEBUG=1` en producción: llena disco en días (experiencia forum GenieACS).

---

## Plan de rollout alineado al parque Gigafiber

| Fase | CPE objetivo | Infra ACS | Acción OLT |
|------|--------------|-----------|------------|
| 0 | 0 | PoC 2 vCPU / 4 GB | Validar 1 TPLG o 1 MSTC en lab |
| 1 | 50 | Prod 4 vCPU / 8 GB | ONUs ya en profile 1 (`SMARTOLT_FLEXIBLE_GPON`) |
| 2 | 150–300 | Misma VM | Migrar TPLG + MSTC a profile con VEIP + TR-069 |
| 3 | 400+ | Revisar RAM MongoDB | Huawei HGU selectivo; excluir VSOL bridge |
| 4 | 1500+ | MongoDB en VM aparte | Escalar según métricas |

---

## Integración con ispadmin-backend

El ACS no reemplaza al backend. Responsabilidades:

| Sistema | Rol |
|---------|-----|
| **GenieACS** | Protocolo CWMP, estado CPE, presets, tareas |
| **ispadmin-backend** | Orquestación negocio: suscripción ↔ serial ↔ deviceId GenieACS |
| **OLT** | Aprovisionamiento inicial URL/credenciales ACS vía OMCI |
| **SmartOLT** | Perfil `tr069_profile` al autorizar ONU (cuando aplique) |

API mínima backend → GenieACS NBI:

| Método | Endpoint | Uso en backend |
|--------|----------|----------------|
| `GET` | `/devices/?projection=_id,_lastInform,_deviceId` | Listar CPE y correlacionar por **últimos 6 hex** del serial SmartOLT (`Tr069SerialMatcher`) |
| `POST` | `/devices/{deviceId}/tasks?connection_request` | `setParameterValues` (WAN+VLAN+WiFi) / `getParameterValues` / `reboot` |
| `POST` | `/devices/{deviceId}/tasks` | Mismo, sin Connection Request (fallback si CR falla; espera próximo Inform) |
| `GET` | `/devices/?query={"_id":"…"}&projection=…` | Verificar SSID aplicados tras el set |

Implementación: paquete `service/genieacs/` (`GenieAcsClient`, `Tr069ProvisioningService`, `Tr069PostInstallProvisioner`). Se invoca **después** de `registerSubscription` (fuera del `@Transactional`) en `POST /subscription` y `/subscription/with-facade-photo`.

Propiedades Spring (`genieacs.*` ← env `GENIEACS_*`): ver [vps-secrets-management.md](./vps-secrets-management.md). Feature flag: `GENIEACS_ENABLED` (default `false`).

---

## Plan de implementación en VPS

Plan operativo paso a paso (comandos, nginx, OLT, backend, cronograma): **[genieacs-plan-implementacion-vps.md](./genieacs-plan-implementacion-vps.md)**

## Checklist de despliegue (Docker)

1. ~~Verificar RAM libre en VPS `212.85.13.47` (≥ 2 GB)~~ ✅ (2026-07-20)
2. ~~DNS `acs.gigafiberperu.cloud` → `212.85.13.47`~~ ✅
3. ~~`./scripts/genieacs/deploy-genieacs.sh` → `/opt/gigafiber/genieacs`~~ ✅ (2026-07-20)
4. ~~Configurar `.env` (secretos JWT + Mongo)~~ ✅ generado en deploy
5. ~~Nginx + `certbot --nginx -d acs.gigafiberperu.cloud`~~ ✅ TLS hasta 2026-10-18
6. Presets por modelo en UI GenieACS — **pendiente**
7. PoC: 1 ONU TPLG/MSTC + `tr069-mgmt` en OLT — **pendiente**
8. ~~Integrar NBI backend → `127.0.0.1:7557`~~ ✅ (cliente NBI + alta FIBER; activar con `GENIEACS_ENABLED=true` en Tomcat)
9. Piloto 50 ONUs (profile OLT 1) — **pendiente**
10. Backup cron `mongodump` desde contenedor Mongo — **pendiente**

---

## Estado piloto desplegado (2026-07-20)

Stack **totalmente aislado** en Docker Compose (`name: genieacs-pilot`), independiente del compose de Tomcat/MySQL/Meili en `/opt/gigafiber/docker-compose.yml`.

| Elemento | Valor |
|----------|-------|
| Directorio | `/opt/gigafiber/genieacs` |
| Proyecto Compose | `genieacs-pilot` |
| Red Docker | `genieacs_pilot_internal` (solo contenedores GenieACS + Mongo) |
| Contenedores | `gigafiber-genieacs`, `gigafiber-genieacs-mongo` |
| Volúmenes | `genieacs_pilot_mongo_data`, `genieacs_pilot_ext`, `genieacs_pilot_logs` |
| CWMP público | `https://acs.gigafiberperu.cloud/` (nginx → `127.0.0.1:7547`) |
| NBI | `127.0.0.1:7557` (solo localhost VPS) |
| UI admin | `127.0.0.1:7567` (túnel SSH) |
| MongoDB | Sin puerto en host; auth activo; límite 768 MB RAM |
| GenieACS | Límite 1536 MB RAM; workers piloto: 2 |
| Swap VPS | 2 GB (`/swapfile`) añadido en deploy |
| RAM post-deploy | ~3,4 GB usados / ~4,3 GB disponibles |
| Verificación | `bash /opt/gigafiber/genieacs/verify-genieacs-pilot.sh` |
| Config piloto | `bash /opt/gigafiber/genieacs/configure-genieacs-pilot.sh` |

### Configuración piloto aplicada (2026-07-21, actualizado 2026-08-25)

| Item | Valor |
|------|-------|
| `cwmp.auth` | **`true`** — sin validación HTTP user/pass en Inform. Motivo: gSOAP VSOL/ZTE no completa Digest 401. Detalle: [genieacs-cwmp-auth-http.md](./genieacs-cwmp-auth-http.md) |
| Restricción red CWMP | nginx `allow 38.224.231.4; deny all;` en `:80`/`:443` |
| `cwmp.deviceOnlineThreshold` | `3600` |
| Periodic Inform | **3600 s** (provision `inform`) |
| Presets | `bootstrap`, `default`, `inform`, `tplg-router`, `mstc-router` |
| Provisions | `gigafiber-bootstrap`, `tplg-router`, `mstc-router` |
| Credenciales CPE | `ACS_CPE_USERNAME` / `ACS_CPE_PASSWORD` en `/opt/gigafiber/genieacs/.env` |
| Provision `inform` | Mismas credenciales para **ACS** (`Username`/`Password`) y **Connection Request** (`ConnectionRequestUsername`/`ConnectionRequestPassword`). **No** regenera password aleatorio en cada Inform (ver script `configure-genieacs-pilot.sh`). |

**OLT (misma contraseña que `ACS_CPE_PASSWORD`):**

```
tr069-mgmt 1 acs https://acs.gigafiberperu.cloud/ validate basic username gigafiber-acs password <ACS_CPE_PASSWORD>
```

Comandos operativos:

```bash
# Desde repo local (build + deploy completo)
./scripts/genieacs/deploy-genieacs.sh

# UI admin (primera vez: wizard usuario admin)
ssh -L 7567:127.0.0.1:7567 root@212.85.13.47
# http://127.0.0.1:7567

# Logs
ssh root@212.85.13.47 'cd /opt/gigafiber/genieacs && docker compose logs -f genieacs'
```

---

## Estimación de costo mensual (referencia)

| Concepto | Rango USD/mes |
|----------|---------------|
| VPS 4/8/80 | 20–40 |
| Dominio + DNS | ~1–2 (si no existe) |
| Backups snapshot | 5–10 |
| **Total fase 1** | **~25–50 USD/mes** |

Segundo VPS para MongoDB (solo fase 3+): +20–40 USD/mes.

---

## Connection Request — VPN VPS ↔ MK2 (ago 2026)

GenieACS en el VPS debe alcanzar la **Connection Request URL** de cada ONU (`http://192.168.x.x:7547/tr069`). Los pools viven en **MK2** (`host_device_id=8`); se reutiliza el túnel **`wg-olt`** existente (no un segundo WireGuard).

| Elemento | Valor |
|----------|--------|
| VPS WG | `wg-olt` · `10.255.255.2/30` · `:51820` |
| MK2 WG | `wg-ispadmin-vps` · `10.255.255.1/30` · `:51830` |
| Rutas abonados | 21× `/24` de tabla `ip_pool` prod (incl. **`192.168.123.0/24`** lab GenieACS) → `dev wg-olt` |
| MK2 firewall | TCP `:7547` desde `10.255.255.2` · interfaz `wg-ispadmin-vps` |

Scripts repo:

| Archivo | Uso |
|---------|-----|
| `scripts/genieacs/apply-genieacs-wg-customer-routes.sh` | VPS: `AllowedIPs` + rutas |
| `scripts/genieacs/wg-olt-customer-routes.sh` | VPS: `PostUp`/`PostDown` persistente |
| `scripts/genieacs/mk2-genieacs-cr-forward.rsc` | MK2: reglas firewall CR `:7547` |
| `scripts/genieacs/mk2-genieacs-provisioning-pools.rsc` | MK2: comment **Pool de aprovisionamiento GenieACS TR-069** en gateways |
| `scripts/genieacs/wg-olt-genieacs-allowedips.example` | Plantilla `AllowedIPs` |

Aplicado en prod **2026-08-20**: rutas OK (`ping 192.168.30.1` desde VPS ~85 ms). **Puerto 7547** en CPE suele ir **cerrado en WAN** (`Nucom_WanAccessCfg` en VSOL); MK2 tampoco conecta a `:7547` hasta abrirlo por TR-069 o plantilla. Con routing listo, usar `?connection_request` en NBI cuando el CPE escuche.

**Lab `192.168.123.0/24`:** pool id **162** en `ip_pool`; MK2 `192.168.123.1/24` en `LAN_MK1` comment *Pool de aprovisionamiento GenieACS TR-069*. Desde VPS: `curl http://192.168.123.3:7547/tr069` → **401** (CR alcanzable; usar `?connection_request` en NBI).

---

## Tareas NBI — aprovisionamiento (`setParameterValues`)

Provisionar WAN + VLAN + WiFi en un solo POST. **Validado en lab** VSOL V2804AX15T (ago 2026).

| Campo | Valor |
|-------|-------|
| **URL** | `POST http://127.0.0.1:7557/devices/{deviceId}/tasks?connection_request` |
| **deviceId lab** | `B46415-V2804AX15T-12345B4641531C0B6` |
| **Body** | `{"name":"setParameterValues","parameterValues":[[path,value,type],…]}` |
| **Respuesta OK** | **202** sin `Incorrect connection request credentials` |

Payload completo (IP `192.168.123.4`, VLAN `1`, WiFi `acs2g`/`acs5g`): **[genieacs-vsol-v2804-parametros.md § Aprovisionamiento validado](./genieacs-vsol-v2804-parametros.md#aprovisionamiento-validado--lab-ago-2026)**.

Resumen de paths clave:

| Grupo | Paths |
|-------|--------|
| WAN IP | `…WANConnectionDevice.4.WANIPConnection.1.{AddressingType,ExternalIPAddress,SubnetMask,DefaultGateway,DNSServers,DNSEnabled}` |
| VLAN (triple) | `…X_CT-COM_VLANIDMark`, `…X_ZTE-COM_VLANID`, `…X_ZTE-COM_VLANEnable`, `…WANGponLinkConfig.VLANIDMark` |
| WiFi | `…WLANConfiguration.5` (2.4G), `…WLANConfiguration.1` (5G) → `SSID`, `KeyPassphrase` |

Prerrequisitos: túnel SSH a NBI, VPN `wg-olt`, credenciales CR = `ACS_CPE_USERNAME` / `ACS_CPE_PASSWORD`.

---

## Tareas NBI — reboot vía TR-069

Reiniciar un CPE remotamente usando la RPC CWMP **`Reboot`**, encolada como tarea GenieACS `reboot`.

| Campo | Valor |
|-------|-------|
| **Endpoint NBI** | `POST /devices/{deviceId}/tasks` |
| **deviceId lab VSOL** | `B46415-V2804AX15T-12345B4641531C0B6` |
| **Body** | `{"name":"reboot"}` |
| **Inmediato** | Añadir `?connection_request` (requiere CR alcanzable + credenciales correctas) |
| **Diferido** | Sin query → se ejecuta en el próximo Inform del CPE |

### Túnel SSH (Postman / curl local)

La NBI escucha solo en el VPS (`127.0.0.1:7557`):

```bash
ssh -L 7557:127.0.0.1:7557 -L 3000:127.0.0.1:3000 root@212.85.13.47
# NBI: http://127.0.0.1:7557
# UI:  http://127.0.0.1:3000
```

### curl — reboot inmediato (Connection Request)

```bash
curl -X POST \
  'http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?connection_request' \
  -H 'Content-Type: application/json' \
  -d '{"name":"reboot"}'
```

Respuesta esperada: **HTTP 202** + JSON con `_id` de la tarea. Sin el mensaje `Incorrect connection request credentials`.

### curl — reboot diferido (espera Inform)

```bash
curl -X POST \
  'http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks' \
  -H 'Content-Type: application/json' \
  -d '{"name":"reboot"}'
```

Alternativa en panel VSOL: **Management → TR-069 → Inform** (manual).

### Postman

| Campo | Valor |
|-------|-------|
| Method | `POST` |
| URL | `http://127.0.0.1:7557/devices/B46415-V2804AX15T-12345B4641531C0B6/tasks?connection_request` |
| Header | `Content-Type: application/json` |
| Body (raw JSON) | `{"name":"reboot"}` |

### Comportamiento esperado

1. GenieACS envía RPC `Reboot` al CPE (vía CR o en la siguiente sesión CWMP).
2. El CPE reinicia (~1–3 min); la sesión TR-069 se corta (normal).
3. Tras el boot, el CPE hace **Inform** al ACS (`_lastInform` se actualiza en UI/Mongo).

### Verificación

```bash
# Faults del device (debe estar vacío o sin fault reciente de reboot)
curl -s 'http://127.0.0.1:7557/faults/?query=%7B%22device%22%3A%22B46415-V2804AX15T-12345B4641531C0B6%22%7D'

# Estado del device (last inform)
curl -s 'http://127.0.0.1:7557/devices/?query=%7B%22_id%22%3A%22B46415-V2804AX15T-12345B4641531C0B6%22%7D'
```

En UI GenieACS: device → **Last inform** posterior al reboot.

### Notas operativas

- **Una tarea por petición:** no mezclar `reboot` con `setParameterValues` en el mismo POST.
- Orden típico: (1) config WAN/WiFi → (2) reboot solo si hace falta aplicar en cold boot.
- Credenciales CR deben coincidir con las del CPE (`ACS_CPE_USERNAME` / `ACS_CPE_PASSWORD`); el provision `inform` las mantiene alineadas.
- Si el túnel SSH cae, `curl` a `127.0.0.1:7557` falla — reabrir túnel antes de reintentar.

### Otras tareas NBI frecuentes

| Tarea | Body mínimo | Uso |
|-------|-------------|-----|
| `setParameterValues` | `{"name":"setParameterValues","parameterValues":[[path,value,type],…]}` | IP, VLAN, WiFi |
| `getParameterValues` | `{"name":"getParameterValues","parameterNames":[…]}` | Leer parámetros |
| `refreshObject` | `{"name":"refreshObject","objectName":"InternetGatewayDevice.WANDevice"}` | Refrescar árbol |
| `reboot` | `{"name":"reboot"}` | Reinicio remoto |
| `factoryReset` | `{"name":"factoryReset"}` | Reset de fábrica (**destructivo**) |

Parámetros VSOL V2804AX15T: [genieacs-vsol-v2804-parametros.md](./genieacs-vsol-v2804-parametros.md).

Verificación VPS:

```bash
ip route get 192.168.30.36   # debe salir dev wg-olt
ping -c1 192.168.30.1
curl -v --connect-timeout 4 http://192.168.30.36:7547/tr069   # 401/200 si CPE abre CR
```

Regenerar lista `/24` desde MySQL:

```sql
SELECT REPLACE(ip_segment, '.1/24', '.0/24') FROM ip_pool ORDER BY 1;
```

---

## Persistencia ACS en plataforma (`subscription_acs`)

Tras el alta FIBER con match GenieACS, el backend hace **upsert** de un snapshot en `subscription_acs` (migración **V26**, 1:1 con `subscription`). El status operativo del alta sigue en `subscription.tr069_*` (V25); la tabla ACS es proyección para backoffice/plataforma **sin** consultar el árbol completo del ACS ni exponer secretos.

### Cuándo se escribe

| Momento | Qué pasa |
|---------|----------|
| Provisión `COMPLETE` | Upsert con device id, Inform, modelo/OUI, SSIDs, WAN IP cache, task id |
| Provisión `MANUAL_REQUIRED` **con** `deviceId` | Upsert parcial (device + error + metadatos del poll) |
| `MANUAL_REQUIRED` sin match / `NA` | No se crea fila ACS |
| Fallo del upsert ACS | Se loguea; **no** falla el alta |

Hook: `Tr069PostInstallProvisioner` → `SubscriptionAcsSyncService.upsertFromProvision` tras persistir `tr069_*`.

### Campos por fase

| Fase | Campo (`subscription_acs`) | Origen | Notas |
|------|----------------------------|--------|-------|
| **1 imprescindible** | `subscription_id` | PK/FK | 1:1 con `subscription` |
| 1 | `genieacs_device_id` | `_id` | Espejo de `subscription.tr069_device_id` |
| 1 | `serial_suffix` | Últimos 6 hex SmartOLT/GenieACS | Correlación |
| 1 | `smartolt_serial` | SN ONU del alta | Nullable |
| 1 | `provision_status` / `last_error` | Outcome TR-069 | Alineado a `tr069_provision_status` / `tr069_last_error` |
| 1 | `provisioned_at` / `updated_at` | Reloj app | `provisioned_at` al primer COMPLETE |
| 1 | `last_inform_at` | `_lastInform` | |
| 1 | `product_class` / `oui` / `manufacturer` | `_deviceId.*` | |
| **2 útil** | `connection_request_url` | `ManagementServer.ConnectionRequestURL` | **Sin** password CR |
| 2 | `wan_ip_cache` | IP provisionada / poll | Cruzar con `subscription.ip` |
| 2 | `ssid_24` / `ssid_5` | Request / verificación | Solo nombres; claves en V25 cifradas |
| 2 | `software_version` / `hardware_version` | `_deviceId._Software*` / `_Hardware*` | |
| 2 | `last_boot_at` | `_lastBoot` | Nullable |
| 2 | `last_task_id` / `last_task_status` / `last_task_at` | Respuesta NBI `setParameterValues` | |
| **3 opcional** | tags / faults / historial WiFi | — | **No implementado**; documentado en plan |
| **No guardar** | Árbol completo, WiFi/admin passwords, `GENIEACS_*`, CR password | — | Nunca en BD de negocio |

### Contrato DTO (`SubscriptionAcsDto`)

Para detalle de suscripción / backoffice futuro (controllers siempre DTO):

```json
{
  "subscriptionId": 101,
  "genieacsDeviceId": "B46415-V2804AX15T-…",
  "serialSuffix": "31C0B6",
  "smartoltSerial": "VSOL0031C0B6",
  "provisionStatus": "COMPLETE",
  "tr069RequiresManualConfig": false,
  "lastError": null,
  "lastInformAt": "2026-08-20T12:00:00",
  "productClass": "V2804AX15T",
  "oui": "B46415",
  "manufacturer": "VSOL",
  "connectionRequestUrl": "http://192.168.123.4:7547/tr069",
  "wanIpCache": "192.168.123.4",
  "ssid24": "acs2g",
  "ssid5": "acs5g",
  "softwareVersion": "V1.0",
  "hardwareVersion": "V1.1",
  "lastBootAt": "2026-08-20T11:55:00",
  "lastTaskId": "task-1",
  "lastTaskStatus": "accepted",
  "lastTaskAt": "2026-08-20T12:01:00",
  "provisionedAt": "2026-08-20T12:01:00",
  "updatedAt": "2026-08-20T12:01:00"
}
```

Endpoint `GET /subscription/{id}/acs`, `POST .../acs/refresh` y `POST .../acs/reboot` disponibles para backoffice. Job periódico de sync: **follow-up**.

### Proyección NBI usada en el poll

```
_id,_lastInform,_lastBoot,_deviceId,InternetGatewayDevice.ManagementServer.ConnectionRequestURL
```

---

## Parámetros TR-069 por modelo

- **VSOL V2804AX15T** (HGU lab, IP/VLAN/WiFi writable): [genieacs-vsol-v2804-parametros.md](./genieacs-vsol-v2804-parametros.md)

## Comandos OLT relacionados

Ver catálogo: `.agent-docs/olt-gateway-comandos-catalogo.md`

Configuración TR-069 en OLT (referencia):

```
# line profile
tr069-management enable
tr069-management ip-index 0

# por ONU (pon-onu-mng)
tr069-mgmt 1 acs https://acs.gigafiberperu.cloud/ validate basic username USER password PASS
tr069-mgmt 1 state unlock
```

---

## Criterio de éxito PoC

- 1 CPE aparece en GenieACS UI tras boot
- Backend puede reiniciar CPE vía NBI
- Cambio WiFi remoto en 1 modelo TP-Link o MitraStar
- OLT muestra `TR069 management: Enable` en el line profile del piloto
- Sin saturación de CPU/RAM en VPS con ≤ 20 CPE de prueba
