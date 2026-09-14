# Pools GenieACS / aprovisionamiento TR-069 en MK2.
# Idempotente. Comment unificado en gateways de ip_pool (+ lab 192.168.123).

:local c "Pool de aprovisionamiento GenieACS TR-069"

:if ([:len [/ip address find where address="192.168.212.1/24"]] = 0) do={
  /ip address add address=192.168.212.1/24 interface=LAN-VLAN1 comment=$c
} else={
  /ip address set [find where address="192.168.212.1/24"] comment=$c
}

:if ([:len [/ip address find where address="192.168.213.1/24"]] = 0) do={
  /ip address add address=192.168.213.1/24 interface=LAN-VLAN1 comment=$c
} else={
  /ip address set [find where address="192.168.213.1/24"] comment=$c
}

:if ([:len [/ip address find where address="192.169.22.1/24"]] = 0) do={
  /ip address add address=192.169.22.1/24 interface=LAN-VLAN1 comment=$c
} else={
  /ip address set [find where address="192.169.22.1/24"] comment=$c
}

/ip address set [find where address="192.168.123.1/24"] comment=$c
/ip address set [find where address="192.168.0.1/24"] comment=$c
/ip address set [find where address="192.168.9.1/24"] comment=$c
/ip address set [find where address="192.168.20.1/24"] comment=$c
/ip address set [find where address="192.168.22.1/24"] comment=$c
/ip address set [find where address="192.168.25.1/24"] comment=$c
/ip address set [find where address="192.168.26.1/24"] comment=$c
/ip address set [find where address="192.168.30.1/24"] comment=$c
/ip address set [find where address="192.168.33.1/24"] comment=$c
/ip address set [find where address="192.168.49.1/24"] comment=$c
/ip address set [find where address="192.168.88.1/24"] comment=$c
/ip address set [find where address="192.168.93.1/24"] comment=$c
/ip address set [find where address="192.168.95.1/24"] comment=$c
/ip address set [find where address="192.168.200.1/24"] comment=$c
/ip address set [find where address="192.168.210.1/24"] comment=$c
/ip address set [find where address="192.168.211.1/24"] comment=$c
/ip address set [find where address="192.168.220.1/24"] comment=$c
/ip address set [find where address="192.168.221.1/24"] comment=$c
