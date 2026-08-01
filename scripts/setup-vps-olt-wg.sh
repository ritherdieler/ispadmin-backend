#!/usr/bin/env bash
set -euo pipefail

MK_PUBLIC_IP="${MK_PUBLIC_IP:-38.224.231.4}"
WG_PEER_IP="${WG_PEER_IP:-10.255.255.1}"
OLT_NET="${OLT_NET:-10.11.104.0/24}"
OLT_HOST="${OLT_HOST:-10.11.104.2}"
WG_NAME="${WG_NAME:-wg-olt}"
WG_CONF="${WG_CONF:-/etc/wireguard/${WG_NAME}.conf}"
GRE_NAME="${GRE_NAME:-gre-mk1}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root" >&2
  exit 1
fi

if [[ ! -f "$WG_CONF" ]]; then
  echo "Missing WireGuard config: $WG_CONF (run apply-mk2-olt-vps-wg-from-vps.sh first)" >&2
  exit 1
fi

if ! command -v wg >/dev/null 2>&1; then
  echo "Installing wireguard-tools..." >&2
  apt-get update -qq && apt-get install -y wireguard-tools
fi

echo "WireGuard peer Mikrotik: ${MK_PUBLIC_IP}"

wg_healthy() {
  ip link show "$WG_NAME" >/dev/null 2>&1 || return 1
  ping -c 1 -W 2 "$WG_PEER_IP" >/dev/null 2>&1 || return 1
  ping -c 1 -W 3 "$OLT_HOST" >/dev/null 2>&1 || return 1
  return 0
}

if wg_healthy; then
  echo "WireGuard already healthy: ${WG_NAME} -> ${WG_PEER_IP}, OLT ${OLT_HOST}"
  ip route get "$OLT_HOST" || true
  wg show "$WG_NAME" 2>/dev/null || true
  exit 0
fi

if ip link show "$GRE_NAME" >/dev/null 2>&1; then
  echo "Removing legacy GRE ${GRE_NAME}..."
  ip link set "$GRE_NAME" down 2>/dev/null || true
  ip tunnel del "$GRE_NAME" 2>/dev/null || true
  ip route del "$OLT_NET" 2>/dev/null || true
fi

wg-quick down "$WG_NAME" 2>/dev/null || true
wg-quick up "$WG_NAME"

for i in $(seq 1 15); do
  if ping -c 1 -W 2 "$OLT_HOST" >/dev/null 2>&1; then
    echo "WireGuard up: ${WG_NAME} -> ${WG_PEER_IP} (${MK_PUBLIC_IP})"
    ip route get "$OLT_HOST"
    wg show "$WG_NAME"
    exit 0
  fi
  sleep 1
done

echo "ERROR: WireGuard up but OLT ${OLT_HOST} unreachable" >&2
wg show "$WG_NAME" || true
ip route get "$OLT_HOST" || true
exit 1
