#!/usr/bin/env bash
# Rutas ip_pool abonados por wg-olt (VPS ↔ MK2). Lo invoca PostUp/PostDown de wg-olt.conf.
IFACE="${1:-wg-olt}"
ACTION="${2:-up}"
CIDRS="192.168.0.0/24 192.168.9.0/24 192.168.20.0/24 192.168.22.0/24 192.168.25.0/24 192.168.26.0/24 192.168.30.0/24 192.168.33.0/24 192.168.49.0/24 192.168.88.0/24 192.168.93.0/24 192.168.95.0/24 192.168.123.0/24 192.168.200.0/24 192.168.210.0/24 192.168.211.0/24 192.168.212.0/24 192.168.213.0/24 192.168.220.0/24 192.168.221.0/24 192.168.250.0/24 192.168.252.0/22 192.169.22.0/24 10.20.0.0/22 10.20.250.0/24"
if [[ "$ACTION" == "up" ]]; then
  for c in $CIDRS; do ip route replace "$c" dev "$IFACE"; done
  ip route replace 10.11.104.0/24 dev "$IFACE" 2>/dev/null || true
else
  for c in $CIDRS; do ip route del "$c" dev "$IFACE" 2>/dev/null || true; done
fi
