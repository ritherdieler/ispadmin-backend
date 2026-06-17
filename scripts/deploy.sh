#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_LOCAL="$SCRIPT_DIR/deploy.config.local"
CONFIG_EXAMPLE="$SCRIPT_DIR/deploy.config.example"

MODE="deploy"
SKIP_BUILD=0

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy.sh [--setup|--full|--war-only|--deploy]

  --setup     Upload DJL libs, patch Docker image/compose, rebuild Tomcat (once)
  --full      --setup then deploy WAR
  --war-only  Deploy existing target/ispadmin.war only
  --deploy    Build, verify, deploy WAR (default)

Environment:
  DEPLOY_SSH_PASSWORD   Optional; if omitted and no SSH key works, password is prompted once

Config:
  scripts/deploy.config.local (copy from deploy.config.example)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --setup) MODE="setup"; shift ;;
    --full) MODE="full"; shift ;;
    --war-only) MODE="war-only"; SKIP_BUILD=1; shift ;;
    --deploy) MODE="deploy"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -f "$CONFIG_LOCAL" ]]; then
  _ENV_DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:-}"
  # shellcheck source=/dev/null
  source "$CONFIG_LOCAL"
  if [[ -z "${DEPLOY_SSH_PASSWORD:-}" && -n "$_ENV_DEPLOY_SSH_PASSWORD" ]]; then
    DEPLOY_SSH_PASSWORD="$_ENV_DEPLOY_SSH_PASSWORD"
  fi
elif [[ -f "$CONFIG_EXAMPLE" ]]; then
  # shellcheck source=/dev/null
  source "$CONFIG_EXAMPLE"
else
  echo "Missing deploy config. Copy scripts/deploy.config.example to scripts/deploy.config.local" >&2
  exit 1
fi

VPS_HOST="${VPS_HOST:-}"
VPS_USER="${VPS_USER:-root}"
VPS_PORT="${VPS_PORT:-22}"
DOCKER_COMPOSE_DIR="${DOCKER_COMPOSE_DIR:-/opt/gigafiber}"
DOCKER_TOMCAT_CONTAINER="${DOCKER_TOMCAT_CONTAINER:-tomcat9027}"
DOCKER_TOMCAT_LIB_HOST_DIR="${DOCKER_TOMCAT_LIB_HOST_DIR:-/opt/gigafiber/tomcat/lib}"
DOCKER_TOMCAT_DOCKERFILE="${DOCKER_TOMCAT_DOCKERFILE:-/opt/gigafiber/tomcat/Dockerfile}"
DOCKER_COMPOSE_FILE="${DOCKER_COMPOSE_FILE:-/opt/gigafiber/docker-compose.yml}"
CATALINA_HOME="${CATALINA_HOME:-/usr/local/tomcat}"
WAR_NAME="${WAR_NAME:-ispadmin.war}"
SSH_IDENTITY_FILE="${SSH_IDENTITY_FILE:-}"
DEPLOY_SSH_PASSWORD="${DEPLOY_SSH_PASSWORD:-}"

if [[ -z "$VPS_HOST" ]]; then
  echo "VPS_HOST is required in deploy.config.local" >&2
  exit 1
fi

SSH_TARGET="${VPS_USER}@${VPS_HOST}"
WAR_PATH="$PROJECT_DIR/target/$WAR_NAME"
TOMCAT_LIB_SRC="$PROJECT_DIR/target/tomcat-lib"
CATALINA_OPTS_VALUE='-DPYTORCH_VERSION=2.7.1 -DPYTORCH_FLAVOR=cpu -Dai.djl.pytorch.native_helper=com.dscorp.wispadmin.wispadmin.util.PytorchNativeHelper'
TOMCAT_BASE_IMAGE="${TOMCAT_BASE_IMAGE:-tomcat:9.0-jdk11-temurin-jammy}"

SSH_CONTROL_DIR=""
SSH_CONTROL_PATH=""

cleanup_ssh() {
  if [[ -n "$SSH_CONTROL_DIR" ]]; then
    if [[ -n "$SSH_IDENTITY_FILE" ]]; then
      ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -i "$SSH_IDENTITY_FILE" \
        -o "ControlPath=$SSH_CONTROL_PATH" -o ControlMaster=no \
        -O exit "$SSH_TARGET" 2>/dev/null || true
    else
      ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new \
        -o "ControlPath=$SSH_CONTROL_PATH" -o ControlMaster=no \
        -O exit "$SSH_TARGET" 2>/dev/null || true
    fi
    rm -rf "$SSH_CONTROL_DIR"
  fi
}
trap cleanup_ssh EXIT

init_ssh() {
  SSH_CONTROL_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ispadmin-deploy-ssh.XXXXXX")"
  SSH_CONTROL_PATH="$SSH_CONTROL_DIR/cm"

  local probe=(ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes -o ConnectTimeout=10)
  [[ -n "$SSH_IDENTITY_FILE" ]] && probe+=(-i "$SSH_IDENTITY_FILE")

  if "${probe[@]}" "$SSH_TARGET" true 2>/dev/null; then
    echo "SSH: using key authentication"
    if [[ -n "$SSH_IDENTITY_FILE" ]]; then
      ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -i "$SSH_IDENTITY_FILE" \
        -o "ControlPath=$SSH_CONTROL_PATH" -o ControlMaster=yes -o ControlPersist=600 \
        -fN "$SSH_TARGET"
    else
      ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new \
        -o "ControlPath=$SSH_CONTROL_PATH" -o ControlMaster=yes -o ControlPersist=600 \
        -fN "$SSH_TARGET"
    fi
    return 0
  fi

  if [[ -z "$DEPLOY_SSH_PASSWORD" ]]; then
    read -rs -p "SSH password for $SSH_TARGET: " DEPLOY_SSH_PASSWORD
    echo
  fi

  if ! command -v sshpass >/dev/null 2>&1; then
    echo "Password auth requires sshpass (brew install hudochenkov/sshpass/sshpass) or configure SSH key" >&2
    exit 1
  fi

  echo "SSH: opening shared session (password asked once)"
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  if [[ -n "$SSH_IDENTITY_FILE" ]]; then
    sshpass -e ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -i "$SSH_IDENTITY_FILE" \
      -o "ControlPath=$SSH_CONTROL_PATH" -o ControlMaster=yes -o ControlPersist=600 \
      -fN "$SSH_TARGET"
  else
    sshpass -e ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new \
      -o "ControlPath=$SSH_CONTROL_PATH" -o ControlMaster=yes -o ControlPersist=600 \
      -fN "$SSH_TARGET"
  fi
}

SSH_BASE=(ssh -p "$VPS_PORT" -o StrictHostKeyChecking=accept-new -o ControlMaster=no)
SCP_BASE=(scp -P "$VPS_PORT" -o StrictHostKeyChecking=accept-new -o ControlMaster=no)
if [[ -n "$SSH_IDENTITY_FILE" ]]; then
  SSH_BASE+=(-i "$SSH_IDENTITY_FILE")
  SCP_BASE+=(-i "$SSH_IDENTITY_FILE")
fi

run_ssh() {
  "${SSH_BASE[@]}" -o "ControlPath=$SSH_CONTROL_PATH" "$SSH_TARGET" "$@"
}

run_scp() {
  "${SCP_BASE[@]}" -o "ControlPath=$SSH_CONTROL_PATH" "$@"
}

build_war() {
  local models_dir="$PROJECT_DIR/src/main/resources/models"
  for model in face_feature.zip ultranet.zip; do
    if [[ ! -f "$models_dir/$model" ]]; then
      echo "Missing $models_dir/$model — required for facial recognition." >&2
      echo "Restore from git: git checkout a4c8d3c -- src/main/resources/models/face_feature.zip" >&2
      exit 1
    fi
  done
  echo "Building WAR for Linux x86_64..."
  (cd "$PROJECT_DIR" && bash mvnw clean package -DskipTests -Ddjl.linux)
  bash "$SCRIPT_DIR/verify-djl-war.sh"
}

upload_tomcat_lib() {
  if [[ ! -d "$TOMCAT_LIB_SRC" ]]; then
    echo "Missing $TOMCAT_LIB_SRC. Run build first." >&2
    exit 1
  fi
  echo "Uploading DJL jars to $DOCKER_TOMCAT_LIB_HOST_DIR ..."
  run_ssh "mkdir -p '$DOCKER_TOMCAT_LIB_HOST_DIR'"
  run_scp "$TOMCAT_LIB_SRC"/*.jar "$SSH_TARGET:$DOCKER_TOMCAT_LIB_HOST_DIR/"
}

upload_dockerfile() {
  local template="$SCRIPT_DIR/docker/tomcat.Dockerfile"
  if [[ ! -f "$template" ]]; then
    echo "Missing $template" >&2
    exit 1
  fi
  echo "Uploading Tomcat Dockerfile..."
  run_ssh "mkdir -p '$(dirname "$DOCKER_TOMCAT_DOCKERFILE")'"
  run_scp "$template" "$SSH_TARGET:$DOCKER_TOMCAT_DOCKERFILE"
}

patch_remote_docker_files() {
  echo "Updating docker-compose on VPS..."
  upload_dockerfile
  run_ssh "bash -s" <<EOF
set -euo pipefail
COMPOSE='$DOCKER_COMPOSE_FILE'
LIB_DIR='$DOCKER_TOMCAT_LIB_HOST_DIR'

if [[ ! -d "\$LIB_DIR" ]] || [[ -z "\$(find "\$LIB_DIR" -maxdepth 1 -name '*.jar' -print -quit)" ]]; then
  echo "No jars in \$LIB_DIR" >&2
  exit 1
fi

if [[ ! -f "\$COMPOSE" ]]; then
  echo "docker-compose not found: \$COMPOSE" >&2
  exit 1
fi

cp "\$COMPOSE" "\${COMPOSE}.bak.\$(date +%Y%m%d%H%M%S)"

python3 - <<PY
from pathlib import Path

compose_path = Path("$DOCKER_COMPOSE_FILE")
text = compose_path.read_text()
catalina_opts = """$CATALINA_OPTS_VALUE"""

if "CATALINA_OPTS:" in text or "CATALINA_OPTS=" in text:
    print("docker-compose already contains CATALINA_OPTS")
else:
    lines = text.splitlines()
    out = []
    in_tomcat = False
    in_environment = False
    inserted = False
    for line in lines:
        if line.rstrip() == "  tomcat:":
            in_tomcat = True
            in_environment = False
        elif in_tomcat and line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(":"):
            in_tomcat = False
            in_environment = False
        if in_tomcat and line.strip() == "environment:":
            in_environment = True
        out.append(line)
        if in_tomcat and in_environment and not inserted and line.startswith("      SPRING_"):
            out.append(f'      CATALINA_OPTS: "{catalina_opts}"')
            inserted = True
    if not inserted:
        raise SystemExit("Could not insert CATALINA_OPTS under tomcat.environment")
    compose_path.write_text("\\n".join(out) + "\\n")
    print("Added CATALINA_OPTS to docker-compose.yml")
PY
EOF
}

rebuild_tomcat_container() {
  echo "Rebuilding Tomcat container..."
  run_ssh "cd '$DOCKER_COMPOSE_DIR' && docker compose build tomcat && docker compose up -d tomcat"
}

wait_for_tomcat() {
  echo "Waiting for Tomcat to start..."
  local i
  for i in $(seq 1 60); do
    if run_ssh "curl -sf -o /dev/null http://127.0.0.1:8080/ 2>/dev/null"; then
      echo "Tomcat is responding on port 8080"
      return 0
    fi
    sleep 5
  done
  echo "Tomcat did not become ready in time" >&2
  return 1
}

wait_for_app() {
  echo "Waiting for $WAR_NAME to deploy at /ispadmin/ ..."
  local i code
  for i in $(seq 1 60); do
    code="$(run_ssh "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/ispadmin/ 2>/dev/null || true")"
    if [[ "$code" == "200" || "$code" == "302" ]]; then
      echo "Application is responding (HTTP $code)"
      return 0
    fi
    sleep 5
  done
  echo "Application did not become ready in time (last HTTP $code)" >&2
  return 1
}

deploy_war() {
  if [[ ! -f "$WAR_PATH" ]]; then
    echo "Missing $WAR_PATH" >&2
    exit 1
  fi
  local war_bytes
  war_bytes="$(wc -c < "$WAR_PATH" | tr -d ' ')"
  echo "Deploying $WAR_NAME (${war_bytes} bytes) to container $DOCKER_TOMCAT_CONTAINER ..."
  run_scp "$WAR_PATH" "$SSH_TARGET:/tmp/$WAR_NAME"
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
WAR='$WAR_NAME'
EXPECTED_BYTES='$war_bytes'

remote_bytes="\$(wc -c < "/tmp/\$WAR" | tr -d ' ')"
if [[ "\$remote_bytes" != "\$EXPECTED_BYTES" ]]; then
  echo "Remote WAR size mismatch: expected \$EXPECTED_BYTES got \$remote_bytes" >&2
  exit 1
fi

docker exec "\$CONTAINER" sh -c "rm -rf \$CATALINA/webapps/ispadmin \$CATALINA/webapps/\$WAR"
docker cp "/tmp/\$WAR" "\$CONTAINER:\$CATALINA/webapps/\$WAR"

container_bytes="\$(docker exec "\$CONTAINER" sh -c "wc -c < \$CATALINA/webapps/\$WAR" | tr -d ' ')"
if [[ "\$container_bytes" != "\$EXPECTED_BYTES" ]]; then
  echo "Container WAR size mismatch: expected \$EXPECTED_BYTES got \$container_bytes" >&2
  exit 1
fi
EOF
}

verify_djl_logs() {
  echo "Checking DJL startup in container logs..."
  local i
  for i in $(seq 1 12); do
    if run_ssh "docker logs '$DOCKER_TOMCAT_CONTAINER' 2>&1 | grep -q 'Motor facial DJL listo'"; then
      echo "DJL face engine ready"
      return 0
    fi
    sleep 5
  done
  run_ssh "docker logs '$DOCKER_TOMCAT_CONTAINER' 2>&1 | tail -200" || true
  echo "WARNING: DJL ready message not found in logs yet" >&2
  return 1
}

verify_http() {
  local code
  code="$(run_ssh "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/ispadmin/ || true")"
  echo "GET /ispadmin/ -> HTTP $code"
  [[ "$code" == "200" || "$code" == "302" ]]
}

setup_djl() {
  init_ssh
  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    build_war
  fi
  upload_tomcat_lib
  patch_remote_docker_files
  rebuild_tomcat_container
  wait_for_tomcat
  verify_djl_logs || true
}

case "$MODE" in
  setup)
    setup_djl
    echo "Setup complete. Redeploy WAR with: ./scripts/deploy.sh --war-only"
    ;;
  full)
    setup_djl
    deploy_war
    wait_for_app
    verify_djl_logs || true
    verify_http || true
    ;;
  war-only)
    init_ssh
    deploy_war
    wait_for_app
    verify_http || true
    ;;
  deploy)
    build_war
    init_ssh
    deploy_war
    wait_for_app
    verify_http || true
    ;;
esac

echo "Done."
