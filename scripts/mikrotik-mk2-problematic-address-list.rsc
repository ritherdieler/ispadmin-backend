# MK2 — address-list "Clientes con paginas problematicas"
# Fuente: export live MK1 2026-08-19 (14 entradas validas; excluida 0.0.0.0)
# No modifica IP principal 38.224.231.4

/ip firewall address-list
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.88.60]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.88.60
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.25.73]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.25.73
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.22.174]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.22.174
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.93.69]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.93.69 comment="pereza toshiro"
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.25.216]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.25.216 comment="fiorela decena "
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.25.119]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.25.119
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.25.52]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.25.52
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.221.17]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.221.17 comment="ivan gamez ocaa - direc tv"
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.210.122]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.210.122
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.210.140]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.210.140 comment="Posta la villa - SIS "
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.22.54]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.22.54 comment="JUDIT ESPINOZA GUIMAREY"
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.26.46]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.26.46 comment="EYSER CARRANZA"
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.99.169]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.99.169 comment="JONATAN PRUEVA TEST"
}
:if ([:len [find list="Clientes con paginas problematicas" address=192.168.30.202]] = 0) do={
  add list="Clientes con paginas problematicas" address=192.168.30.202 comment="RUBEL INGA CAQUI - APP CAJA HUANCAYO"
}
