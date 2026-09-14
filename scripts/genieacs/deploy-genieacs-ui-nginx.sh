#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_LOCAL="$BACKEND_DIR/scripts/deploy.config.local"

VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
OPEN_UFW="${OPEN_UFW:-1}"

if [[ -f "$CONFIG_LOCAL" ]]; then
  # shellcheck source=/dev/null
  source "$CONFIG_LOCAL"
fi

VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:-}"
SSH_TARGET="${VPS_USER}@${VPS_HOST}"

SSH_BASE=(ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes)
SCP_BASE=(scp -P "$VPS_PORT" -o StrictHostKeyChecking=accept-new)

init_ssh() {
  if "${SSH_BASE[@]}" "$SSH_TARGET" true 2>/dev/null; then
    SSH_BASE=(ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new)
    SCP_BASE=(scp -P "$VPS_PORT" -o StrictHostKeyChecking=accept-new)
    return 0
  fi

  if [[ -z "$DEPLOY_SSH_PASSWORD" ]]; then
    echo "Falta DEPLOY_SSH_PASSWORD o clave SSH para $SSH_TARGET" >&2
    exit 1
  fi

  if ! command -v sshpass >/dev/null 2>&1; then
    echo "Instalar sshpass: brew install hudochenkov/sshpass/sshpass" >&2
    exit 1
  fi

  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  SSH_BASE=(sshpass -e ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new)
  SCP_BASE=(sshpass -e scp -P "$VPS_PORT" -o StrictHostKeyChecking=accept-new)
}

remote() {
  "${SSH_BASE[@]}" "$SSH_TARGET" "$@"
}

copy_to_remote() {
  "${SCP_BASE[@]}" "$1" "${SSH_TARGET}:$2"
}

init_ssh

echo "==> Publicando vhost UI GenieACS (:8443, IPs Mikrotik)"
copy_to_remote "$SCRIPT_DIR/nginx/genieacs-ui-on-acs.conf.example" "/etc/nginx/sites-available/genieacs-ui-on-acs.conf"
remote "ln -sf /etc/nginx/sites-available/genieacs-ui-on-acs.conf /etc/nginx/sites-enabled/genieacs-ui-on-acs.conf && nginx -t && systemctl reload nginx"

if [[ "$OPEN_UFW" == "1" ]]; then
  echo "==> Abriendo UFW 8443/tcp (nginx filtra por IP Mikrotik)"
  remote "ufw allow 8443/tcp comment 'GenieACS UI (nginx IP filter)' >/dev/null 2>&1 || true"
fi

echo "==> Smoke local en VPS"
remote "curl -sk -o /dev/null -w '%{http_code}\n' https://127.0.0.1:8443/ --resolve acs.gigafiberperu.cloud:8443:127.0.0.1 || true"

echo ""
echo "Panel GenieACS UI publico (solo IPs Mikrotik):"
echo "  https://acs.gigafiberperu.cloud:8443/"
echo "IPs permitidas: 38.224.231.2 (MK1), 38.224.231.4 (MK2)"
