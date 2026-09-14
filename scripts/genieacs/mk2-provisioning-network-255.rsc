# MK2: red de aprovisionamiento TR-069 192.168.252.0/22 (VLAN 100 / sfp-sfpplus2)
# Idempotente. Aplicar en MK2 (38.224.231.4) con admin RouterOS.
# Gateway 192.168.255.1/22 en sfp-sfpplus2 (uplink OLT 0/3/2, native VLAN 100 untagged).
# Coexiste con 192.168.30.1/24 (prod) en la misma interfaz.
# DNS: 8.8.8.8, 8.8.4.4. Pool DHCP .252.2–.255.254. Lease 1h.
# VPS WG debe enrutar 192.168.252.0/22 (no solo 255/24).

:local gwComment "Gateway aprovisionamiento TR-069 VLAN100 /22"
:local iface "sfp-sfpplus2"

# --- Migración: quitar staging residual del bridge VLAN 1 ---
:foreach lan1 in={"LAN-VLAN1";"LAN_MK1"} do={
  :if ([:len [/ip address find where address~"192.168.255.1/" and interface=$lan1]] > 0) do={
    /ip address remove [find where address~"192.168.255.1/" and interface=$lan1]
  }
  :if ([:len [/ip dhcp-server find where name="dhcp-provisioning-255" and interface=$lan1]] > 0) do={
    /ip dhcp-server remove [find where name="dhcp-provisioning-255" and interface=$lan1]
  }
}

# --- L3 gateway /22 (mismo .255.1 para no romper leases existentes) ---
:if ([:len [/ip address find where address="192.168.255.1/22"]] = 0) do={
  :if ([:len [/ip address find where address="192.168.255.1/24"]] > 0) do={
    /ip address set [find where address="192.168.255.1/24"] address=192.168.255.1/22 comment=$gwComment interface=$iface
  } else={
    /ip address add address=192.168.255.1/22 interface=$iface comment=$gwComment
  }
} else={
  /ip address set [find where address="192.168.255.1/22"] comment=$gwComment interface=$iface
}

# --- DHCP pool /22 ---
:if ([:len [/ip pool find where name="provisioning-255"]] = 0) do={
  /ip pool add name=provisioning-255 ranges=192.168.252.2-192.168.255.254 \
    comment="DHCP staging TR-069 192.168.252.0/22 VLAN100"
} else={
  /ip pool set [find where name="provisioning-255"] ranges=192.168.252.2-192.168.255.254 \
    comment="DHCP staging TR-069 192.168.252.0/22 VLAN100"
}

# --- DHCP network /22 ---
:if ([:len [/ip dhcp-server network find where address="192.168.252.0/22"]] = 0) do={
  :if ([:len [/ip dhcp-server network find where address="192.168.255.0/24"]] > 0) do={
    /ip dhcp-server network set [find where address="192.168.255.0/24"] \
      address=192.168.252.0/22 gateway=192.168.255.1 dns-server=8.8.8.8,8.8.4.4 \
      comment="Staging TR-069 VLAN100 /22"
  } else={
    /ip dhcp-server network add address=192.168.252.0/22 gateway=192.168.255.1 \
      dns-server=8.8.8.8,8.8.4.4 comment="Staging TR-069 VLAN100 /22"
  }
} else={
  /ip dhcp-server network set [find where address="192.168.252.0/22"] \
    gateway=192.168.255.1 dns-server=8.8.8.8,8.8.4.4 comment="Staging TR-069 VLAN100 /22"
}

# --- DHCP server ---
:if ([:len [/ip dhcp-server find where name="dhcp-provisioning-255"]] = 0) do={
  /ip dhcp-server add name=dhcp-provisioning-255 interface=$iface address-pool=provisioning-255 \
    lease-time=1h disabled=no comment="Staging TR-069 VLAN100 /22"
} else={
  /ip dhcp-server set [find where name="dhcp-provisioning-255"] interface=$iface \
    address-pool=provisioning-255 lease-time=1h disabled=no comment="Staging TR-069 VLAN100 /22"
}

# --- Firewall: Connection Request GenieACS desde VPS (wg) hacia .252/22:7547 ---
:if ([:len [/ip firewall filter find where comment~"GenieACS CR staging 25"]] = 0) do={
  /ip firewall filter add chain=forward action=accept protocol=tcp dst-port=7547 \
    src-address=10.255.255.2 dst-address=192.168.252.0/22 in-interface=wg-ispadmin-vps \
    comment="GenieACS CR staging 252/22 from VPS"
} else={
  /ip firewall filter set [find where comment~"GenieACS CR staging 25" and protocol=tcp] \
    dst-address=192.168.252.0/22 comment="GenieACS CR staging 252/22 from VPS"
}

:if ([:len [/ip firewall filter find where comment~"GenieACS CR staging 25" and connection-state~"established"]] = 0) do={
  /ip firewall filter add chain=forward action=accept connection-state=established,related \
    src-address=192.168.252.0/22 out-interface=wg-ispadmin-vps \
    comment="GenieACS CR staging 252/22 return"
} else={
  /ip firewall filter set [find where comment~"GenieACS CR staging 25" and connection-state~"established"] \
    src-address=192.168.252.0/22 comment="GenieACS CR staging 252/22 return"
}

# --- NAT: ONUs staging salen a Internet (Inform ACS HTTPS) ---
:if ([:len [/ip firewall nat find where comment~"NAT staging TR-069"]] = 0) do={
  /ip firewall nat add chain=srcnat action=masquerade src-address=192.168.252.0/22 \
    comment="NAT staging TR-069 252/22"
} else={
  /ip firewall nat set [find where comment~"NAT staging TR-069"] \
    src-address=192.168.252.0/22 comment="NAT staging TR-069 252/22"
}

# --- Aislamiento suave: bloquear staging → pools VLAN 1 (LAN-VLAN1) ---
:if ([:len [/ip firewall address-list find where list="staging-tr069-255" and address="192.168.252.0/22"]] = 0) do={
  /ip firewall address-list add list=staging-tr069-255 address=192.168.252.0/22 \
    comment="Staging TR-069 /22"
}
:foreach old in=[/ip firewall address-list find where list="staging-tr069-255" and address="192.168.255.0/24"] do={
  /ip firewall address-list remove $old
}

:if ([:len [/ip firewall filter find where comment~"Drop staging 25"]] = 0) do={
  /ip firewall filter add chain=forward action=drop \
    src-address-list=staging-tr069-255 dst-address=192.168.22.0/24 \
    comment="Drop staging 252/22 to LAN-VLAN1"
} else={
  /ip firewall filter set [find where comment~"Drop staging 25"] \
    comment="Drop staging 252/22 to LAN-VLAN1"
}
