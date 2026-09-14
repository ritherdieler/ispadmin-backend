# MK2: servidor PPPoE con pool dinamico sobre VLAN 100 (migracion PPPoE fase 4)
# Idempotente. Aplicar en MK2 (38.224.231.4) con admin RouterOS.
# Aditivo por diseno: hoy no existe ningun pppoe-server en vlan100-olt, luego
# ningun cliente puede estar usandolo. Los 51 clientes del servidor legado
# "PPOE CLIENTES" (bridge LAN-VLAN1) no se tocan.
#
# Bloque reservado 10.64.0.0/18 = 10.64.0.0 - 10.64.63.255. Verificado libre
# contra /ip/address, /ip/route, /ip/arp, /queue/simple, /ip/pool y subscription.ip
# con scripts/mk2-pppoe-inventory.mjs.
#   10.64.0.1                    gateway en vlan100-olt
#   10.64.0.2   - 10.64.47.254   pool PPPOE-DINAMICO (12285 direcciones)
#   10.64.48.1  - 10.64.51.254   remote-address fija de empresas/colegios/cabinas (fuera de pool)
#   10.64.60.2  - 10.64.60.254   pool PPPOE-STG del piloto
#   10.64.52.0  - 10.64.59.255   y 10.64.61.0 - 10.64.63.255 libres
#
# MSS: los perfiles GF-* llevan change-tcp-mss=yes. RouterOS aplica el clamping
# solo a las sesiones de esos perfiles, asi que no hace falta ninguna regla
# mangle en chain=forward que tocaria el TCP de los 870 clientes actuales.
#
# Rollback: scripts/mikrotik-mk2-pppoe-vlan100-rollback.rsc

:local pppoeIf "vlan100-olt"
:local gateway "10.64.0.1"
:local gatewayCidr "10.64.0.1/18"
:local pppoeNet "10.64.0.0/18"
:local serviceName "GIGAFIBER-PPPOE"
:local serviceNameStg "GIGAFIBER-PPPOE-STG"
:local dns "8.8.8.8,8.8.4.4"

# --- La subinterfaz VLAN 100 ya existe; abortar si no, en vez de crearla a ciegas ---
:if ([:len [/interface vlan find where name=$pppoeIf]] = 0) do={
  :error "No existe la interfaz $pppoeIf. Revisar la VLAN 100 antes de aplicar este script."
}

# --- Gateway del bloque PPPoE ---
:if ([:len [/ip address find where address=$gatewayCidr]] = 0) do={
  /ip address add address=$gatewayCidr interface=$pppoeIf \
    comment="Gateway PPPoE dinamico VLAN100"
} else={
  /ip address set [find where address=$gatewayCidr] interface=$pppoeIf \
    comment="Gateway PPPoE dinamico VLAN100"
}

# --- Pool de produccion ---
:if ([:len [/ip pool find where name="PPPOE-DINAMICO"]] = 0) do={
  /ip pool add name=PPPOE-DINAMICO ranges=10.64.0.2-10.64.47.254 \
    comment="Pool PPPoE dinamico produccion VLAN100"
} else={
  /ip pool set [find where name="PPPOE-DINAMICO"] ranges=10.64.0.2-10.64.47.254 \
    comment="Pool PPPoE dinamico produccion VLAN100"
}

# --- Pool del piloto staging ---
:if ([:len [/ip pool find where name="PPPOE-STG"]] = 0) do={
  /ip pool add name=PPPOE-STG ranges=10.64.60.2-10.64.60.254 \
    comment="Pool PPPoE staging piloto VLAN100"
} else={
  /ip pool set [find where name="PPPOE-STG"] ranges=10.64.60.2-10.64.60.254 \
    comment="Pool PPPoE staging piloto VLAN100"
}

# --- Perfiles GF-{download}-{upload}. rate-limit en RouterOS es rx/tx visto ---
# --- desde el router, o sea upload-del-cliente / download-del-cliente.       ---
# El literal de array debe ir en una sola linea: /import parte las llaves
# multilinea como bloque de codigo y el :foreach no llega a iterar.
:local planProfiles {"GF-200-200"="200M/200M";"GF-300-300"="300M/300M";"GF-400-400"="400M/400M";"GF-500-500"="500M/500M";"GF-600-600"="600M/600M";"GF-1000-1000"="1000M/1000M"}

# La variable NO puede llamarse $name: dentro de "find where name=$name" RouterOS
# resuelve $name contra la propiedad del item, el where da verdadero para todos y
# el set arrasa con los perfiles del PPPoE legado.
:foreach profileName,profileRate in=$planProfiles do={
  :if ([:len [/ppp profile find where name=$profileName]] = 0) do={
    /ppp profile add name=$profileName local-address=$gateway remote-address=PPPOE-DINAMICO \
      dns-server=$dns rate-limit=$profileRate only-one=yes change-tcp-mss=yes \
      comment="Perfil PPPoE dinamico VLAN100"
  } else={
    /ppp profile set [find where name=$profileName] local-address=$gateway \
      remote-address=PPPOE-DINAMICO dns-server=$dns rate-limit=$profileRate only-one=yes \
      change-tcp-mss=yes comment="Perfil PPPoE dinamico VLAN100"
  }
}

# --- Perfil de corte: la sesion sigue autenticando pero sin ancho de banda util ---
:if ([:len [/ppp profile find where name="GF-CORTE"]] = 0) do={
  /ppp profile add name=GF-CORTE local-address=$gateway remote-address=PPPOE-DINAMICO \
    dns-server=$dns rate-limit=1k/1k only-one=yes change-tcp-mss=yes \
    comment="Corte por perfil PPPoE dinamico"
} else={
  /ppp profile set [find where name="GF-CORTE"] local-address=$gateway \
    remote-address=PPPOE-DINAMICO dns-server=$dns rate-limit=1k/1k only-one=yes \
    change-tcp-mss=yes comment="Corte por perfil PPPoE dinamico"
}

# --- Perfil del piloto: mismo gateway, pool y service-name propios ---
:if ([:len [/ppp profile find where name="GF-STG-200-200"]] = 0) do={
  /ppp profile add name=GF-STG-200-200 local-address=$gateway remote-address=PPPOE-STG \
    dns-server=$dns rate-limit=200M/200M only-one=yes change-tcp-mss=yes \
    comment="Perfil PPPoE staging piloto"
} else={
  /ppp profile set [find where name="GF-STG-200-200"] local-address=$gateway \
    remote-address=PPPOE-STG dns-server=$dns rate-limit=200M/200M only-one=yes \
    change-tcp-mss=yes comment="Perfil PPPoE staging piloto"
}

# --- Servidor PPPoE de produccion en VLAN 100 ---
:if ([:len [/interface pppoe-server server find where service-name=$serviceName]] = 0) do={
  /interface pppoe-server server add service-name=$serviceName interface=$pppoeIf \
    default-profile=GF-200-200 one-session-per-host=yes max-mtu=1480 max-mru=1480 \
    authentication=pap,chap,mschap1,mschap2 keepalive-timeout=10 disabled=no \
    comment="PPPoE dinamico clientes fibra VLAN100"
} else={
  /interface pppoe-server server set [find where service-name=$serviceName] \
    interface=$pppoeIf default-profile=GF-200-200 one-session-per-host=yes \
    max-mtu=1480 max-mru=1480 authentication=pap,chap,mschap1,mschap2 \
    keepalive-timeout=10 disabled=no comment="PPPoE dinamico clientes fibra VLAN100"
}

# --- Servidor PPPoE del piloto, separado por service-name ---
:if ([:len [/interface pppoe-server server find where service-name=$serviceNameStg]] = 0) do={
  /interface pppoe-server server add service-name=$serviceNameStg interface=$pppoeIf \
    default-profile=GF-STG-200-200 one-session-per-host=yes max-mtu=1480 max-mru=1480 \
    authentication=pap,chap,mschap1,mschap2 keepalive-timeout=10 disabled=no \
    comment="PPPoE staging piloto VLAN100"
} else={
  /interface pppoe-server server set [find where service-name=$serviceNameStg] \
    interface=$pppoeIf default-profile=GF-STG-200-200 one-session-per-host=yes \
    max-mtu=1480 max-mru=1480 authentication=pap,chap,mschap1,mschap2 \
    keepalive-timeout=10 disabled=no comment="PPPoE staging piloto VLAN100"
}

# --- NAT de salida. El masquerade generico por WAN ya cubriria este bloque; ---
# --- la regla explicita existe para trazabilidad, igual que mgmt-1000.      ---
:if ([:len [/ip firewall nat find where comment~"NAT PPPoE dinamico VLAN100"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=$pppoeNet \
    comment="NAT PPPoE dinamico VLAN100"
}

# --- Aislamiento: los clientes PPPoE no hablan con la red de gestion de ONUs ---
:if ([:len [/ip firewall address-list find where list="pppoe-dinamico-nets" and address=$pppoeNet]] = 0) do={
  /ip firewall address-list add list=pppoe-dinamico-nets address=$pppoeNet \
    comment="PPPoE dinamico VLAN100"
}
:if ([:len [/ip firewall filter find where comment~"Drop PPPoE dinamico to mgmt 1000"]] = 0) do={
  /ip firewall filter add chain=forward action=drop \
    src-address-list=pppoe-dinamico-nets dst-address=10.20.0.0/22 \
    comment="Drop PPPoE dinamico to mgmt 1000"
}
:if ([:len [/ip firewall filter find where comment~"Drop PPPoE dinamico to provisioning 252"]] = 0) do={
  /ip firewall filter add chain=forward action=drop \
    src-address-list=pppoe-dinamico-nets dst-address=192.168.252.0/22 \
    comment="Drop PPPoE dinamico to provisioning 252"
}

:put "PPPoE VLAN100 aplicado. Verificar con: /interface pppoe-server server print; /ppp profile print where name~\"GF-\""
