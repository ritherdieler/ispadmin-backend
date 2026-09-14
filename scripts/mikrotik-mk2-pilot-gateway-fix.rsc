# MK2 — corrección gateway: OLT envía VLAN 100 native untagged en 0/3/2
# Aplicado 2026-07-19 ~23:35 tras ONU piloto online sin ARP en vlan100-olt
# Síntoma: vlan100-olt RX=0, sfp-sfpplus2 RX>0, ARP failed

/ip address remove [find interface=vlan100-olt]
/ip address add address=192.168.30.1/24 interface=sfp-sfpplus2 comment="OLT piloto VLAN100 native untagged"
/ip firewall filter set [find comment="OLT piloto VLAN100 forward"] in-interface=sfp-sfpplus2
/ip firewall filter set [find comment="OLT piloto VLAN100 return"] out-interface=sfp-sfpplus2
/ip firewall filter set [find comment="OLT piloto VLAN100 input mgmt"] in-interface=sfp-sfpplus2
