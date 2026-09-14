# MK2 (38.224.231.4) — uplink OLT 0/3/2 piloto VLAN 100
# Aplicado 2026-07-19. Ver .agent-docs/mikrotik-mk2-config-olt-uplink.md
# IMPORTANTE: con native VLAN 100 untagged en OLT, el gateway va en sfp-sfpplus2
# (usar scripts/mikrotik-mk2-pilot-gateway-fix.rsc, no vlan100-olt para la IP)

/interface bridge port remove [find interface=sfp-sfpplus2]
/interface ethernet set [find default-name=sfp-sfpplus2] comment="OLT 0/3/2 uplink 10G"
/interface vlan add name=vlan100-olt interface=sfp-sfpplus2 vlan-id=100 comment="OLT piloto VLAN100" l3-hw-offloading=yes
/ip firewall filter add chain=forward action=accept in-interface=sfp-sfpplus2 comment="OLT piloto VLAN100 forward"
/ip firewall filter add chain=forward action=accept out-interface=sfp-sfpplus2 connection-state=established,related comment="OLT piloto VLAN100 return"
/ip firewall filter add chain=input action=accept in-interface=sfp-sfpplus2 comment="OLT piloto VLAN100 input mgmt"
/ip address add address=192.168.30.1/24 interface=sfp-sfpplus2 comment="OLT piloto VLAN100 native untagged"
