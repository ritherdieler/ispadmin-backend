#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_LOCAL="$BACKEND_DIR/scripts/deploy.config.local"

VPS_HOST="${VPS_HOST:-212.85.13.47}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
REMOTE_DIR="${REMOTE_DIR:-/opt/gigafiber/genieacs}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-admin@gigafiberperu.cloud}"
SETUP_NGINX="${SETUP_NGINX:-1}"
SETUP_SWAP="${SETUP_SWAP:-1}"

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

echo "==> Test local compose piloto"
bash "$SCRIPT_DIR/test-genieacs-compose.sh"

init_ssh

echo "==> Creando directorio remoto ${REMOTE_DIR}"
remote "mkdir -p ${REMOTE_DIR}/backup"

echo "==> Copiando stack Docker aislado"
copy_to_remote "$SCRIPT_DIR/docker-compose.genieacs.yml" "${REMOTE_DIR}/docker-compose.yml"
copy_to_remote "$SCRIPT_DIR/.env.example" "${REMOTE_DIR}/.env.example"
copy_to_remote "$SCRIPT_DIR/verify-genieacs-pilot.sh" "${REMOTE_DIR}/verify-genieacs-pilot.sh"
remote "chmod +x ${REMOTE_DIR}/verify-genieacs-pilot.sh"

echo "==> Generando .env piloto (secretos nuevos si no existe)"
remote "bash -s" <<REMOTE
set -euo pipefail
cd ${REMOTE_DIR}
if [[ ! -f .env ]] || grep -q 'CAMBIAR_' .env 2>/dev/null; then
  MONGO_PASS=\$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
  JWT_SECRET=\$(openssl rand -hex 64)
  cat > .env <<EOF
MONGO_ROOT_USER=genieacs
MONGO_ROOT_PASSWORD=\${MONGO_PASS}
GENIEACS_UI_JWT_SECRET=\${JWT_SECRET}
GENIEACS_MONGODB_CONNECTION_URL=mongodb://genieacs:\${MONGO_PASS}@mongo/genieacs?authSource=admin
CWMP_WORKER_PROCESSES=2
MAX_CONCURRENT_REQUESTS=25
EOF
  chmod 600 .env
fi
REMOTE

if [[ "$SETUP_SWAP" == "1" ]]; then
  echo "==> Verificando swap (2 GB si falta)"
  remote "bash -s" <<'REMOTE'
set -euo pipefail
if swapon --show | grep -q .; then
  echo "swap ya configurado"
  exit 0
fi
if [[ ! -f /swapfile ]]; then
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
fi
swapon /swapfile || true
grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
swapon --show
REMOTE
fi

echo "==> Levantando stack GenieACS aislado"
remote "cd ${REMOTE_DIR} && docker compose pull && docker compose up -d"

echo "==> Esperando healthchecks"
remote "bash -s" <<REMOTE
set -euo pipefail
cd ${REMOTE_DIR}
for i in \$(seq 1 30); do
  if docker compose ps --format json 2>/dev/null | grep -q '"Health":"healthy"'; then
    docker compose ps
    exit 0
  fi
  if docker compose ps | grep -q 'healthy'; then
    docker compose ps
    exit 0
  fi
  sleep 5
done
docker compose ps
docker compose logs --tail=40 genieacs || true
REMOTE

if [[ "$SETUP_NGINX" == "1" ]]; then
  echo "==> Configurando nginx + TLS para acs.gigafiberperu.cloud"
  copy_to_remote "$SCRIPT_DIR/nginx/acs.gigafiberperu.cloud.conf.example" "/etc/nginx/sites-available/acs.gigafiberperu.cloud.conf"
  remote "ln -sf /etc/nginx/sites-available/acs.gigafiberperu.cloud.conf /etc/nginx/sites-enabled/acs.gigafiberperu.cloud.conf && nginx -t && systemctl reload nginx"
  remote "certbot --nginx -d acs.gigafiberperu.cloud --non-interactive --agree-tos --email ${CERTBOT_EMAIL} --redirect || certbot --nginx -d acs.gigafiberperu.cloud --non-interactive --agree-tos --register-unsafely-without-email --redirect"
  remote "nginx -t && systemctl reload nginx"
fi

echo "==> Verificacion piloto en VPS"
remote "REMOTE_DIR=${REMOTE_DIR} bash ${REMOTE_DIR}/verify-genieacs-pilot.sh"

if [[ "${SETUP_UI_NGINX:-1}" == "1" ]]; then
  echo "==> Publicando panel UI GenieACS (nginx :8443, IPs Mikrotik)"
  bash "$SCRIPT_DIR/deploy-genieacs-ui-nginx.sh"
fi

echo ""
echo "Piloto GenieACS desplegado (stack aislado en ${REMOTE_DIR})"
echo "UI admin (IPs Mikrotik): https://acs.gigafiberperu.cloud:8443/"
echo "UI admin (tunel SSH alternativo): ssh -L 13000:127.0.0.1:3000 ${SSH_TARGET}  # http://127.0.0.1:13000"
echo "NBI backend (solo localhost VPS): http://127.0.0.1:7557"
echo "CWMP publico: https://acs.gigafiberperu.cloud/"
