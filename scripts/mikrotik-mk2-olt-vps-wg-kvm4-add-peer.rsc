# MK2: segundo peer WireGuard hacia KVM4 (ADD only; no toca el peer de prod).
# KVM4 2.24.66.53:51820 <-> MK2 38.224.231.4:51830
# Tunnel dual-run: 10.255.254.0/30 (KVM4 10.255.254.2, MK2 10.255.254.1)
# Prod sigue en 10.255.255.0/30. No usar apply-mk2-olt-vps-wg-from-vps.sh aqui.

/ip address remove [find comment="ispAdmin VPS KVM4 WG MK2"]
/ip address add address=10.255.254.1/30 interface=wg-ispadmin-vps comment="ispAdmin VPS KVM4 WG MK2"

/interface wireguard peers remove [find comment="ispAdmin VPS KVM4"]
/interface wireguard peers add interface=wg-ispadmin-vps public-key="REPLACE_KVM4_PUBLIC_KEY" \
    endpoint-address=2.24.66.53 endpoint-port=51820 allowed-address=10.255.254.2/32 \
    persistent-keepalive=25s comment="ispAdmin VPS KVM4"

/ip firewall address-list remove [find list=IspAdminVPS address=2.24.66.53]
/ip firewall address-list add list=IspAdminVPS address=2.24.66.53 comment="ispAdmin VPS KVM4"
/ip firewall address-list remove [find list=IspAdminVPS address=10.255.254.2]
/ip firewall address-list add list=IspAdminVPS address=10.255.254.2 comment="ispAdmin VPS KVM4 tunnel peer"
/ip firewall address-list remove [find list=api_whitelist address=2.24.66.53]
/ip firewall address-list add list=api_whitelist address=2.24.66.53 comment="ispAdmin VPS KVM4"

/ip firewall nat remove [find comment="IspAdmin VPS KVM4 WG -> OLT LAN SNAT"]
/ip firewall nat add chain=srcnat action=masquerade src-address=10.255.254.0/30 \
    dst-address=10.11.104.0/24 comment="IspAdmin VPS KVM4 WG -> OLT LAN SNAT"
/ip firewall nat remove [find comment="GenieACS CR srcnat mgmt 1000 KVM4"]
/ip firewall nat add chain=srcnat action=masquerade src-address=10.255.254.2 \
    dst-address=10.20.0.0/22 out-interface=vlan1000-olt comment="GenieACS CR srcnat mgmt 1000 KVM4"

/ip firewall filter remove [find comment="IspAdmin VPS KVM4 WG accept"]
/ip firewall filter add chain=input protocol=udp dst-port=51830 src-address=2.24.66.53 action=accept \
    comment="IspAdmin VPS KVM4 WG accept" place-before=0
/ip firewall filter remove [find comment="IspAdmin VPS KVM4 WG forward"]
/ip firewall filter add chain=forward in-interface=wg-ispadmin-vps src-address=10.255.254.0/30 \
    dst-address=10.11.104.0/24 action=accept comment="IspAdmin VPS KVM4 WG forward" place-before=0

/ip service set ssh address=212.85.13.47/32,192.168.0.0/16,2.24.66.53/32
/ip service set www-ssl address=212.85.13.47/32,192.168.0.0/16,10.64.60.4/32,2.24.66.53/32
/ip service set api address=212.85.13.47/32,192.168.0.0/16,2.24.66.53/32
