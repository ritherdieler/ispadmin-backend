#!/usr/bin/env bash
set -euo pipefail
HOST="${MIKROTIK_HOST:-38.224.231.4}"
USER="${MIKROTIK_USER:-gigafiber2023}"
PILOT_IP="${PILOT_SUBSCRIBER_IP:-192.168.30.202}"

if [[ -z "${MIKROTIK_PASSWORD:-}" ]]; then
  echo "Export MIKROTIK_PASSWORD first" >&2
  exit 1
fi

run() {
  sshpass -p "$MIKROTIK_PASSWORD" ssh -o StrictHostKeyChecking=no "${USER}@${HOST}" "$@"
}

echo "=== IP gateway piloto ==="
run '/ip address print where address~"192.168.30"'

echo "=== Stats uplink (RX debe crecer en sfp-sfpplus2) ==="
run '/interface print stats where name~"sfp-sfpplus2|vlan100"'

echo "=== Ping abonado piloto ==="
run "/ping ${PILOT_IP} count=5"

echo "=== ARP abonado piloto ==="
run "/ip arp print where address=${PILOT_IP}"

echo "=== Firewall piloto ==="
run '/ip firewall filter print where comment~"OLT piloto VLAN100"'
