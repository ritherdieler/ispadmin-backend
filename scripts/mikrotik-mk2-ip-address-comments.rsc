# MK2: comentarios de /ip address alineados con el rol real.
# Idempotente. No crea ni mueve IPs.
#
# TR-069 de aprovisionamiento (DHCP pre-alta) = solo 192.168.252.0/22
# (gateway 192.168.255.1/22 en vlan100-olt).
# El resto de 192.168.x.1/24 en LAN-VLAN1 y 192.168.30/31 en vlan100-olt
# son gateways de clientes.

/ip address set [find where address="192.168.22.1/24"] comment="Gateway clientes VLAN1 192.168.22.0/24"
/ip address set [find where address="192.168.88.1/24"] comment="Gateway clientes VLAN1 192.168.88.0/24"
/ip address set [find where address="192.168.33.1/24"] comment="Gateway clientes VLAN1 192.168.33.0/24"
/ip address set [find where address="192.168.20.1/24"] comment="Gateway clientes VLAN1 192.168.20.0/24"
/ip address set [find where address="192.168.49.1/24"] comment="Gateway clientes VLAN1 192.168.49.0/24"
/ip address set [find where address="192.168.50.1/24"] comment="Gateway clientes VLAN1 192.168.50.0/24"
/ip address set [find where address="192.168.25.1/24"] comment="Gateway clientes VLAN1 192.168.25.0/24"
/ip address set [find where address="192.168.95.1/24"] comment="Gateway clientes VLAN1 192.168.95.0/24"
/ip address set [find where address="192.168.93.1/24"] comment="Gateway clientes VLAN1 192.168.93.0/24"
/ip address set [find where address="192.168.168.1/24"] comment="Gateway clientes VLAN1 192.168.168.0/24"
/ip address set [find where address="192.168.9.1/24"] comment="Gateway clientes VLAN1 192.168.9.0/24"
/ip address set [find where address="192.168.99.1/24"] comment="Gateway clientes VLAN1 192.168.99.0/24"
/ip address set [find where address="192.168.26.1/24"] comment="Gateway clientes VLAN1 192.168.26.0/24"
/ip address set [find where address="192.168.55.1/24"] comment="Gateway clientes VLAN1 192.168.55.0/24"
/ip address set [find where address="192.168.100.1/24"] comment="Gateway clientes VLAN1 192.168.100.0/24"
/ip address set [find where address="192.168.0.1/24"] comment="Gateway clientes VLAN1 192.168.0.0/24"
/ip address set [find where address="192.168.1.1/24"] comment="Gateway clientes VLAN1 192.168.1.0/24"
/ip address set [find where address="192.168.200.1/24"] comment="Gateway clientes VLAN1 192.168.200.0/24"
/ip address set [find where address="192.168.201.1/24"] comment="Gateway clientes VLAN1 192.168.201.0/24"
/ip address set [find where address="192.168.210.1/24"] comment="Gateway clientes VLAN1 192.168.210.0/24"
/ip address set [find where address="192.168.211.1/24"] comment="Gateway clientes VLAN1 192.168.211.0/24"
/ip address set [find where address="192.168.123.1/24"] comment="Gateway clientes VLAN1 192.168.123.0/24"
/ip address set [find where address="192.168.220.1/24"] comment="Gateway clientes VLAN1 192.168.220.0/24"
/ip address set [find where address="192.168.44.1/24"] comment="Gateway clientes VLAN1 192.168.44.0/24"
/ip address set [find where address="192.168.221.1/24"] comment="Gateway clientes VLAN1 192.168.221.0/24"
/ip address set [find where address="192.168.175.1/24"] comment="Gateway clientes VLAN1 192.168.175.0/24"
/ip address set [find where address="192.168.212.1/24"] comment="Gateway clientes VLAN1 192.168.212.0/24"
/ip address set [find where address="192.168.213.1/24"] comment="Gateway clientes VLAN1 192.168.213.0/24"
/ip address set [find where address="192.169.22.1/24"] comment="Gateway clientes VLAN1 192.169.22.0/24"

/ip address set [find where address="192.168.30.1/24"] comment="Gateway clientes VLAN100 192.168.30.0/24"
/ip address set [find where address="192.168.31.1/24"] comment="Gateway clientes VLAN100 192.168.31.0/24"
/ip address set [find where address="192.168.255.1/22"] comment="Gateway aprovisionamiento TR-069 192.168.252.0/22"
/ip address set [find where address="192.168.250.1/24"] comment="Gateway staging e2e VLAN100 192.168.250.0/24"

/ip address set [find where address="10.20.0.1/22"] comment="Gateway gestion CPE VLAN1000 10.20.0.0/22"
/ip address set [find where address="10.20.250.1/24"] comment="Gateway staging e2e VLAN1000 10.20.250.0/24"

/ip address set [find where address="8.243.126.162/32"] comment="SNAT problematicos"

/ip address print where comment~"Gateway clientes|aprovisionamiento TR-069|staging e2e|gestion CPE"
