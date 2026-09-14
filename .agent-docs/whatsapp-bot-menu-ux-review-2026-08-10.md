# Batería menú WhatsApp + hallazgos UX — 2026-08-10 (noche)

Cliente: `51982014925` (Saúl León Laguna)  
Canal: Safari WhatsApp Web + CRM  
Release prod: `1.0.3+f7f6fae`  
Contexto: fuera de horario laboral (Lima, ~21:20–21:45).

## Cobertura del menú

| Elemento | Resultado | Evidencia |
|----------|-----------|-----------|
| Menú principal (lista + descripciones) | PASS | 4 opciones con helper text; footer `MENU`/`ASESOR` |
| Consultar deuda | PASS | `[DEUDA]` saldo S/ 100.00, CCI/Yape, botones `Ya pagué` / `Menú principal` / `Hablar con asesor` |
| Ya pagué | PASS | `[YA_PAGUE]` pide voucher + after-hours |
| Menú principal (desde deuda/ya pagué) | PASS | Reenvía `[MAIN_MENU]` |
| Hablar con asesor (menú / deuda) | PASS | `[ASESOR]` **solicitud** + after-hours |
| Reportar avería | PASS | `[SUPPORT_MENU:SOLO_INTERNET]` Sin Internet / Internet lento |
| Internet lento → diag | PASS | `[SUPPORT_DIAG:SLOW_INTERNET]` luces LOS/PON |
| Verde / azul (cierre) | PASS | `[SUPPORT_CLOSED:…:SLOW_INTERNET:fiber_green]` **caso** |
| Registrar pago | PASS | `[COMPROBANTE]` foto/PDF + Yape/BCP |
| Botón diagnóstico viejo fuera de flujo | FAIL UX | Ver P1 abajo |

Registrar pago / soporte también cubiertos en batería diurna (`.agent-docs/whatsapp-bot-ux-battery-prod-2026-08-10.md`).

## Problemas que afectan UX (prioridad)

### P1 — Botones viejos de diagnóstico crean casos basura (`UNKNOWN`) — CORREGIDO

- **Qué pasó:** Con el bot ya en menú/asesor, se pulsó `🔴 Luz roja` de un mensaje anterior.
- **Resultado:** `[SUPPORT_CLOSED:ESPERANDO_ASESOR:UNKNOWN:fiber_red]` — cierra “caso” sin issue real.
- **Fix (2026-08-10):** FSM solo acepta `support_diag_*` en `SUPPORT_DIAG`; si no, reenvía menú. Ver `whatsapp-bot-menu-ux-fixes-2026-08-10.md`.

### P2 — Ortografía sin tildes — CORREGIDO (copy código + defaults)

### P3 — Inconsistencia de trato — CORREGIDO

### P4 — Copy deuda / “Ya pagué” — CORREGIDO (parcial: plan humanizado, plural, after-hours)

### P5 — Doble respuesta a voucher — CORREGIDO (código local)

- Dedup 3 min: si ya hay AUTO_REPLY `[VOUCHER]` reciente, no se reenvía el ACK.
- El segundo archivo sigue guardándose y el paso FSM se aplica.
- Ver `whatsapp-bot-voucher-ack-dedup-2026-08-10.md`.

### P6 — ACK `gracias` con menú pendiente — PENDIENTE (comportamiento FSM)

### P7 — CTA tras handoff — CORREGIDO (`Si necesita algo más, escriba MENU.`)

## Oportunidades de mejora (no bloqueantes)

1. **Descripciones del listado** ya ayudan (`Internet o TV…`, `Adjunte su voucher…`) — mantenerlas al añadir opciones.
2. **Coherencia solicitud vs caso** — OK en asesor vs soporte; no mezclar.
3. **Sin Internet / Internet lento** — flujo claro; falta probar rama TV/cable en combo (este número es `SOLO_INTERNET`).
4. **Horario en una sola oración** — bien; unificar tildes.
5. **Nombre del cliente** — usar tildes desde CRM (`Saúl León Laguna`).

## Notas de prueba

- WhatsApp Web perdió sesión a mitad (conflicto “Usar aquí” / QR); se recuperó y se cerraron las ramas pendientes.
- Selector de lista en WA Web: radio + `data-testid=list-msg-modal-button` (a veces fuera del nodo `dialog`).
- Borradores `MENU` en el composer pueden enviarse por error si la automatización no limpia el box.
