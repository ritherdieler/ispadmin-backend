# Contexto LLM — OLT Manager (Huawei MA5608T)

Documento canónico de contexto para LLMs y desarrollo. Adapta la visión original (“sistema propio vs SmartOLT”) al **módulo ya implementado** `oltgateway`.

Origen conceptual: borrador en Downloads (*Contexto para LLM — Sistema propio de gestión de OLT Huawei MA5608T*).  
Detalle técnico: no duplicar aquí; enlazar docs abajo.

---

## 1. Meta

Independizar las operaciones críticas GPON de SmartOLT mediante un **OLT Manager propio** (paquete `com.dscorp.wispadmin.oltgateway.**`) que habla con la Huawei MA5608T y expone HTTP al resto de WispAdmin / BackOffice.

No se busca clonar SmartOLT completo. Prioridad: velocidad, estabilidad y bajo consumo de sesiones CLI en la OLT.

### OLT en producción (verificado 2026-08-26)

| Campo | Valor |
|-------|--------|
| Fabricante | Huawei |
| Modelo / Product | SmartAX MA5608T |
| VERSION | `MA5600V800R015C00` |
| PATCH | `SPH106 HP1013` |
| Área activa | Program A / Data A |
| Mgmt IP | `10.11.104.2:22` |
| Usuario gateway | `oltadmin` |

### Stack

- **Kotlin** + Spring Boot (WAR WispAdmin)
- MySQL (tablas `olt_mgr_*`; misma JDBC temporal, dominio propio del gateway)
- SSH CLI Huawei (única vía live hoy)
- HTTP `/api/olt-gateway` con `X-Olt-Gateway-Key`

---

## 2. Estado del proyecto (DONE / PARTIAL / TODO)

| Capacidad | Estado | Dónde |
|----------|--------|-------|
| Detectar ONTs autofind | **DONE** | `GET .../onus/autofind`, `GET .../onu/unconfigured_onus` (SSH live) |
| Listar pendientes de autorización | **DONE** | Mismo autofind; no hay cola persistente dedicada |
| Autorizar ONT | **DONE** (gated) | `POST .../onu/authorize_onu` → `ont add` + `service-port`; `olt.gateway.writes.enabled` |
| Eliminar ONT | **DONE** (gated) | `POST .../onu/delete/{externalId}` → soft-delete DB + CLI |
| Consultar ONTs registradas | **DONE** | Live `GET /onus`; DB `GET /onus/configured` |
| Estado Online/Offline | **DONE** | Inventory sync → `olt_mgr_onu_status_current` |
| Potencia óptica | **DONE** | Signal poll **SNMP** canónico (~5 min); SSH optical **deprecado** — [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md) |
| Info técnica por ONT | **DONE** | By-SN / detalle F/S/P/ONT ID; SNMP SN+ifIndex también PASS |
| Alarmas / eventos | **PARTIAL** | Poll SSH `display alarm active all` → NetDiag; traps SNMP **disabled** / 0 targets |
| Actualización automática | **DONE** | Inventory (~10 min SNMP) + signal (~5 min SNMP) si enabled |
| SSH connection manager + cola | **DONE** | `OltCliBus` + `HuaweiCliSession` (pool=1) |
| SNMP walk bus | **DONE** | `OltSnmpBusRegistry` por OLT; MA5608T = 1 GETBULK (límite de modelo) |
| Abstracción dominio (no CLI en controller) | **DONE** | Facade / CommandService / QueryService |
| Estado local MySQL | **DONE** | Capa A config + B telemetría |
| Auditoría admin | **DONE** | `olt_mgr_audit_log` + `olt_mgr_task` |
| Circuit breaker / OLT offline | **DONE** | `OltReachabilityTracker` |
| Mock | **DONE** | `olt.gateway.mock.enabled` |
| Compat 6 ops SmartOLT (WispAdmin) | **DONE** | `SmartOltCompatController` + `OltHttpClient` |
| SNMP GET/GETBULK / walk | **PARTIAL** | Inventario + óptica canónicos SNMP — [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md) |
| SNMP Trap receiver | **PARTIAL** | `OltSnmpTrapReceiver` ASN.1 (udp/1162, off). OLT AS-IS: traps disabled + 0 targets. Sin mapeo NetDiag aún — [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md) §Traps |
| Multi-OLT / adapters ZTE/VSOL | **TODO** | Modelo `olt_mgr_olt` preparado; una OLT en seed |
| UI BackOffice OLT Manager | **TODO** | Consumo actual vía WispAdmin (alta FIBER) + scripts ops |
| Catálogo VLAN / service-port / templates | **PARTIAL** | Entities stub; sin sync/API CRUD |
| Backup / restore | **PARTIAL** | Scripts TFTP/GDrive; no API gateway |
| Señales cada ~15 s (estilo SmartOLT) | **TODO** | SNMP óptica full ~104 s; inventario/estado ~1–2 s — candidato sync híbrido, no 15 s óptica aún |

---

## 3. Arquitectura AS-IS

Modelo de 3 capas (detalle: [olt-gateway-3layer.md](./olt-gateway-3layer.md)):

| Capa | Rol | Implementación |
|------|-----|----------------|
| **A** | Config deseada | `olt_mgr_onu` + catálogos |
| **B** | Telemetría cacheable | `olt_mgr_onu_status_current` |
| **C** | CLI SSH + SNMP RO | `OltCliBus` → `HuaweiCliSession`; `OltSnmpBus` → UDP :161 |

```text
WispAdmin / BackOffice
        │  HTTP (X-Olt-Gateway-Key)
        ▼
  /api/olt-gateway
        │
        ▼
  OltManagerFacade
     ├── A/B  olt_mgr_* (MySQL)
     └── C    OltCliBus (1 SSH) + OltSnmpBusRegistry (1 bus/OLT, límite por modelo)
                │
                ▼
           MA5608T 10.11.104.2
```

**Frontera obligatoria:** WispAdmin solo es cliente HTTP. Prohibido inyectar facade/repos del gateway en `OnuService` / `OltService`.

### API (base `/api/olt-gateway`)

**Nativa**

| Método | Path | Fuente |
|--------|------|--------|
| GET | `/health` | Reachability |
| GET | `/olt/info` | `display version` + boards |
| GET | `/onus` | Inventario SSH live |
| GET | `/onus/configured` | DB A+B paginado |
| GET | `/onus/autofind` | Autofind live |
| GET | `/onus/by-sn/{sn}` | Detalle |
| GET | `/onus/{slot}/{port}/{ontId}` | Detalle F/S/P |
| GET | `/onus/.../optical` | Óptica unitaria |
| POST | `/admin/sync/inventory` | Sync SSH→DB |
| POST | `/admin/sync/signal` | Poll óptico→DB |
| GET | `/admin/sync/status` | Estado + cola bus |

**SmartOLT-compat (WispAdmin)**

| Op | Path |
|----|------|
| Unconfigured | `GET /onu/unconfigured_onus` |
| By SN | `GET /onu/get_onus_details_by_sn/{sn}` |
| Authorize | `POST /onu/authorize_onu` |
| Move | `POST /onu/move/{sn}` |
| Delete | `POST /onu/delete/{externalId}` |
| Reboot | `POST /onu/reboot/{externalId}` |

Código: `OltGatewayController`, `SmartOltCompatController`, `OltManagerFacade`, `OltGatewayQueryService`, `OltGatewayCommandService`.

---

## 4. Filosofía objetivo (TO-BE) — evolución, no prerequisito

El módulo **ya corre** con SSH + estado local. La visión a largo plazo sigue siendo:

> **SNMP para monitorear. SNMP Traps para reaccionar. SSH para configurar.**

Hoy: **SSH para monitorear y configurar**; traps sustituidos por poll de alarmas.  
SNMP GET/GETBULK: **validado + cliente inventario** — [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md). SSH inventory deprecado.

```text
                    OBJETIVO
                       │
         ┌─────────────┼─────────────┐
         │             │             │
       SNMP       SNMP TRAPS        SSH
     (futuro)     (futuro)       (hoy + writes)
         │             │             │
         └─────────────┼─────────────┘
                       ▼
                 Estado local olt_mgr_*
```

No abandonar SNMP: usarlo para bajar carga CLI (señal masiva, autofind rápido) cuando la investigación en *esta* versión lo confirme.

---

## 5. SSH (canónico hoy)

No abrir una sesión por comando. No asumir sesión eterna.

### Componentes

| Pieza | Rol |
|-------|-----|
| `OltSshClient` | Conexión SSH (algoritmos legacy) |
| `HuaweiCliSession` | Shell, enable, `config`/`mmi-mode`/`scroll`, More, confirm `{ <cr>|... }` |
| `OltCliBus` | Cola priorizada, 1 worker, skip duplicados, keepalive |
| `OltSnmpBusRegistry` | Un `OltSnmpBus` por OLT; límite = modelo (`MA5608T` → 1 walk) |
| `OltReachabilityTracker` | Fallos consecutivos → backoff / circuit |

### Prioridad de jobs

```text
WRITE > ADHOC > KEEPALIVE > INVENTORY | SIGNAL_POLL | ALARM_POLL
```

`pool-size=1`. El Reenter OLT (~4) queda libre para operadores humanos; el gateway no multiplica sesiones.

### Paginación CLI

Preparación de sesión encapsulada en `HuaweiCliSession` (`mmi-mode enable`, `scroll 512`). Fallback: detectar `---- More ----` y enviar espacio; confirm `<cr>` automático.

Guía hardware/CLI: [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md).  
Catálogo de comandos usados: [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md).  
Bus: [olt-gateway-cli-bus.md](./olt-gateway-cli-bus.md).

---

## 6. Estado local y polling actual

El frontend / WispAdmin **no** debe martillar la OLT en cada pantalla: leer DB (`/onus/configured`) o sync admin.

| Job | Cadencia default | Propiedad | CLI principal |
|-----|------------------|-----------|---------------|
| Inventory | ~10 min `fixedDelay` | `olt.gateway.sync.inventory-interval-ms` | `display ont info` slot-all / port-all |
| Signal | ~10 min | `olt.gateway.sync.signal-interval-ms` | `display ont optical-info {port} all` |
| Alarm (NetDiag) | Poll periódico | netdiag | `display alarm active all` |

Contraste con SmartOLT (~15 s de señal): **no replicar por SSH N×ONU**. Si se necesita esa cadencia, investigar SNMP GETBULK en la OLT real antes de acortar el poll SSH.

Inventario real (referencia): ~760 ONUs, slots GPON 0 y 1 (GPFD); slot 1 suele ir a port-all.

---

## 7. Modelo de datos

Diseño: [olt-manager-db-model.md](./olt-manager-db-model.md).

**Núcleo en uso:** `olt_mgr_olt`, `olt_mgr_olt_model`, `olt_mgr_zone`, `olt_mgr_onu_type`, `olt_mgr_onu`, `olt_mgr_onu_status_current`, `olt_mgr_task`, `olt_mgr_audit_log`, `olt_mgr_sync_run`.

**Stubs (entities sin sync/API CRUD):** `olt_mgr_onu_service_port`, `olt_mgr_onu_extra_vlan`, `olt_mgr_custom_template`, `olt_mgr_speed_profile`, `olt_mgr_olt_pon_port`, `olt_mgr_olt_vlan`.

Credenciales: `olt.gateway.password` / env `OLT_GATEWAY_PASSWORD` (no hardcodear en prod). Seed: `OltMgrSeedRunner`.

---

## 8. Auditoría, tolerancia, seguridad

### Hecho

- Task + audit en authorize/move/delete/reboot
- Timeouts CLI, retry controlado vía bus, skip sync si write running
- Circuit / backoff si OLT inalcanzable ([olt-gateway-reachability-unavailable.md](./olt-gateway-reachability-unavailable.md))
- API key en gateway; writes off por defecto en prod

### Gaps

- Receptor SNMP traps + reconciliación trap↔poll
- Rate limiting fino por usuario UI
- SNMPv3 / community fuera de properties en claro
- Multi-OLT con credenciales cifradas por fila
- Prevención explícita de comandos duplicados de write más allá del bus

---

## 9. Roadmap reordenado

No empezar “Fase 1 SNMP antes de escribir el módulo”: el módulo SSH ya existe.

### Hecho (no reimplementar)

1. Cliente SSH + prompt/paginación/reconexión  
2. Cola / connection manager (`OltCliBus`)  
3. Inventario ONUs + sync DB  
4. Óptica (poll por puerto)  
5. Autofind, authorize, delete, move, reboot (writes gated)  
6. Estado local A/B + auditoría  
7. Alarmas activas vía CLI → NetDiag  
8. Compat HTTP SmartOLT (6 ops) + mock  

### Investigación SNMP — hecha (2026-08-26)

Resultados: [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md).  
Firmware probado: `MA5600V800R015C00` + `SPH106 HP1013`.  
Resumen: SNMPv2c RO OK; GPON `1.3.6.1.4.1.2011.6.128`; traps **off**.

### Después

- Cliente SNMP RO en `oltgateway` (GETBULK inventory/status/optical/autofind)  
- Trap enable + target controlado + receptor ASN.1 (no reusar NetDiag MikroTik `:1620` sin parser)  
- Bajar cadencia de señal cuando el cliente SNMP esté en prod  
- Completar stubs VLAN/service-port o APIs de catálogo  
- UI BackOffice (inventario, pendientes, authorize, alarmas)  
- Multi-OLT / `OltAdapter` por fabricante (dominio ya desacoplado del CLI en controllers)  

### Fuera de alcance inmediato del gateway

Backup TFTP/GDrive (scripts ops), DST-NAT/GRE VPS (docs infra), clonar graphs/batch/CATV SmartOLT.

---

## 10. Reglas para el LLM

Actúa como arquitecto/backend en Kotlin/Spring, GPON Huawei MA5608T, SSH CLI y (cuando toque) SNMP.

1. **No inventes** comandos CLI, MIBs, OIDs ni capacidades de firmware.  
2. **Antes de proponer código nuevo**, lee:
   - este doc,
   - [olt-gateway-3layer.md](./olt-gateway-3layer.md),
   - [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md),
   - [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md),
   - clases en `oltgateway` citadas arriba.  
3. **No propongas** “crear el SSH client / connection manager desde cero”. Extiende lo existente.  
4. Respeta la **frontera HTTP** con WispAdmin.  
5. Para CLI nuevo o cambiado: probar en la OLT real → parser → código → **actualizar el catálogo** en el mismo turno.  
6. Para SNMP: mecanismo → MIB/OID → índices → prueba mínima de solo lectura → analizar salida real → luego Java/Kotlin.  
7. No asumas que comandos/OIDs de MA5800 u otras familias aplican a MA5608T V800R015.  
8. Writes: respetar `olt.gateway.writes.enabled`; no activar en prod sin validación lab.  
9. Preferir estado local / sync sobre abrir SSH por cada request de UI.  
10. Diff mínimo; TDD del módulo (`src/test/.../oltgateway`).

---

## 11. Índice de docs y código

| Tema | Ruta |
|------|------|
| Arquitectura 3 capas + API | [olt-gateway-3layer.md](./olt-gateway-3layer.md) |
| MVP lectura (parcialmente stale en “writes”) | [olt-gateway-read-mvp.md](./olt-gateway-read-mvp.md) |
| Bus CLI | [olt-gateway-cli-bus.md](./olt-gateway-cli-bus.md) |
| Catálogo comandos | [olt-gateway-comandos-catalogo.md](./olt-gateway-comandos-catalogo.md) |
| Guía MA5608T | [olt-ma5608t-gpon-guide.md](./olt-ma5608t-gpon-guide.md) |
| Modelo DB | [olt-manager-db-model.md](./olt-manager-db-model.md) |
| Óptica / signal poll | [olt-gateway-optical-signal-parser.md](./olt-gateway-optical-signal-parser.md), [olt-gateway-signal-poll-coverage.md](./olt-gateway-signal-poll-coverage.md) |
| Alarmas | [olt-ma5608t-alarms-syslog.md](./olt-ma5608t-alarms-syslog.md), NetDiag |
| SNMP / traps (capacidades live) | [olt-ma5608t-snmp-capabilities.md](./olt-ma5608t-snmp-capabilities.md) |
| Backup ops | [olt-ma5608t-backup.md](./olt-ma5608t-backup.md) |
| Controllers | `.../oltgateway/controller/OltGatewayController.kt`, `SmartOltCompatController.kt` |
| Facade / writes / query | `OltManagerFacade`, `OltGatewayCommandService`, `OltGatewayQueryService` |
| SSH | `OltCliBus`, `HuaweiCliSession` |
| SNMP | `OltSnmpBusRegistry`, `OltSnmpModelLimits`, `Snmp4jOltSnmpClient`, `OltSnmpTrapReceiver` |
| Sync | `OltInventorySyncService`, `OltSignalPollService` |

---

## META (sin cambio)

Construir (y ahora **evolucionar**) una capa de gestión propia, rápida y confiable para la Huawei MA5608T para que las operaciones esenciales del ISP dejen de depender de SmartOLT.

**Hoy:** SSH + estado local + 6 ops.  
**Mañana:** SNMP/Traps donde la OLT real lo permita; SSH para configurar y para lo que SNMP no resuelva.
