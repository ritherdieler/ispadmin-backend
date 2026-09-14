#!/usr/bin/env bash
# GenieACS local para pruebas offline (VSOL / TR-069).
# Para lab en banco y piloto GigaFiber usar el GenieACS del VPS:
#   CWMP https://acs.gigafiberperu.cloud/
#   UI vía túnel: ssh -L 13000:127.0.0.1:3000 root@212.85.13.47
#   Inventario WAN: ./scripts/genieacs/probe-vsol-wan.sh
#
# Uso:
#   ./scripts/genieacs/start-genieacs-local.sh          # localhost only
#   ./scripts/genieacs/start-genieacs-local.sh --lan    # CWMP en 0.0.0.0 (ONU en red)
#   ./scripts/genieacs/start-genieacs-local.sh --stop
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE=(docker compose -f docker-compose.genieacs.yml)
LAN=0
STOP=0

for arg in "$@"; do
  case "$arg" in
    --lan) LAN=1 ;;
    --stop) STOP=1 ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *) echo "Opción desconocida: $arg" >&2; exit 1 ;;
  esac
done

if ! docker info >/dev/null 2>&1; then
  echo "Docker no está corriendo. Abre Docker Desktop e intenta de nuevo." >&2
  exit 1
fi

if [[ "$STOP" == 1 ]]; then
  "${COMPOSE[@]}" down
  echo "GenieACS local detenido."
  exit 0
fi

if [[ "$LAN" == 1 ]]; then
  COMPOSE=(docker compose -f docker-compose.genieacs.yml -f docker-compose.genieacs.local.yml)
fi

if [[ ! -f .env ]] || grep -q 'CAMBIAR_' .env 2>/dev/null; then
  MONGO_PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)"
  JWT_SECRET="$(openssl rand -hex 64)"
  ACS_PASS="$(openssl rand -base64 18 | tr -d '/+=' | head -c 24)"
  cat > .env <<EOF
MONGO_ROOT_USER=genieacs
MONGO_ROOT_PASSWORD=${MONGO_PASS}
GENIEACS_UI_JWT_SECRET=${JWT_SECRET}
GENIEACS_MONGODB_CONNECTION_URL=mongodb://genieacs:${MONGO_PASS}@mongo/genieacs?authSource=admin
CWMP_WORKER_PROCESSES=2
MAX_CONCURRENT_REQUESTS=25
ACS_CPE_USERNAME=gigafiber-acs
ACS_CPE_PASSWORD=${ACS_PASS}
EOF
  chmod 600 .env
  echo "Creado ${SCRIPT_DIR}/.env"
fi

set -a
# shellcheck source=/dev/null
source .env
set +a

"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up -d

echo "Esperando healthchecks..."
for _ in $(seq 1 40); do
  if "${COMPOSE[@]}" ps 2>/dev/null | grep -q healthy; then
    break
  fi
  sleep 3
done

"${COMPOSE[@]}" ps

python3 <<'PY'
import json, os, subprocess

user = os.environ["ACS_CPE_USERNAME"]
password = os.environ["ACS_CPE_PASSWORD"]
mongo_user = os.environ["MONGO_ROOT_USER"]
mongo_pass = os.environ["MONGO_ROOT_PASSWORD"]

js = f"""
const acsUser = {json.dumps(user)};
const acsPass = {json.dumps(password)};
db.config.replaceOne(
  {{ _id: "cwmp.auth" }},
  {{ _id: "cwmp.auth", value: "true" }},
  {{ upsert: true }}
);
db.config.replaceOne(
  {{ _id: "cwmp.deviceOnlineThreshold" }},
  {{ _id: "cwmp.deviceOnlineThreshold", value: "3600" }},
  {{ upsert: true }}
);
print("cwmp.auth=true (no HTTP Digest); CR user " + acsUser);
"""

subprocess.run(
    [
        "docker", "compose", "-f", "docker-compose.genieacs.yml",
        "exec", "-T", "mongo", "mongosh",
        "-u", mongo_user, "-p", mongo_pass,
        "--authenticationDatabase", "admin", "genieacs", "--quiet",
    ],
    input=js,
    text=True,
    check=True,
    cwd=os.getcwd(),
)
PY

"${COMPOSE[@]}" restart genieacs
sleep 6

HOST_IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo 'TU_IP_LAN')"

echo ""
echo "=== GenieACS local listo ==="
echo "UI admin:     http://127.0.0.1:3000   (esta imagen Docker; :7567 devuelve 404)"
echo "NBI REST:     http://127.0.0.1:7557"
echo "CWMP (CPE):   http://127.0.0.1:7547"
if [[ "$LAN" == 1 ]]; then
  echo "CWMP LAN:     http://${HOST_IP}:7547   (configura esto en la VSOL)"
  echo "UI LAN:       http://${HOST_IP}:3000"
fi
echo "CPE user:     ${ACS_CPE_USERNAME}"
echo "CPE password: ${ACS_CPE_PASSWORD}  (también en .env)"
echo ""
echo "Detener: ./scripts/genieacs/start-genieacs-local.sh --stop"
