# Conexiones reales a MikroTik

Diagrama Archify (`architecture`, showcase) de quién abre REST `:443` hoy.

- Spec: [mikrotik-conexiones.architecture.json](./mikrotik-conexiones.architecture.json)
- HTML: [mikrotik-conexiones.html](./mikrotik-conexiones.html)

La UI fija del viewer queda en inglés. El contenido está en español.

Un cliente REST de runtime: Traffic. Core, NetDiag y consola llaman HTTP a Traffic. La OLT solo la toca el Gateway. Detalle: [mikrotik-only-via-traffic-2026-09-16.md](../mikrotik-only-via-traffic-2026-09-16.md).
