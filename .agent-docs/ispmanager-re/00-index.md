# Pack RE SmartOLT → IspManager

Pack documental para diseñar **IspManager** (clon funcional de SmartOLT) sin scaffold de producto.

Instancia capturada: `https://gigafiberperu.smartolt.com` (v3.53.0), OLT `2 - HAWEI` (MA5608T), ~758 ONUs.  
Fecha pack: 2026-07-17.

## Cómo usar este pack

1. Leer [01-sitemap-and-ia.md](./01-sitemap-and-ia.md) para el mapa de pantallas.
2. Contratar APIs con [02-api-contracts.md](./02-api-contracts.md) (pública verificada + UI AJAX).
3. Diseñar jobs/poll con [03-polling-and-jobs.md](./03-polling-and-jobs.md).
4. Detallar UX por pantalla en [04-screen-specs/](./04-screen-specs/).
5. Formatos CSV en [05-export-import-formats.md](./05-export-import-formats.md).
6. Auth/permisos en [06-permissions-and-auth.md](./06-permissions-and-auth.md).
7. Look & feel (especificación, no assets) en [07-ui-spec-notes.md](./07-ui-spec-notes.md).
8. Priorizar MVP con [08-gap-matrix.md](./08-gap-matrix.md).

## Contexto previo (no duplicar)

| Doc | Rol |
|-----|-----|
| [../smartolt-onu-data-model.md](../smartolt-onu-data-model.md) | Capas A/B/C del modelo ONU |
| [../olt-manager-db-model.md](../olt-manager-db-model.md) | DDL propuesto IspManager |
| [../smartolt-reverse-engineering.md](../smartolt-reverse-engineering.md) | Cadencias View ONU, endpoints UI, flujos |
| [../olt-gateway-read-mvp.md](../olt-gateway-read-mvp.md) | Gateway CLI read-only actual |

## Contenido del pack

| Archivo | Contenido |
|---------|-----------|
| [00-index.md](./00-index.md) | Este índice |
| [01-sitemap-and-ia.md](./01-sitemap-and-ia.md) | Menú, rutas, deep links |
| [02-api-contracts.md](./02-api-contracts.md) | Contratos request/response |
| [03-polling-and-jobs.md](./03-polling-and-jobs.md) | Cadencias UI + jobs server |
| [04-screen-specs/](./04-screen-specs/) | Fichas por pantalla |
| [05-export-import-formats.md](./05-export-import-formats.md) | Schemas CSV / samples |
| [06-permissions-and-auth.md](./06-permissions-and-auth.md) | Sesión, X-Token, roles |
| [07-ui-spec-notes.md](./07-ui-spec-notes.md) | Layout / tokens visuales |
| [08-gap-matrix.md](./08-gap-matrix.md) | Feature → cobertura → MVP |

## Método de captura (esta corrida)

- API pública: GET/listados con header `X-Token` (API key de Settings; **enmascarada** en docs). Verificado en vivo 2026-07-17.
- Colección Postman pública SmartOLT: 107 endpoints (`api.smartolt.com` / documenter).
- UI AJAX / cadencias: `smartolt-reverse-engineering.md` + hooks XHR en chat principal.
- Browser MCP en **chat principal** (2026-07-17): Configured list, Graphs, Events, Diagnostics, Tasks, Export/Import, presets wizard (sin save), General/Users, config mismatch, VPN/TR069. Subagente previo sin browser; gaps N/A rellenados aquí.
- Hooks JS en página (`window.__ispRE`): buffer de `console.*` + `error`/`unhandledrejection`, XHR y `fetch`; persistencia opcional con CDP `Page.addScriptToEvaluateOnNewDocument`. Dump: `JSON.stringify(window.__ispRE.console)`.
- No se ejecutaron writes destructivos (reboot/delete/resync/batch execute/scan auto-fix).

## Reglas del pack

- Solo documentación. Sin código de producto IspManager.
- Tokens/API keys/secrets enmascarados (`***`).
- Payloads de write documentados; **no ejecutados** (reboot/delete/resync/disable reales).
- Gaps honestos: lo no observable desde web/API queda N/A.
