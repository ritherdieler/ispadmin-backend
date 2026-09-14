# MK1 — deshabilitar policy toTarazona + IPs 8.243.126.x (post-validacion MK2)
# Aplicado 2026-08-19. Usa disabled=yes (rollback: disabled=no).
# NO toca IP principal 38.224.231.2/27

/ip address
set [find address~"8.243.126"] disabled=yes

/ip firewall mangle
set [find comment~"Clientes problematicos"] disabled=yes

/ip firewall nat
set [find comment~"SNAT problematicos"] disabled=yes

/ip route
set [find routing-table=toTarazona] disabled=yes

/ip firewall address-list
set [find list="Clientes con paginas problematicas"] disabled=yes
