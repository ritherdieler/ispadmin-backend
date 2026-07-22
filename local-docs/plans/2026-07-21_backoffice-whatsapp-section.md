# Plan implementado: Sección WhatsApp en el backoffice

| Campo | Valor |
|-------|-------|
| **Fecha** | 2026-07-21 |
| **Plan origen** | `backoffice_whatsapp_section_a68d5694.plan.md` |
| **Rama backend** | `feature/backoffice-whatsapp-section` |
| **Rama frontend** | `feature/backoffice-whatsapp-section` |
| **Repos** | `wispadministrator-main` + `ispadmin-backoffice-main` |

---

## Alcance implementado

- Sección WhatsApp en backoffice para **ADMIN** y **SECRETARY** (no ACCOUNTANT).
- Listado de clientes con factura pendiente y teléfono válido (selección con checkboxes).
- Envío individual y por lote a clientes seleccionados.
- Historial de logs de mensajes.
- Panel opcional colapsable para envío manual por plantilla (teléfono + datos).

---

## Backend — endpoints nuevos

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/ispadmin/whatsapp/reminder-candidates` | Candidatos con factura pendiente y teléfono |
| `POST` | `/ispadmin/whatsapp/reminders/selected` | Envío a `paymentIds` seleccionados (máx. 50) |
| `POST` | `/ispadmin/whatsapp/messages/template` | Envío manual por plantilla |

### Endpoints reutilizados

| Método | Ruta | Uso |
|--------|------|-----|
| `GET` | `/ispadmin/whatsapp/logs` | Historial (últimos 50) |
| `GET` | `/ispadmin/whatsapp/logs/payment/{paymentId}` | Logs por factura |

---

## Backend — DTOs y servicios

### DTOs nuevos

- `WhatsAppReminderCandidateDto` — candidato para UI (incluye `alreadySentToday`)
- `WhatsAppSelectedRemindersRequestDto` — body `{ paymentIds: [...] }`
- `WhatsAppSendTemplateRequestDto` — envío manual plantilla
- `WhatsAppSendResponseDto` — respuesta formal de envío plantilla

### Servicio

`PaymentWhatsAppNotificationService`:

- `getReminderCandidates(limit = 200)` — lista candidatos
- `sendPaymentRemindersToSelected(paymentIds)` — lote manual con dedup diario

### Repository

- `findAllPendingPaymentsWithValidPhone(limit)` — pendientes con teléfono peruano válido (sin filtrar “ya enviado hoy”; el flag se calcula en servicio)

---

## Frontend — cambios

| Archivo | Cambio |
|---------|--------|
| `src/pages/WhatsAppMessages.tsx` | Página principal: tabla, selección, envío, historial, manual |
| `src/services/whatsAppService.ts` | Cliente API WhatsApp |
| `src/services/config.ts` | Endpoint `whatsapp` |
| `src/hooks/useAuth.ts` | `hasAccess('whatsapp')` → ADMIN \| SECRETARY |
| `src/components/ui/ProtectedRoute.tsx` | Recurso `whatsapp` |
| `src/components/layout/Sidebar.tsx` | Ítem menú en Gestión Financiera |
| `src/App.tsx` | Ruta `/whatsapp` lazy |

---

## Permisos

| Rol | Acceso |
|-----|--------|
| ADMIN | Sí |
| SECRETARY | Sí |
| ACCOUNTANT | No |
| Otros | No |

---

## Configuración WhatsApp (referencia)

- Plantilla: `payment_reminder_gigaperu` / idioma `es_PE`
- Producción: **GigaFiberPeru-Mensajes**, Phone Number ID `1187318341136661`
- Token y secretos: `application-local.properties` (dev) / variables `WHATSAPP_*` (prod)
- Webhook verify token: `gigafiber_whatsapp_verify_2026`

---

## Cómo probar

1. Backend con perfil local y token WhatsApp configurado.
2. Frontend apuntando a `http://localhost:8080/ispadmin`.
3. Login **SECRETARY** o **ADMIN** → menú **Gestión Financiera → WhatsApp**.
4. Verificar carga de candidatos en tabla.
5. Seleccionar 1+ clientes → **Enviar a seleccionados** → revisar panel de resultados y logs.
6. Cliente con badge “Ya enviado hoy” → checkbox deshabilitado; reintento vía API → `SKIPPED`.
7. Login **ACCOUNTANT** → no debe ver menú ni acceder a `/whatsapp`.

### Verificación automática ejecutada

- Backend: `mvnw compile test` (tests WhatsApp webhook) → OK
- Frontend: `npm run build` → OK

---

## Pendientes post-implementación

- [ ] Commit y PR en ambas ramas (cuando el usuario lo solicite)
- [ ] Deploy backend a prod con `SecurityConfig.kt` y variables `WHATSAPP_*`
- [ ] Configurar webhook Meta en prod: `https://<dominio>/ispadmin/whatsapp/webhook`
- [ ] Confirmar entrega real de mensajes en prod (método de pago Meta activo)

---

## Archivos backend principales

```
src/main/kotlin/.../controller/WhatsAppController.kt
src/main/kotlin/.../service/PaymentWhatsAppNotificationService.kt
src/main/kotlin/.../repository/PaymentRepository.kt
src/main/kotlin/.../dto/WhatsAppReminderCandidateDto.kt
src/main/kotlin/.../dto/WhatsAppSendResponseDto.kt
src/main/kotlin/.../requestbody/WhatsAppSelectedRemindersRequestDto.kt
src/main/kotlin/.../requestbody/WhatsAppSendTemplateRequestDto.kt
src/test/kotlin/.../controller/WhatsAppWebhookSecurityTest.kt
```
