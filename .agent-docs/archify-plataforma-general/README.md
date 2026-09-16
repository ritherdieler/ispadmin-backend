# Plataforma Gigafiber

Diagrama Archify (`architecture`, showcase) de los 4 WARs y el campo.

- Spec: [plataforma.architecture.json](./plataforma.architecture.json)
- HTML: [plataforma.html](./plataforma.html)

La UI fija del viewer queda en inglés. El contenido del diagrama está en español.

Hechos: clientes solo entran por Core; Traffic / Gateway / ACS son hermanos HTTP; Redis solo lo publican Traffic y Gateway; ACS habla con GenieACS y manda `cpe-inform` al Gateway.
