# Handoff — OLT Gateway único (compartido)

**Fecha handoff:** 2026-09-21.  
**De:** agente migración VPS KVM4.  
**Para:** agente nuevo (solo gateway único).  
**Pedido Sergio:** la migración dual-run hasta ensayo D-1 **es suficiente**; cutover `.cloud` **no** es este ticket. Delegar el cambio del gateway único.

---

## Contexto (no reabrir)

Migración VPS → KVM4 (`2.24.66.53`) en dual-run con ensayo D-1 **hecho**. Prod público sigue en `212.85.13.47`. WG segundo peer up (`10.255.254.0/30`). Cutover DNS `.cloud` **bloqueado** hasta OK explícito de Sergio — **fuera de scope** de este handoff.

Docs migración:

- Store: `AgentStores/.../bc-67885de4.../files/docs/migracion-vps-kvm4.md`
- Backend: `ispadmin-backend/.agent-docs/migracion-kvm4-ensayo-d1-2026-09-20.md`
- Backend: `ispadmin-backend/.agent-docs/migracion-kvm4-wg-segundo-peer-2026-09-20.md`

---

## Objetivo de este ticket

Un solo proceso `olt-gateway` es el **único** dueño SSH/SNMP hacia la MA5608T `10.11.104.2`. Prod Core, staging Core y prestaging Mac le hablan **solo por HTTP**. Motivo: la OLT tiene ~3 VTY; dos `OltCliBus` = lockout.

**Diseño canónico (leer entero antes de editar):**

`AgentStores/.../bc-67885de4.../files/docs/olt-gateway-compartido.md`

Auditoría: `.../internal/olt-gateway-deploy-ssh-audit.md`  
Encaje caja: `.../docs/infra-kvm4-docker-traefik.md` §2.5 / §6

---

## Destino (decisiones no reabrir)

| | |
|---|---|
| Procesos Gateway | **Uno** (`olt-gateway`), siempre up |
| Context | `/ispadmin-oltgateway` |
| Schema | **Uno:** `prod_oltgateway` (`stg_oltgateway` fósil) |
| Pool SSH | 2 (`INTERACTIVE` + `BACKGROUND`) |
| Público | **No** exponer Gateway (ni Traefik ni nginx público) |
| Clientes externos | Solo Core (Android / backoffice) |

---

## Orden de fases (obligatorio)

| # | Qué | Demostrar primero |
|---|---|---|
| **0** | Contrato: 2ª API key, guard SN lab, ACS por llamante, dual XADD óptica. `olt.gateway.enabled` se eliminó; no es parte del contrato | Tests Gradle RED→GREEN. **Bloqueante** |
| **1** | Stopgap VTY: staging/prestaging sin `OltCliBus`; URL al Gateway vivo (puede ser el embebido de prod) | Un solo TCP a `10.11.104.2:22` con staging up |
| **2** | WAR sibling `ispadmin-oltgateway` + imagen slim | Health `oltReachable=true` |
| **3** | Cutover SSH: Core prod como cliente HTTP; levantar contenedor. No usar `OLT_GATEWAY_ENABLED` | Authorize lab `ZTEGDC47BFFD`, SNMP, 360 |
| **4** | Staging solo HTTP + key lab | Alta lab staging sin 2º SSH |
| **5** | Prestaging Mac: túnel `:8092`, sin SSH OLT en la Mac | Runbook lab |
| **6** | Encaje KVM4 compose (después; migración caja ya dual-run) | Sin publicar Gateway |

Camino corto: unit/smoke local **antes** de `deploy.sh` staging/prod. Ver `ispadmin-backend/AGENTS.md` y `pruebas-camino-mas-corto.md`.

---

## Prohibido

- Abrir SSH a la OLT en paralelo al Gateway (`free-olt-ssh` primero; un solo SSH).
- Publicar `/ispadmin-oltgateway` en internet.
- Cutover DNS `.cloud` o apagar `212.85.13.47` (otro ticket + OK Sergio).
- Deploy prestaging al VPS; backoffice staging al VPS.
- JDBC cruzado Core↔Gateway.
- Empezar por fase 2/3 sin fase 0 verde (staging podría authorize flota sin guard lab).

---

## Verificación mínima (§10 del diseño)

1. Key staging + SN no-lab → 403; SN lab → OK.  
2. Router ACS: env `stg` → URL staging; sin header → 400.  
3. Un `publishOpticalBatch` → XADD `prod:` **y** `stg:`.  
4. `olt.gateway.enabled` se eliminó. `OltCliBus` arranca si `olt.gateway.mock.enabled=false`. Para no crear el bus, el proceso no debe escanear el paquete (`gigafiber.subsystems.oltgateway.enabled=false`).  
5. Prod + staging (+ prestaging) up → **un** TCP a `10.11.104.2:22`.  
6. Health Gateway → `oltReachable=true`.

ONU lab canónica: `ZTEGDC47BFFD` (tag GenieACS `lab`).

---

## Prompt listo para pegar al agente nuevo

```text
Implementa el OLT Gateway compartido (un solo dueño SSH/SNMP a 10.11.104.2).

Leer primero (store migración + diseño):
- docs/olt-gateway-compartido.md (fuente de verdad)
- internal/olt-gateway-deploy-ssh-audit.md
- ispadmin-backend/AGENTS.md + pruebas-camino-mas-corto.md + pruebas-local-gateway-acs-lab.md

Contexto: migración VPS→KVM4 dual-run + ensayo D-1 ya hechos. NO hacer cutover .cloud ni tocar DNS prod.

Empezar por Fase 0 (TDD): segunda key staging, guard SN lab, ACS por llamante, dual XADD óptica. `olt.gateway.enabled` ya no existe. Luego Fase 1 stopgap VTY.

Prohibido: publicar el Gateway, segundo SSH a la OLT, deploy prestaging al VPS, JDBC cruzado.

Responder en español. Código/comentarios en inglés. Diff mínimo.
```

---

## Estado al cerrar este handoff

- Migración dual-run / ensayo: **cerrada como suficiente** (sin cutover).
- Gateway único: **pendiente**, delegado a agente nuevo con este doc.
