# Deploy prod — NetDiag perf + GPON LLM — 2026-08-01

## Git

| Item | Valor |
|------|--------|
| Rama integrada | `cursor/cloud-agent-1785608325796-hn7rh` → `develop` (merge `1f7f7d3`) |
| Commit cursor base | `19d8061` (feat netdiag perf + GPON LLM) |
| Push | `origin/develop` @ `1f7f7d3` |

### Notas de merge

- Conflicto `MikroTikConnectionService`: `RouterOsEntryId.normalize` + `setOnDevice` REST (sin `executeSingleCommand` para address-list).
- `face_feature.zip` conservado desde `develop` (eliminado en rama cursor).
- Doc: `.agent-docs/netdiag-mikrotik-ros7.md` unificado (optical MK2 + checklist Fase 1.5).

## Tests pre-deploy

| Suite | Resultado |
|-------|-----------|
| `MikroTikConnectionServiceTest`, `RouterOsEntryIdTest` | OK |
| `./mvnw test` completo | 549/550 OK; fallo preexistente `HuaweiCliSessionReadTest.readUntil falla rapido si la sesion SSH muere durante el comando` |

Deploy WAR usa `-DskipTests` (script estándar).

## Backend

```bash
./scripts/deploy.sh --deploy
```

| Item | Valor |
|------|--------|
| Release | `1.0.3+1f7f7d3` |
| Health | `GET https://api.gigafiberperu.cloud/ispadmin/` → 200 |
| NetDiag summary | `GET /ispadmin/api/netdiag/incidents/summary` + `X-Netdiag-Key` → 200 |

## Backoffice (post backend)

Build `8a31c61` en `origin/develop` (NOC summary badge, polling visibilidad).

```bash
npm run build -- --mode production
rsync -avz --delete dist/ root@212.85.13.47:/var/www/gigafiber/backoffice/
```

| Check | Resultado |
|-------|-----------|
| `https://backoffice.gigafiberperu.cloud/` | 200 |
