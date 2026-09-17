# Traffic en la plataforma

Diagrama Archify (`architecture`, showcase) de cómo el WAR Traffic se integra con Core, Redis, MySQL y MikroTik.

- Spec: [traffic-plataforma.architecture.json](./traffic-plataforma.architecture.json)
- HTML: [traffic-plataforma.html](./traffic-plataforma.html)

La UI fija del viewer queda en inglés (`html lang` fallback). El contenido del diagrama está en español.

Hechos: clientes solo hablan con Core; Traffic es el único que abre RouterOS REST (poll, live 360 y escrituras). Core llama Traffic HTTP. Detalle: [mikrotik-only-via-traffic-2026-09-16.md](../mikrotik-only-via-traffic-2026-09-16.md).
