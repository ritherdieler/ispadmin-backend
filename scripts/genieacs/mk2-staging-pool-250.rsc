# MK2: gateway staging e2e 192.168.250.0/24 (VLAN 100 / sfp-sfpplus2)
# Idempotente. Aplicar en MK2 (38.224.231.4) con admin RouterOS.
# Coexiste con 192.168.30.1/24 (prod) y 192.168.255.1/24 (ACS DHCP) en la misma interfaz.
# Sin DHCP: el alta FIBER staging asigna IP estática por TR-069.

:local gwComment "Gateway staging e2e VLAN100"
:local iface "sfp-sfpplus2"

:if ([:len [/ip address find where address="192.168.250.1/24"]] = 0) do={
  /ip address add address=192.168.250.1/24 interface=$iface comment=$gwComment
} else={
  /ip address set [find where address="192.168.250.1/24"] comment=$gwComment interface=$iface
}

:if ([:len [/ip firewall nat find where comment="NAT staging e2e 250"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=192.168.250.0/24 \
    comment="NAT staging e2e 250"
}

:if ([:len [/ip firewall address-list find where list="staging-e2e-250" address="192.168.250.0/24"]] = 0) do={
  /ip firewall address-list add list=staging-e2e-250 address=192.168.250.0/24 \
    comment="Staging e2e pool"
}

:if ([:len [/ip firewall filter find where comment="Drop staging 250 to legacy LAN_MK1"]] = 0) do={
  /ip firewall filter add chain=forward action=drop \
    src-address-list=staging-e2e-250 dst-address=192.168.22.0/24 \
    comment="Drop staging 250 to legacy LAN_MK1"
}
