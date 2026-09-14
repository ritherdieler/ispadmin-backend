#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_LOCAL="$SCRIPT_DIR/deploy.config.local"
CONFIG_EXAMPLE="$SCRIPT_DIR/deploy.config.example"

if [[ -f "$CONFIG_LOCAL" ]]; then
  # shellcheck source=/dev/null
  source "$CONFIG_LOCAL"
elif [[ -f "$CONFIG_EXAMPLE" ]]; then
  # shellcheck source=/dev/null
  source "$CONFIG_EXAMPLE"
fi

VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
LOCAL_PORT="${LOCAL_PORT:-13306}"
REMOTE_MYSQL_PORT="${REMOTE_MYSQL_PORT:-3306}"
DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:-}"

usage() {
  cat <<EOF
Uso: ./scripts/db-tunnel.sh [start|stop|status]

Abre un tunel SSH para conectar a MySQL del VPS desde tu maquina local.

  Host local:  127.0.0.1
  Puerto local: $LOCAL_PORT
  Usuario MySQL: root
  Base de datos: ispadmin

Ejemplo DBeaver / MySQL Workbench:
  Host: 127.0.0.1
  Port: $LOCAL_PORT
  User: root
  Database: ispadmin

O desde terminal:
  mysql -h 127.0.0.1 -P $LOCAL_PORT -u root -p ispadmin

Variables opcionales:
  LOCAL_PORT=13306  Puerto local del tunel
EOF
}

ssh_cmd() {
  if [[ -n "$DEPLOY_SSH_PASSWORD" ]] && command -v sshpass >/dev/null 2>&1; then
    SSHPASS="$DEPLOY_SSH_PASSWORD" sshpass -e ssh -o StrictHostKeyChecking=no -p "$VPS_PORT" "$@"
  else
    ssh -o StrictHostKeyChecking=no -p "$VPS_PORT" "$@"
  fi
}

is_running() {
  lsof -iTCP:"$LOCAL_PORT" -sTCP:LISTEN -n -P >/dev/null 2>&1
}

start_tunnel() {
  if is_running; then
    echo "Tunel ya activo en 127.0.0.1:$LOCAL_PORT"
    return 0
  fi

  echo "Abriendo tunel SSH -> ${VPS_USER}@${VPS_HOST}:${REMOTE_MYSQL_PORT} en localhost:$LOCAL_PORT"
  if [[ -n "$DEPLOY_SSH_PASSWORD" ]] && command -v sshpass >/dev/null 2>&1; then
    SSHPASS="$DEPLOY_SSH_PASSWORD" sshpass -e ssh -f -N \
      -o StrictHostKeyChecking=no \
      -o ExitOnForwardFailure=yes \
      -p "$VPS_PORT" \
      -L "${LOCAL_PORT}:127.0.0.1:${REMOTE_MYSQL_PORT}" \
      "${VPS_USER}@${VPS_HOST}"
  else
    ssh -f -N \
      -o StrictHostKeyChecking=no \
      -o ExitOnForwardFailure=yes \
      -p "$VPS_PORT" \
      -L "${LOCAL_PORT}:127.0.0.1:${REMOTE_MYSQL_PORT}" \
      "${VPS_USER}@${VPS_HOST}"
  fi

  sleep 1
  if is_running; then
    echo "Tunel activo. Conecta a 127.0.0.1:$LOCAL_PORT"
  else
    echo "No se pudo abrir el tunel." >&2
    exit 1
  fi
}

stop_tunnel() {
  local pids
  pids="$(lsof -tiTCP:"$LOCAL_PORT" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -z "$pids" ]]; then
    echo "No hay tunel activo en el puerto $LOCAL_PORT"
    return 0
  fi
  kill $pids
  echo "Tunel cerrado."
}

status_tunnel() {
  if is_running; then
    echo "Tunel activo en 127.0.0.1:$LOCAL_PORT"
    lsof -iTCP:"$LOCAL_PORT" -sTCP:LISTEN -n -P
  else
    echo "Tunel inactivo."
  fi
}

case "${1:-start}" in
  start) start_tunnel ;;
  stop) stop_tunnel ;;
  status) status_tunnel ;;
  -h|--help|help) usage ;;
  *) echo "Comando desconocido: $1" >&2; usage; exit 1 ;;
esac
