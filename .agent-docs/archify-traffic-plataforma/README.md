# Traffic en la plataforma

Diagrama Archify (`architecture`, showcase) de cómo el WAR Traffic se integra con Core, Redis, MySQL y MikroTik.

- Spec: [traffic-plataforma.architecture.json](./traffic-plataforma.architecture.json)
- HTML: [traffic-plataforma.html](./traffic-plataforma.html)

La UI fija del viewer queda en inglés (`html lang` fallback). El contenido del diagrama está en español.

Hechos: clientes solo hablan con Core; Traffic polléa RouterOS REST, persiste rollups y publica `gigafiber.events`; el live 360 (`live-readings`) va Core → MikroTik y no pasa por Traffic.
