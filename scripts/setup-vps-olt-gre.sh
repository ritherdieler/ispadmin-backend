#!/usr/bin/env bash
set -euo pipefail

VPS_PUBLIC_IP="${VPS_PUBLIC_IP:-212.85.13.47}"
MK_PUBLIC_IP="${MK_PUBLIC_IP:-${GRE_MK_PUBLIC_IP:-38.224.231.4}}"
GRE_LOCAL_IP="${GRE_LOCAL_IP:-10.255.255.2}"
GRE_PEER_IP="${GRE_PEER_IP:-10.255.255.1}"
OLT_NET="${OLT_NET:-10.11.104.0/24}"
OLT_HOST="${OLT_HOST:-10.11.104.2}"
GRE_NAME="${GRE_NAME:-gre-mk1}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root" >&2
  exit 1
fi

echo "GRE peer Mikrotik: ${MK_PUBLIC_IP} (set MK_PUBLIC_IP or GRE_MK_PUBLIC_IP for MK1=38.224.231.2)"

modprobe gre 2>/dev/null || true

gre_healthy() {
  ip link show "$GRE_NAME" >/dev/null 2>&1 || return 1
  ip -4 addr show dev "$GRE_NAME" | grep -q "${GRE_LOCAL_IP}/30" || return 1
  ip route get "$OLT_HOST" 2>/dev/null | grep -q "dev ${GRE_NAME}" || return 1
  ping -c 1 -W 2 "$GRE_PEER_IP" >/dev/null 2>&1 || return 1
  ping -c 1 -W 3 "$OLT_HOST" >/dev/null 2>&1 || return 1
  return 0
}

if gre_healthy; then
  echo "GRE already healthy: ${GRE_NAME} ${GRE_LOCAL_IP}/30 -> ${GRE_PEER_IP}"
  ip route get "$OLT_HOST"
  exit 0
fi

if ip link show "$GRE_NAME" >/dev/null 2>&1; then
  ip link set "$GRE_NAME" down || true
  ip tunnel del "$GRE_NAME" || true
fi

ip tunnel add "$GRE_NAME" mode gre remote "$MK_PUBLIC_IP" local "$VPS_PUBLIC_IP" ttl 255
ip addr flush dev "$GRE_NAME" 2>/dev/null || true
ip addr add "${GRE_LOCAL_IP}/30" dev "$GRE_NAME"
ip link set "$GRE_NAME" up
ip route replace "$OLT_NET" via "$GRE_PEER_IP" dev "$GRE_NAME"

for i in $(seq 1 10); do
  if ping -c 1 -W 2 "$OLT_HOST" >/dev/null 2>&1; then
    echo "GRE up: ${GRE_NAME} ${GRE_LOCAL_IP}/30 -> ${GRE_PEER_IP} (${MK_PUBLIC_IP})"
    ip addr show "$GRE_NAME"
    ip route get "$OLT_HOST"
    exit 0
  fi
  sleep 1
done

echo "ERROR: GRE created but OLT ${OLT_HOST} unreachable" >&2
ip addr show "$GRE_NAME" || true
ip route get "$OLT_HOST" || true
exit 1
