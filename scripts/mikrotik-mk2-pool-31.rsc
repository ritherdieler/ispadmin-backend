# MK2: gateway prod VLAN100 192.168.31.0/24 (vlan100-olt)
# Idempotente. Aplicar en MK2 (38.224.231.4) con admin RouterOS.
# Tras cutover 2026-09-10 la VLAN 100 termina tagged en vlan100-olt.
# Coexiste con 192.168.30.1/24 en la misma interfaz.
# NAT WAN masquerade cubre el segmento; se añade srcnat explícito.

:if ([:len [/ip address find where address="192.168.31.1/24"]] = 0) do={
  /ip address add address=192.168.31.1/24 interface=vlan100-olt comment="Gateway clientes VLAN100 192.168.31.0/24"
} else={
  /ip address set [find where address="192.168.31.1/24"] comment="Gateway clientes VLAN100 192.168.31.0/24" interface=vlan100-olt
}

:if ([:len [/ip firewall nat find where comment="NAT prod VLAN100 31"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=192.168.31.0/24 comment="NAT prod VLAN100 31"
}

:if ([:len [/ip firewall address-list find where list="prod-vlan100-31" address="192.168.31.0/24"]] = 0) do={
  /ip firewall address-list add list=prod-vlan100-31 address=192.168.31.0/24 comment="Prod VLAN100 pool 31"
}

/ip address print where address~"192.168.3[01]"
/ip firewall nat print where comment~"VLAN100 31|prod VLAN100"
