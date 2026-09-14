# MK2: rollback de scripts/mikrotik-mk2-pppoe-vlan100.rsc
# Deja el router como estaba antes de la fase 4 de la migracion PPPoE.
# No toca el servidor legado "PPOE CLIENTES" ni sus perfiles PLAN *.
#
# Orden: primero expulsa sesiones, luego borra servidores, luego secrets del
# catalogo GF-*, luego perfiles, pools, direccion y reglas de firewall.
# Si quedan secrets apuntando a un perfil GF-*, RouterOS no deja borrar el perfil.

:local pppoeNet "10.64.0.0/18"
:local gatewayCidr "10.64.0.1/18"
:local serviceName "GIGAFIBER-PPPOE"
:local serviceNameStg "GIGAFIBER-PPPOE-STG"

# --- Expulsar sesiones activas de los perfiles nuevos ---
:foreach s in=[/ppp active find] do={
  :local prof [/ppp secret get [find where name=[/ppp active get $s name]] profile]
  :if ($prof ~ "^GF-") do={
    /ppp active remove $s
  }
}

# --- Servidores PPPoE nuevos ---
/interface pppoe-server server remove [find where service-name=$serviceName]
/interface pppoe-server server remove [find where service-name=$serviceNameStg]

# --- Secrets creados por el Core contra el catalogo GF-* ---
:foreach s in=[/ppp secret find] do={
  :if ([/ppp secret get $s profile] ~ "^GF-") do={
    /ppp secret remove $s
  }
}

# --- Perfiles del catalogo ---
:foreach p in=[/ppp profile find where name~"^GF-"] do={
  /ppp profile remove $p
}

# --- Pools ---
/ip pool remove [find where name="PPPOE-DINAMICO"]
/ip pool remove [find where name="PPPOE-STG"]

# --- Direccion del gateway ---
/ip address remove [find where address=$gatewayCidr]

# --- Firewall ---
/ip firewall nat remove [find where comment~"NAT PPPoE dinamico VLAN100"]
/ip firewall filter remove [find where comment~"Drop PPPoE dinamico to mgmt 1000"]
/ip firewall filter remove [find where comment~"Drop PPPoE dinamico to provisioning 252"]
/ip firewall address-list remove [find where list="pppoe-dinamico-nets"]

:put "Rollback PPPoE VLAN100 completado. El servidor legado del bridge sigue intacto."
