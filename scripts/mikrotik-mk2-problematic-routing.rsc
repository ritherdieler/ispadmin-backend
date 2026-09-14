# MK2 — policy routing clientes problematicos (toTarazona)
# Aplicado 2026-08-19. IP principal 38.224.231.4/27 NO se modifica.
# WAN iface MK2: "WAN-VLAN SFP-SFPPLUS1"
# SNAT: 8.243.126.161 | gateway ruta: 38.224.231.1 | table: toTarazona
#
# Importar address-list ANTES (scripts/mikrotik-mk2-problematic-address-list.rsc)

/routing table
:if ([:len [find name=toTarazona]] = 0) do={
  add name=toTarazona fib
}

/ip address
:if ([:len [find address="8.243.126.160/32"]] = 0) do={
  add address=8.243.126.160/32 interface="WAN-VLAN SFP-SFPPLUS1" comment="IP 8 CORP TARAZONA"
}
:if ([:len [find address="8.243.126.161/32"]] = 0) do={
  add address=8.243.126.161/32 interface="WAN-VLAN SFP-SFPPLUS1" comment="SNAT problematicos"
}
:if ([:len [find address="8.243.126.162/32"]] = 0) do={
  add address=8.243.126.162/32 interface="WAN-VLAN SFP-SFPPLUS1"
}
# 8.243.126.163 puede ya existir (IP SECUNDARIA PAG PROBLEMATICAS)

/ip firewall mangle
:if ([:len [find comment="Clientes problematicos -> Tarazona (forzado)"]] = 0) do={
  add chain=prerouting action=mark-routing new-routing-mark=toTarazona passthrough=no \
    in-interface=LAN-VLAN1 src-address-list="Clientes con paginas problematicas" \
    comment="Clientes problematicos -> Tarazona (forzado)"
}
:if ([:len [find comment="Clientes problematicos VLAN100 -> Tarazona"]] = 0) do={
  add chain=prerouting action=mark-routing new-routing-mark=toTarazona passthrough=no \
    in-interface=sfp-sfpplus2 src-address-list="Clientes con paginas problematicas" \
    comment="Clientes problematicos VLAN100 -> Tarazona"
}

/ip route
:if ([:len [find routing-table=toTarazona dst-address=0.0.0.0/0]] = 0) do={
  add dst-address=0.0.0.0/0 gateway=38.224.231.1 routing-table=toTarazona \
    distance=1 check-gateway=ping comment="Default marcada -> Tarazona"
}

/ip firewall nat
:if ([:len [find comment="SNAT problematicos -> 8.243.126.161"]] = 0) do={
  add chain=srcnat action=src-nat routing-mark=toTarazona to-addresses=8.243.126.161 \
    comment="SNAT problematicos -> 8.243.126.161" place-before=[find chain=srcnat action=masquerade out-interface="WAN-VLAN SFP-SFPPLUS1"]
}
