# Auditoría historial WhatsApp (última semana) — 2026-08-10

Fuente: CRM API (`/whatsapp/conversations` + `/thread`), sesión `dscorp`.  
Ventana: mensajes desde **2026-08-03** (muestra de ~1500 conversaciones listadas; deep-scan ~60 hilos con actividad de bot).

## Resumen ejecutivo

| Severidad | Hallazgo | Evidencia |
|-----------|----------|-----------|
| Alta | Doble ACK de voucher en <90s | `51982014925` (45s), `51913075891` Eugenia (58s) |
| Alta | Caso soporte con issue `UNKNOWN` | Solo `51982014925` 21:33 (batería UX / botón viejo) — **ya corregido en código local, no desplegado** |
| Media | Copy viejo (tú / “Fuera de horario laboral:” / “figura”) convivió con copy nuevo el mismo día | Varios clientes hasta ~20:20; humanización ~20:50+ |
| Media | `ASESOR` decía “caso” en vez de “solicitud” (pre-coherencia) | Varios hilos 10 ago tarde |
| Baja | `p.m..` (punto duplicado) | `businessHoursSentence()` + horario que ya termina en `p.m.` |
| Baja | API `dateFrom` con fecha-hora → HTTP 500 | `dateFrom=2026-08-03T00:00:00` falla; `2026-08-03` OK |
| Info | Plantillas operador aún usan `Estimado(a)` | No es auto-reply del bot |
| Info | ~49 conversaciones con `hasPendingReceipt` | Cola de comprobantes por revisar |

## Bugs / inconsistencias detalladas

### 1. Doble respuesta a comprobante
- Cliente envía 2 medias casi seguidas → 2× `Recibimos su comprobante…`.
- Confirmado en prueba y en cliente real `51913075891`.
- **Pendiente de fix** (idempotencia por media/wamid o debounce).

### 2. Diagnóstico stale → `UNKNOWN`
- Marker: `[SUPPORT_CLOSED:ESPERANDO_ASESOR:UNKNOWN:fiber_red]`.
- Solo visto en el número de prueba de la batería UX.
- Fix local: FSM ignora `support_diag_*` fuera de `SUPPORT_DIAG`.

### 3. Inconsistencia de releases en el mismo día (10 ago)
Línea de tiempo aproximada en prod:
- Hasta ~19:00–20:20: saludo **tú** (`Te atiende…`), asesor **“Tu caso / Su caso fue registrado”**, after-hours **“Fuera de horario laboral: será atendido…”**.
- Desde ~20:50: copy humanizado **usted**, `solicitud`/`caso` según tipo, after-hours en oración.
- Markers distintos en historial: `[ASESOR]`, `[ASESOR:FALLBACK]`, `[ASESOR:RULES]` → rutas/versiones distintas.

### 4. Copy de deuda / Ya pagué (pre-fix local)
En historial de la semana (aún en prod hasta que se despliegue el fix de esta noche):
- `Estimado(a)`, `basico_wireless 50`, `pendiente(s)`, `Hola {nombre}`, `figura(n)` (versiones viejas).

### 5. Punto doble en horario
Texto típico: `… a 12:30 p.m..`  
Causa: frase `Nuestro horario es de {secretaryHours}.` cuando `secretaryHours` ya incluye `p.m.`

### 6. Filtro de conversaciones por datetime
`GET /whatsapp/conversations?dateFrom=2026-08-03T00:00:00` → 500.  
`dateFrom=2026-08-03` funciona. Probable parse estricto en `resolveDateRange`.

## Qué ya está abordado en código local (sin deploy)
- P1 UNKNOWN / botones diag stale  
- Tildes, trato usted, plan humanizado, plural, CTA `MENU`  
Ver `whatsapp-bot-menu-ux-fixes-2026-08-10.md`.

## Recomendaciones siguientes
1. Desplegar fixes locales (UX P1–P4/P7 + voucher dedup P5).  
2. Revisar overrides en VPS `.env` de `after-hours` / `secretary-hours` (evitar copy viejo).  
3. Endurecer parse de `dateFrom` (aceptar date-only y date-time, o devolver 400 claro).  
4. Evitar `p.m..` (no añadir `.` final si el horario ya termina en punto).
