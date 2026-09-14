# UI spec notes — IspManager (especificación, no copiar assets)

Objetivo: orientar look & feel de IspManager. **No** reutilizar CSS/JS/imágenes propietarios de SmartOLT.

## 1. Stack observado en SmartOLT (inventario)

| Tecnología | Uso |
|------------|-----|
| jQuery | AJAX, DOM |
| Bootstrap (legado) | layout, modales, grid |
| DataTables | tablas Configured / reports |
| Font Awesome | iconografía |
| Charts / PNG server-side | graphs tráfico/señal (API devuelve PNG) |
| Polling helper | `SmartOLTPolling` + visibility API |

## 2. Tokens visuales (especificación IspManager)

Dirección: **consola de red / ops** — densa, legible, no marketing.

| Token | Valor sugerido | Notas |
|-------|----------------|-------|
| `--bg-app` | `#0f172a` o `#f8fafc` | elegir un tema; evitar púrpura genérico |
| `--bg-sidebar` | azul oscuro `#0b1f33` | SmartOLT usa dark blue sidebar |
| `--accent` | `#0ea5e9` o verde ops `#059669` | status Online |
| `--danger` | `#dc2626` | Critical / LOS |
| `--warning` | `#d97706` | Warning signal |
| `--ok` | `#16a34a` | Very good / Online |
| `--font-sans` | Inter **solo si** ya es estándar del producto; preferir fuente ya usada en backoffice GigaFiber | no copiar tipografía SmartOLT |
| `--radius` | 6–8px | tablas/modales |
| Densidad | compacta | filas DataTable-like |

Semántica de color de señal (replicar):

| Categoría | Color |
|-----------|-------|
| Very good / Good | verde |
| Warning | ámbar |
| Critical | rojo |
| Offline / LOS / Power fail | gris / rojo según severidad |

## 3. Layout

```text
┌──────────┬────────────────────────────────────────┐
│ Sidebar  │ Top bar (OLT context, search, user)    │
│ menú     ├────────────────────────────────────────┤
│          │ Filtros / actions                      │
│          │ Tabla o detalle                        │
│          │ Modales centrados (forms densos)       │
└──────────┴────────────────────────────────────────┘
```

- Listados: filtros arriba + more-filters colapsable + batch bar.
- View ONU: cabecera identidad PON + cards status/señal/tráfico + secciones config + acciones peligrosas al final.
- OLT details: tabs horizontales (cards, PON, uplink, VLANs…).

## 4. i18n

- UI SmartOLT observada en **inglés**.
- IspManager: ES primario (operadores GigaFiber) + EN opcional.
- Strings clave a portar: “Resync config”, “Unconfigured”, “Permission Required”, “auto-refresh in”, “Daily Auto Configuration Check”.

## 5. Motion

Mínimo: skeleton en tablas, countdown auto-refresh, toast success/error. Sin animaciones decorativas.

## 6. Accesibilidad

- Contraste status badges.
- Modales focus trap (equiv. Bootstrap modal).
- No depender solo del color para Critical/Warning.
