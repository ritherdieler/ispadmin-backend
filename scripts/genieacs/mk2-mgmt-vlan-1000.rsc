# MK2: VLAN 1000 de gestion CPE (TR-069 + staging e2e + web/SSH ONU)
# Idempotente. Aplicar en MK2 (38.224.231.4) con admin RouterOS.
# Transporte: tagged por el NNI OLT 0/3/2 -> sfp-sfpplus2 (native del puerto sigue 101).
# Gateway TR-069 10.20.0.1/22 y staging e2e 10.20.250.1/24, ambos en vlan1000-olt.
# DNS 8.8.8.8, 8.8.4.4. Pool DHCP 10.20.0.2-10.20.3.254. Lease 1h.
# El staging e2e /24 no lleva DHCP: la IP se pinta por TR-069.
# La red de aprovisionamiento de la VLAN 100 sigue viva y no se toca aqui.
# VPS wg-olt debe enrutar 10.20.0.0/22 y 10.20.250.0/24.

:local iface "sfp-sfpplus2"
:local mgmtIf "vlan1000-olt"
:local mgmtNet "10.20.0.0/22"
:local stagingNet "10.20.250.0/24"

# --- Subinterfaz VLAN 1000 tagged sobre el uplink OLT ---
:if ([:len [/interface vlan find where name=$mgmtIf]] = 0) do={
  /interface vlan add name=$mgmtIf interface=$iface vlan-id=1000 \
    comment="Terminacion VLAN 1000 gestion CPE tagged OLT 0/3/2"
} else={
  /interface vlan set [find where name=$mgmtIf] interface=$iface vlan-id=1000 \
    comment="Terminacion VLAN 1000 gestion CPE tagged OLT 0/3/2"
}

# --- Gateway TR-069 /22 ---
:if ([:len [/ip address find where address="10.20.0.1/22"]] = 0) do={
  /ip address add address=10.20.0.1/22 interface=$mgmtIf \
    comment="Gateway gestion CPE TR-069 VLAN1000"
} else={
  /ip address set [find where address="10.20.0.1/22"] interface=$mgmtIf \
    comment="Gateway gestion CPE TR-069 VLAN1000"
}

# --- Gateway staging e2e /24 (sin DHCP) ---
:if ([:len [/ip address find where address="10.20.250.1/24"]] = 0) do={
  /ip address add address=10.20.250.1/24 interface=$mgmtIf \
    comment="Gateway staging e2e VLAN1000"
} else={
  /ip address set [find where address="10.20.250.1/24"] interface=$mgmtIf \
    comment="Gateway staging e2e VLAN1000"
}

# --- Interface list: solo la subinterfaz, no el puerto fisico (lo comparte la VLAN 100) ---
:if ([:len [/interface list find where name="OLT-VLAN1000"]] = 0) do={
  /interface list add name=OLT-VLAN1000 comment="Gestion CPE VLAN 1000"
}
:if ([:len [/interface list member find where list="OLT-VLAN1000" and interface=$mgmtIf]] = 0) do={
  /interface list member add list=OLT-VLAN1000 interface=$mgmtIf
}

# --- Pool DHCP /22 ---
:if ([:len [/ip pool find where name="mgmt-1000"]] = 0) do={
  /ip pool add name=mgmt-1000 ranges=10.20.0.2-10.20.3.254 \
    comment="DHCP gestion CPE 10.20.0.0/22 VLAN1000"
} else={
  /ip pool set [find where name="mgmt-1000"] ranges=10.20.0.2-10.20.3.254 \
    comment="DHCP gestion CPE 10.20.0.0/22 VLAN1000"
}

# --- DHCP network /22 ---
:if ([:len [/ip dhcp-server network find where address=$mgmtNet]] = 0) do={
  /ip dhcp-server network add address=$mgmtNet gateway=10.20.0.1 \
    dns-server=8.8.8.8,8.8.4.4 comment="Gestion CPE VLAN1000 /22"
} else={
  /ip dhcp-server network set [find where address=$mgmtNet] gateway=10.20.0.1 \
    dns-server=8.8.8.8,8.8.4.4 comment="Gestion CPE VLAN1000 /22"
}

# --- DHCP server en la subinterfaz VLAN 1000 ---
:if ([:len [/ip dhcp-server find where name="dhcp-mgmt-1000"]] = 0) do={
  /ip dhcp-server add name=dhcp-mgmt-1000 interface=$mgmtIf address-pool=mgmt-1000 \
    lease-time=1h disabled=no comment="Gestion CPE VLAN1000 /22"
} else={
  /ip dhcp-server set [find where name="dhcp-mgmt-1000"] interface=$mgmtIf \
    address-pool=mgmt-1000 lease-time=1h disabled=no comment="Gestion CPE VLAN1000 /22"
}

# --- Firewall: Connection Request GenieACS desde el VPS por wg ---
:if ([:len [/ip firewall filter find where comment~"GenieACS CR mgmt 1000"]] = 0) do={
  /ip firewall filter add chain=forward action=accept protocol=tcp dst-port=7547 \
    src-address=10.255.255.2 dst-address=$mgmtNet in-interface=wg-ispadmin-vps \
    comment="GenieACS CR mgmt 1000 from VPS"
} else={
  /ip firewall filter set [find where comment~"GenieACS CR mgmt 1000" and protocol=tcp] \
    dst-address=$mgmtNet comment="GenieACS CR mgmt 1000 from VPS"
}

:if ([:len [/ip firewall filter find where comment~"GenieACS CR mgmt 1000 return"]] = 0) do={
  /ip firewall filter add chain=forward action=accept connection-state=established,related \
    src-address=$mgmtNet out-interface=wg-ispadmin-vps \
    comment="GenieACS CR mgmt 1000 return"
}

# --- NAT: las ONUs de gestion alcanzan el ACS (Inform HTTPS) ---
:if ([:len [/ip firewall nat find where comment~"NAT mgmt 1000 /22"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=$mgmtNet \
    comment="NAT mgmt 1000 /22"
}
# VPS (10.255.255.2) → CPE 10.20: la ONU no tiene ruta de vuelta al /30 de wg.
:if ([:len [/ip firewall nat find where comment~"GenieACS CR srcnat mgmt 1000"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=10.255.255.2 \
    dst-address=$mgmtNet out-interface=$mgmtIf \
    comment="GenieACS CR srcnat mgmt 1000"
}
:if ([:len [/ip firewall nat find where comment~"NAT mgmt 1000 staging"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=$stagingNet \
    comment="NAT mgmt 1000 staging /24"
}

# --- Aislamiento: gestion no habla con las LAN de abonado ---
:if ([:len [/ip firewall address-list find where list="mgmt-1000-nets" and address=$mgmtNet]] = 0) do={
  /ip firewall address-list add list=mgmt-1000-nets address=$mgmtNet \
    comment="Gestion CPE VLAN1000"
}
:if ([:len [/ip firewall address-list find where list="mgmt-1000-nets" and address=$stagingNet]] = 0) do={
  /ip firewall address-list add list=mgmt-1000-nets address=$stagingNet \
    comment="Staging e2e VLAN1000"
}

:if ([:len [/ip firewall filter find where comment~"Drop mgmt 1000 to LAN-VLAN1"]] = 0) do={
  /ip firewall filter add chain=forward action=drop \
    src-address-list=mgmt-1000-nets dst-address=192.168.22.0/24 \
    comment="Drop mgmt 1000 to LAN-VLAN1"
}
