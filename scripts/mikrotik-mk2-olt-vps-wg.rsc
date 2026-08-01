# MK2: WireGuard con VPS ispAdmin + enrutamiento a OLT (plantilla; claves vía apply script)
# VPS 212.85.13.47:51820 <-> MK2 38.224.231.4:51830
# Tunnel: 10.255.255.0/30 (VPS 10.255.255.2, MK2 10.255.255.1)
# Reemplaza GRE gre-ispadmin-vps; SNAT origen túnel hacia LAN OLT

/interface gre remove [find name=gre-ispadmin-vps]
/interface wireguard remove [find name=wg-ispadmin-vps]
/interface wireguard add name=wg-ispadmin-vps listen-port=51830 private-key="REPLACE_MK2_PRIVATE_KEY" \
    comment="ispAdmin VPS WireGuard -> OLT LAN (MK2)"

/ip address remove [find interface=wg-ispadmin-vps]
/ip address add address=10.255.255.1/30 interface=wg-ispadmin-vps comment="ispAdmin VPS WG MK2"

/interface wireguard peers remove [find interface=wg-ispadmin-vps]
/interface wireguard peers add interface=wg-ispadmin-vps public-key="REPLACE_VPS_PUBLIC_KEY" \
    endpoint-address=212.85.13.47 endpoint-port=51820 allowed-address=10.255.255.2/32 \
    persistent-keepalive=25s comment="ispAdmin VPS"

/ip firewall address-list remove [find list=IspAdminVPS address=212.85.13.47]
/ip firewall address-list add list=IspAdminVPS address=212.85.13.47 comment="ispAdmin VPS"
/ip firewall address-list remove [find list=IspAdminVPS address=10.255.255.2]
/ip firewall address-list add list=IspAdminVPS address=10.255.255.2 comment="ispAdmin VPS tunnel peer"

/ip firewall nat remove [find comment="IspAdmin VPS GRE -> OLT LAN SNAT"]
/ip firewall nat remove [find comment="IspAdmin VPS WG -> OLT LAN SNAT"]
/ip firewall nat add chain=srcnat action=masquerade src-address=10.255.255.0/30 \
    dst-address=10.11.104.0/24 comment="IspAdmin VPS WG -> OLT LAN SNAT"

/ip firewall filter remove [find comment="IspAdmin VPS GRE accept"]
/ip firewall filter remove [find comment="IspAdmin VPS GRE forward"]
/ip firewall filter remove [find comment="IspAdmin VPS WG accept"]
/ip firewall filter add chain=input protocol=udp dst-port=51830 src-address=212.85.13.47 action=accept \
    comment="IspAdmin VPS WG accept" place-before=0
/ip firewall filter remove [find comment="IspAdmin VPS WG forward"]
/ip firewall filter add chain=forward in-interface=wg-ispadmin-vps src-address=10.255.255.0/30 \
    dst-address=10.11.104.0/24 action=accept comment="IspAdmin VPS WG forward" place-before=0
