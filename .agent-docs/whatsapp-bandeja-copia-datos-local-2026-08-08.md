# Copia local de datos WhatsApp/CRM desde prod (bandeja)

Fecha: 2026-08-08

## Qué se copió

Desde VPS `ispadmin` (túnel `./scripts/db-tunnel.sh`) → local `ispadmin_dev`:

| Tabla | Filas |
|-------|------:|
| whatsapp_inbound_message | 518 |
| whatsapp_message_log | 1733 |
| whatsapp_synced_template | 10 |
| whatsapp_phone_session | 133 |
| whatsapp_chat_state | (incluido) |
| crm_conversation | 133 |
| crm_assignment_event | (incluido) |
| crm_internal_note | (incluido) |
| subscription | 1319 |
| user | 18 |

Dump: `backups/whatsapp-bandeja-from-prod-YYYYMMDD-HHMMSS.sql` (no versionar; suele estar en backups/).

## Cómo repetir

```bash
./scripts/db-tunnel.sh start
export PATH="/usr/local/mysql/bin:$PATH"

# Dump remoto (password MySQL del VPS en application-prod / .env del VPS)
MYSQL_PWD='…' mysqldump -h 127.0.0.1 -P 13306 -u root \
  --single-transaction --quick --set-gtid-purged=OFF --column-statistics=0 \
  ispadmin whatsapp_inbound_message whatsapp_message_log whatsapp_synced_template \
  whatsapp_phone_session whatsapp_chat_state crm_conversation crm_assignment_event \
  crm_internal_note subscription user \
  > backups/whatsapp-bandeja-from-prod-$(date +%Y%m%d-%H%M%S).sql

# Import local (password de application-local.properties)
MYSQL_PWD='…' mysql -h 127.0.0.1 -P 3306 -u root ispadmin_dev -e "
SET FOREIGN_KEY_CHECKS=0; SET UNIQUE_CHECKS=0;
SOURCE $(ls -t backups/whatsapp-bandeja-from-prod-*.sql | head -1);
SET UNIQUE_CHECKS=1; SET FOREIGN_KEY_CHECKS=1;
"
```

## Probar bandeja

1. Backend local con profile `dev`/`local` contra `ispadmin_dev`.
2. Backoffice apuntando a `localhost:8080`.
3. Abrir WhatsApp hub → Conversaciones; chips y `GET /whatsapp/conversations?view=…` / `view-counts`.
4. Scroll en **Todas**: el listado pagina con `cursor` / `hasMore` / `nextCursor` (50 por página).

## Arrancar stack local (2026-08-08)

MySQL en `localhost:3306`, BD `ispadmin_dev` (credenciales en `application-local.properties`). El túnel prod (`13306`) no hace falta si ya importaste el dump.

```bash
# Terminal 1 — backend (context-path /ispadmin, puerto 8080)
cd ispadmin-backend && ./run-dev.sh

# Terminal 2 — backoffice (puerto 3000)
cd ispadmin-backoffice && npm run dev
```

Backoffice: crear `ispadmin-backoffice/.env.development.local` con `VITE_API_BASE_URL=http://localhost:8080/ispadmin` para no pisar el `.env` que apunta a prod.

Comprobaciones rápidas:

- `curl -s http://127.0.0.1:8080/ispadmin/actuator/health` → `{"status":"UP"}`
- UI: http://localhost:3000 (login con usuario de la tabla `user` importada)
