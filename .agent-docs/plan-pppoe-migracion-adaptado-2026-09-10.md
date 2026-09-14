# Plan PPPoE adaptado a la infraestructura real de Gigafiber

**Fecha:** 2026-09-10
**Origen:** `gigafiber/Plan_Implementacion_PPPoE_VLAN_GigaFiber_2026-09-09.docx`
**Estado:** diseño validado contra MK2 en vivo, BD `ispadmin` prod y código de los 4 WARs. Sin cambios de red aplicados.
**Objetivo del usuario:** que PPPoE sea el mecanismo de gestión de **todas** las suscripciones y dejar de depender de la IP estática.

Este documento **reemplaza** al `.docx` como fuente de trabajo. El `.docx` propone una arquitectura genérica; aquí se contrasta con lo que existe realmente y se corrigen las suposiciones que no aplican.

---

## 1. Qué acertó el documento y qué hay que corregir

| Tema del `.docx` | Realidad verificada 2026-09-10 | Veredicto |
|---|---|---|
| «PPPoE ya existe: 123 secrets, 51 sesiones» | Exacto. Server `PPOE CLIENTES` **activo** en bridge `LAN-VLAN1`, 123 secrets, 51 sesiones | ✅ correcto |
| «~966 Simple Queues» | 973 colas: 922 por IP + **51 dinámicas `<pppoe-*>`** | ✅ correcto |
| «27 ACTIVE sin cola, 4 mismatch, 96 huérfanas, 70 cancelados» | **Ya corregido** el 2026-09-10 (ver `mk2-queue-reconcile-2026-08-28.md`) | ⚠️ obsoleto |
| «Corte por deuda mal ordenado» | **Ya corregido**: `place-before` + listas `deudores`/`cancelados` separadas | ⚠️ obsoleto |
| «Crear VLAN 200 para gestión de ONU» | **Ya existe VLAN 1000** `vlan1000-olt` con `10.20.0.1/22` y pool `mgmt-1000` | ❌ no crear VLAN 200 |
| «Crear VLAN 110 para wireless» | Los wireless PPPoE están en el bridge `LAN-VLAN1` (VLAN 1 legado, en desuso) | ❌ replantear |
| «Crear VLAN 210 para infra» | Gestión OLT va por `ether3` `10.11.104.89/24`, fuera de VLAN | ❌ no aplica |
| «Reservar un /20 para gestión» | `10.20.0.0/22` = 1022 hosts. **Insuficiente** para 3000 ONUs | ⚠️ ampliar a /21 o /20 |
| «Un colector nuevo cada 5 s» | El WAR **Traffic ya lo hace**: block-read de `/queue/simple`, delta bytes, reset de contador, buckets y rollups | ⚠️ adaptar, no crear |
| «username `GF-{subscriptionId}`» | Los 123 secrets actuales son nombres libres heredados de MK1, sin relación con `subscription.id` | ✅ válido, pero exige mapeo previo |
| «MTU 1480 y MSS clamping» | Server con `max-mtu=1480` pero **cero reglas de MSS clamp** en MK2 | 🔴 hueco abierto hoy |

### Lo que el documento no vio (hallazgo bloqueante)

**El pool PPPoE y las IP estáticas comparten el mismo `/24`.**

| Evidencia | Valor |
|---|---|
| Pool `PPOE CLIENTES` | `192.168.26.2-192.168.26.254` |
| Suscripciones no canceladas con `ip` en `192.168.26.x` | **88** |
| Sesiones PPPoE activas cuya IP dinámica **coincide** con una `subscription.ip` de la BD | **44 de 51** |
| Colas simples con target `192.168.26.x` | 85 |
| Colas dinámicas `<pppoe-*>` | 51 |

Consecuencias reales, hoy, en producción:

1. **Doble shaping.** El abonado PPPoE recibe el `rate-limit` del perfil (cola dinámica) **y** una simple queue por IP creada por IspAdmin. Dos limitadores sobre el mismo tráfico.
2. **Colas que siguen a la IP, no al cliente.** Si el abonado reconecta y el pool le entrega otra dirección, su cola por IP queda apuntando a un tercero. Esto explica una parte de las colas huérfanas y de los 5 `CONFLICT` (dos suscripciones con la misma IP) detectados en el reconciliador.
3. **`IpAllocationService` puede entregar a un cliente nuevo una IP que el pool PPPoE va a repartir** en la siguiente reconexión.

Mientras esto no se separe, cualquier migración masiva a PPPoE amplifica el problema. **Es la fase 1 real.**

---

## 2. Inventario verificado (fuente de verdad de este plan)

### 2.1 MK2 — CCR2116 `38.224.231.4`, RouterOS 7.23.2

CPU 11 %, 15 GB de 16 GB libres, uptime 3 semanas. No hay límite de hardware.

**VLANs existentes (4, no hay más):**

| ID | Nombre | Sobre | Uso |
|---|---|---|---|
| 450 | `WAN-VLAN SFP-SFPPLUS1` | `sfp-sfpplus1` | Uplink Internet, `/27` público + 4 `/32` |
| 1 | `vlan1-olt` | `sfp-sfpplus3` | Legado, en desuso hacia VLAN 100 |
| 100 | `vlan100-olt` | `sfp-sfpplus2` | Abonados GPON |
| 1000 | `vlan1000-olt` | `sfp-sfpplus2` | Gestión CPE / TR-069 |

**Direccionamiento:**

| Interfaz | Redes |
|---|---|
| `LAN-VLAN1` | **29** redes `/24`: `192.168.{0,1,9,20,22,25,26,33,44,49,50,55,88,93,95,99,100,123,168,175,200,201,210,211,212,213,220,221}.1` + `192.169.22.1` |
| `vlan100-olt` | `192.168.30.1/24`, `192.168.31.1/24`, `192.168.250.1/24` (e2e), `192.168.255.1/22` (provisioning TR-069) |
| `vlan1000-olt` | `10.20.0.1/22` (mgmt CPE), `10.20.250.1/24` (e2e) |
| `ether3` | `10.11.104.89/24` (gestión OLT) |
| `wg-ispadmin-vps` | `10.255.255.1/30` |

**Espacio RFC1918 libre:** `192.168.0.0/16` está prácticamente agotado. `10.0.0.0/8` está casi virgen: solo `10.11.104.0/24`, `10.20.0.0/22`, `10.20.250.0/24` y `10.255.255.0/30`.

**PPPoE actual:**

| Ítem | Valor |
|---|---|
| Servidor | 1 solo: `PPOE CLIENTES` sobre `LAN-VLAN1`, `max-mtu=1480`, `one-session-per-host=yes`, `default-profile=default` |
| Pools | `PPOE CLIENTES` `192.168.26.2-254` (253 IP), `PPOE GAMERS` `192.168.55.2-254` (sin uso) |
| Secrets | 123 |
| Sesiones activas | 51 |
| MSS clamp | **ninguno** |

**Perfiles PPPoE (nombres comerciales heredados de MK1):**

| Perfil | rate-limit | Secrets |
|---|---|---|
| `PLAN 50 SOLES` | 200M/200M | 82 |
| `PLAN 70` | 300M/300M | 26 |
| `PLAN 70 NEW` | 400M/400M | 8 |
| `PLAN 100` | 400M/400M | 2 |
| `PLAN 100 NW` | 400M/400M | 2 |
| `PLAN 150 SOLES NEW` | 600M/600M | 3 |
| `PLAN 150 SOLES` | 600M/600M | 0 |
| `CORTE DE SERVICIO` | 1k/1k | 0 |

Todos con `local-address=192.168.26.1`, `remote-address=PPOE CLIENTES`, `only-one=yes`.

### 2.2 Base de datos `ispadmin`

| Tipo | ACTIVE | CANCELLED |
|---|---|---|
| FIBER | 707 | 260 |
| ONLY_TV_FIBER | 32 | 59 |
| WIRELESS | 163 | 124 |
| **Total activo** | **902** | 443 |

Catálogo `plan`: 29 filas, **11 activas**. Las activas de FIBER son 200/200, 300/300, 400/400, 500/500 y `cable_basico`; WIRELESS activas 20/20, 30/30, 15/30, 20/45.

`Subscription` **no tiene** ningún campo PPP: la identidad de red es `ip` + `ipPool` + `hostDevice`. Última migración Flyway del Core: **V51**.

### 2.3 Código

| Capacidad | Estado |
|---|---|
| Cliente RouterOS | Solo REST 7 (`RouterOs7RestAdapter`). Soporta paths arbitrarios: `/ppp/secret`, `/ppp/profile`, `/ppp/active` funcionarían hoy sin tocar el transporte |
| `MikrotikSession` | `print`, `call`, `add`, `set`, `remove`. **No hay `move`** (el corte lo resuelve con `POST <path>/move`) |
| Soporte PPP en código | **Cero.** Ni un literal `/ppp/*` en `src/main/kotlin` |
| Shaping | `QueueManagerService` + `SimpleQueueProvisioner`, siempre `target = subscription.ip` |
| Corte | `ServiceCutManagerService` + address-lists `deudores` / `cancelados` por IP |
| Telemetría | WAR Traffic: `SubscriptionTrafficPollService` hace block-read `/queue/simple` con `.proplist [.id,target,name,bytes,rate,packets,max-limit]`, calcula delta, detecta reset por uptime y por contador descendente, escribe buckets y rollups. Indexado **por IP**. Poll 60 s; WebSocket live ~5 s |
| TR-069 | Todo `WANIPConnection` + `AddressingType=Static`. `WANPPPConnection` no aparece en código de producción. El criterio de `COMPLETE` compara `ExternalIPAddress` contra `subscription.ip` |
| Perfiles ONU | Tabla `tr069_model_profile` en schema ACS, resueltos por `productClass`. Modelos con cobertura: ZTE F6600R, Huawei HG8145X6, VSOL V2804AX15T |

---

## 3. Decisiones de arquitectura adaptadas

| # | Decisión | Justificación |
|---|---|---|
| D1 | **No crear VLAN 200/210.** La gestión de ONU se queda en **VLAN 1000** | Ya existe, ya tiene DHCP, NAT y pool. Crear otra sería duplicar |
| D2 | **Ampliar el direccionamiento de gestión** de `10.20.0.0/22` a `10.20.0.0/20` | 1022 hosts no alcanzan para 3000 ONUs. Ampliar la máscara no rompe leases existentes |
| D3 | **Nuevo pool PPPoE en espacio 10.x**, `10.32.0.0/16`, subdividido por POP/OLT | `192.168.26.0/24` colisiona con IP estáticas y solo da 253 direcciones |
| D4 | **Segundo servidor PPPoE sobre `vlan100-olt`** para GPON | El único servidor está en `LAN-VLAN1` (wireless). GPON llega por VLAN 100 |
| D5 | **Wireless se queda donde está** durante toda la migración GPON | Mover el L2 wireless y migrar GPON a la vez multiplica el riesgo. VLAN 110 se decide después |
| D6 | **Username `GF-{subscription.id}`** para altas nuevas; los 123 secrets heredados se **mapean**, no se renombran | Renombrar un secret activo corta la sesión |
| D7 | **Perfiles PPPoE por velocidad, no por nombre comercial**: `GF_200_200`, `GF_300_300`, `GF_400_400`, `GF_500_500`, `GF_600_600`, `GF_CORTE` | Hay 6 perfiles para 4 velocidades distintas y nombres que ya no corresponden al catálogo. El mapeo debe ser `plan.download/upload → perfil` |
| D8 | **Corte PPPoE = cambio a perfil `GF_CORTE` + `/ppp/active/remove`**, no `disabled` | Permite portal de pago y reconexión inmediata al reactivar. `disabled` deja al cliente sin diagnóstico |
| D9 | **El colector de tráfico no se reescribe**: se cambia la clave de `IP` a `pppoe-username` | Las colas dinámicas `<pppoe-*>` ya exponen `bytes` en el mismo block-read |
| D10 | **`subscription.ip` se conserva** como campo histórico/wireless; la identidad pasa a `pppoe_username` | Evita romper 900 suscripciones y todo el subsistema de tráfico de golpe |
| D11 | **MSS clamping antes de cualquier abonado nuevo en PPPoE** | 51 sesiones corren hoy sin clamp con MTU 1480 |

### Decisiones que siguen abiertas

- Bloque definitivo del pool PPPoE (`10.32.0.0/16` es propuesta, falta contrastar con rutas del VPS y de la OLT).
- Si los wireless se migran a VLAN 110 o se quedan en `LAN-VLAN1` indefinidamente.
- Retención exacta de muestras a 5 s (hoy el bucket es de minutos).
- Qué modelos de ONU aceptan cambio de WAN a PPPoE por TR-069 sin visita.
- Portal de pago sobre el perfil `GF_CORTE`.

---

## 4. Fases adaptadas

Las fases del `.docx` se renumeran porque su Fase 0 y Fase 1 ya están hechas.

### Fase A — Desacoplar el pool PPPoE de las IP estáticas 🔴 bloqueante

Sin esto no se migra nada. No requiere código nuevo.

1. Crear pool `GF-PPPOE-1` en `10.32.0.0/20` y gateway `10.32.0.1/20` en `LAN-VLAN1`.
2. Añadir `masquerade` para `10.32.0.0/20`.
3. Apuntar los 8 perfiles PPPoE a `remote-address=GF-PPPOE-1` y `local-address=10.32.0.1`.
4. Reconectar en ventana los 51 activos (`/ppp/active/remove`) para que tomen dirección del pool nuevo.
5. Borrar las **85 colas por IP en `192.168.26.x`** que pertenezcan a abonados PPPoE (quedan solo las dinámicas del perfil).
6. Marcar en BD esas 88 suscripciones como `accessType=PPPOE` y **excluirlas del reconciliador de colas** y de `IpAllocationService`.
7. Añadir la regla de MSS clamp (`chain=forward action=change-mss new-mss=clamp-to-pmtu tcp-flags=syn`).

**Verificación:** cero sesiones PPPoE con dirección en `192.168.26.0/24`; cero colas por IP para abonados PPPoE; el reconciliador reporta `pppoeIgnored=51` y `0 CONFLICT` nuevos.

### Fase B — Modelo de datos

Migración Flyway **V52** en el schema del Core:

```
subscription.access_type        ENUM('STATIC_IP','PPPOE')  NOT NULL DEFAULT 'STATIC_IP'
subscription.pppoe_username     VARCHAR(64)  NULL UNIQUE
subscription.pppoe_password_enc VARCHAR(255) NULL
subscription.pppoe_profile      VARCHAR(64)  NULL
subscription.pppoe_migration_status ENUM(...) NULL
subscription.onu_mgmt_ip        VARCHAR(45)  NULL
subscription.onu_mgmt_mac       VARCHAR(17)  NULL
```

La contraseña se cifra con el mismo mecanismo que `wifi_password_24_enc`. Tabla `subscription_pppoe_event` para auditoría (quién, cuándo, qué, resultado).

Estados de migración: `NOT_STARTED`, `PRECHECK`, `CREDENTIAL_CREATED`, `ACS_CONFIGURING`, `VERIFYING`, `MIGRATED`, `ROLLING_BACK`, `ROLLED_BACK`, `FAILED`.

### Fase C — Cliente PPPoE en el Core (TDD)

Nuevo `PppoeManagerService` en `wispadmin/service/mikrotik/`, en paralelo a `QueueManagerService`, sobre el `MikrotikSession` que ya existe:

| Operación | Path REST |
|---|---|
| Listar secrets en bloque | `POST /rest/ppp/secret/print` con `.proplist` |
| Alta / baja de secret | `PUT` / `DELETE /rest/ppp/secret` |
| Cambio de plan | `PATCH /rest/ppp/secret/{id}` → `profile` |
| Sesiones activas en bloque | `POST /rest/ppp/active/print` |
| Desconectar sesión | `DELETE /rest/ppp/active/{id}` |
| Perfiles | `/rest/ppp/profile` |

Todo idempotente: leer antes de escribir, nunca duplicar por nombre. Contraseñas jamás en log (el `GenieAcsCurlLogger` ya tiene precedente de SPV «privado»).

Tests unitarios con sesión falsa, siguiendo `MikroTikServiceRestTest`.

### Fase D — Corte y reactivación por tipo de acceso

`ServiceCutManagerService` pasa a delegar en una estrategia:

| `accessType` | Cortar | Reactivar |
|---|---|---|
| `STATIC_IP` | address-list `deudores`/`cancelados` (ya implementado) | `removeIpFromAllCutLists` (ya implementado) |
| `PPPOE` | `profile = GF_CORTE` + `/ppp/active/remove` | `profile = <perfil del plan>` |

`CutLists` y las dos reglas drop se conservan tal cual para los estáticos.

### Fase E — Telemetría PPPoE

Cambios acotados en el WAR Traffic:

1. `TrafficDirectoryTarget` gana `pppoeUsername`; la clave del índice pasa a ser `ip` **o** `pppoeUsername`.
2. `RouterOsTrafficCounterParser.normalizeTarget` reconoce `<pppoe-USER>` y devuelve el username.
3. `TrafficCounterState` se rekeya por `subscription_id` en vez de por `client_ip` (hoy la PK es la IP, que en PPPoE cambia en cada reconexión).
4. La reconexión PPPoE resetea el contador de la cola dinámica: se reutiliza la detección de reset que ya existe (contador descendente), sin generar picos falsos.

No hay una llamada por cliente: el `print` de `/queue/simple` ya trae todas las colas dinámicas en una sola petición.

### Fase F — Adaptador TR-069 PPPoE (laboratorio primero)

1. Extender `Tr069ModelProfile` con `wanPppConnectionPath` y `buildClientPppoeWanParameterValues` (`WANPPPConnection.{n}.Username/Password/ConnectionType=IP_Routed_PPPoE`).
2. `addObject` sobre `WANPPPConnection` en vez de `WANIPConnection`, por fabricante.
3. Cambiar el criterio de `COMPLETE`: en vez de `ExternalIPAddress == subscription.ip`, verificar `ConnectionStatus=Connected` **y** que la sesión aparezca en `/ppp/active` con el username esperado.
4. Alinear el preset `default` de GenieACS, que hoy refresca `WANIPConnection.*.ExternalIPAddress`.
5. La WAN de gestión (VLAN 1000) **no se toca**: el corte PPPoE no debe dejar la ONU inalcanzable.

Empezar con `ZTEGDC47BFFD` (ONU canónica de laboratorio, tag `lab`), según el runbook `pruebas-local-gateway-acs-lab.md`.

### Fase G — Orquestador de migración

State machine idempotente en el Core, con reintentos y rollback:

```
PRECHECK → CREDENTIAL_CREATED → ACS_CONFIGURING → VERIFYING → MIGRATED
                                        │
                                        └─ fallo → ROLLING_BACK → ROLLED_BACK
```

Rollback = restaurar el `WANIPConnection` estático anterior (snapshot guardado en `PRECHECK`) y borrar el secret. Ante un fallo, la oleada se detiene.

### Fase H — Piloto y oleadas

1. ONU de laboratorio.
2. 5 abonados FIBER controlados.
3. 20–50 abonados.
4. **Altas nuevas en PPPoE por defecto.**
5. Oleadas de clientes existentes, midiendo CPU del CCR2116, reconexiones, latencia y tickets de soporte.

Prerrequisito heredado del informe de auditoría: reconciliar las 235 ONU desfasadas OLT/SmartOLT antes de nuevas oleadas sobre VLAN 100.

---

## 5. Riesgos principales

| Riesgo | Mitigación |
|---|---|
| Colisión pool PPPoE ↔ IP estática (**activa hoy**) | Fase A antes que nada |
| Sin MSS clamp con MTU 1480 | Regla en Fase A |
| Un solo servidor PPPoE, en el bridge legado VLAN 1 | Servidor separado en `vlan100-olt` (D4) |
| `only-one=yes` + reconexiones | Ya está bien configurado; vigilar en el piloto |
| Modelos de ONU sin soporte PPPoE por TR-069 | Descubrimiento por modelo en Fase F; los que no, quedan como migración manual |
| Cliente RouterOS sin reintentos | La Fase C debe ser idempotente por diseño |
| `restoreInternetConnection` no toca el router hoy | Deuda previa, corregir al pasar por Fase D |
| Doble fuente de shaping durante la transición | `access_type` decide quién crea cola y quién no |

## 6. Criterios de aceptación del piloto

1. La ONU sigue siendo alcanzable por VLAN 1000 con la sesión PPPoE caída o cortada.
2. `subscription.id ↔ pppoe_username ↔ serial ONU` sin ambigüedad.
3. Cambio de plan = cambio de perfil, verificable en `/ppp/active`.
4. Corte y reactivación sin depender de address-lists por IP.
5. El colector obtiene RX/TX de todas las sesiones activas en **una** llamada cada ciclo.
6. Una reconexión no produce delta negativo ni pico falso.
7. Rollback devuelve al abonado a IP estática con su cola.
8. CPU del CCR2116 dentro de umbral durante el piloto.

## 7. Siguiente paso concreto

**Fase A**, en ventana, sobre MK2. Es la única que no necesita código y desbloquea todo lo demás. Antes de ejecutarla hay que decidir el bloque definitivo del pool (D3) y confirmar que `10.32.0.0/16` no colisiona con rutas del VPS ni de la OLT.

## Relacionado

- [auditoria-red-olt-mk2-2026-09-09.md](./auditoria-red-olt-mk2-2026-09-09.md)
- [mk2-queue-reconcile-2026-08-28.md](./mk2-queue-reconcile-2026-08-28.md)
- [cutover-pppoe-wireless-mk2.md](./cutover-pppoe-wireless-mk2.md)
- [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md)
- [vlan1-desuso-destino-vlan100.md](./vlan1-desuso-destino-vlan100.md)
- [mikrotik-mk2-comandos-catalogo.md](./mikrotik-mk2-comandos-catalogo.md)
- [tr069-e2e-validacion-modelo.md](./tr069-e2e-validacion-modelo.md)
