#!/usr/bin/env bash
# Precarga en ispadmin_staging el catálogo e2e (copia desde prod).
# Uso (desde ispadmin-backend):
#   ./scripts/sql/staging-e2e-seed-all.sh
# Requiere scripts/deploy.config.local (VPS_HOST, DEPLOY_SSH_PASSWORD).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_LOCAL="${DEPLOY_CONFIG_LOCAL:-$BACKEND_ROOT/scripts/deploy.config.local}"
CATALOG="$SCRIPT_DIR/staging-e2e-registration-catalog.sql"
RELOAD_STAGING="${RELOAD_STAGING:-1}"

[[ -f "$CATALOG" ]] || { echo "Missing $CATALOG" >&2; exit 1; }
[[ -f "$CONFIG_LOCAL" ]] || { echo "Missing $CONFIG_LOCAL" >&2; exit 1; }
# shellcheck disable=SC1090
set -a; source "$CONFIG_LOCAL"; set +a

VPS_HOST="${VPS_HOST:?VPS_HOST required}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:?DEPLOY_SSH_PASSWORD required}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8033}"
TOMCAT_CONTAINER="${DOCKER_TOMCAT_STAGING_CONTAINER:-tomcat-staging}"

ssh_vps() {
  sshpass -p "$DEPLOY_SSH_PASSWORD" ssh -o StrictHostKeyChecking=no -p "$VPS_PORT" \
    -o PreferredAuthentications=password -o PubkeyAuthentication=no \
    "${VPS_USER}@${VPS_HOST}" "$@"
}

echo "== apply $CATALOG to ispadmin_staging via $MYSQL_CONTAINER =="
ssh_vps "ROOTPW=\$(docker exec $MYSQL_CONTAINER printenv MYSQL_ROOT_PASSWORD)
docker exec -i -e MYSQL_PWD=\"\$ROOTPW\" $MYSQL_CONTAINER mysql -uroot" < "$CATALOG"

if [[ "$RELOAD_STAGING" == "1" ]]; then
  echo "== reload ispadmin-staging-acs so ACS Tr069ModelProfileRegistry picks up rows =="
  ssh_vps "docker exec $TOMCAT_CONTAINER rm -f /usr/local/tomcat/webapps/ispadmin-staging-acs.xml
docker exec $TOMCAT_CONTAINER touch /usr/local/tomcat/webapps/ispadmin-staging-acs.war"
  echo "Waiting for /ispadmin-staging-acs/ ..."
  for _ in $(seq 1 40); do
    code="$(ssh_vps "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/ispadmin-staging-acs/ 2>/dev/null || true")"
    if [[ "$code" == "200" ]]; then
      echo "ACS staging HTTP 200"
      exit 0
    fi
    sleep 3
  done
  echo "ACS staging did not return HTTP 200 after reload" >&2
  exit 1
fi
echo "SEED_OK (reload skipped)"
