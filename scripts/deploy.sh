#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_LOCAL="$SCRIPT_DIR/deploy.config.local"
CONFIG_EXAMPLE="$SCRIPT_DIR/deploy.config.example"

MODE="deploy"
SKIP_BUILD=0
DEPLOY_ENV=""
WITH_SUBSYSTEMS=""

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy.sh [--setup|--full|--war-only|--deploy] [--env prod|staging] [--with key,key]

  --setup     Upload DJL libs, patch Docker image/compose, rebuild Tomcat (once)
  --full      --setup then deploy WAR
  --war-only  Deploy existing target WAR only
  --deploy    Build, verify, deploy WAR (default)
  --env       prod (default): ispadmin.war → /ispadmin
              staging: ispadmin-staging.war → /ispadmin-staging (does not touch ispadmin.war)
  --with      Optional subsystems to keep in the staging WAR (observability,oltgateway,netdiag,traffic,servicehealth).
              Default staging: none.

All deploy modes run the complete Maven test suite before building or connecting to the VPS.
Any failing test aborts the deployment.

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
    --env)
      DEPLOY_ENV="${2:-}"
      if [[ "$DEPLOY_ENV" != "prod" && "$DEPLOY_ENV" != "staging" ]]; then
        echo "Invalid --env (use prod or staging)" >&2
        usage
        exit 1
      fi
      shift 2
      ;;
    --with)
      WITH_SUBSYSTEMS="${2:-}"
      shift 2
      ;;
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
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/opt/gigafiber/.env}"
OBS_BASE_URL="${OBS_BASE_URL:-}"
OBS_API_KEY="${OBS_API_KEY:-}"

DEPLOY_ENV="${DEPLOY_ENV:-prod}"
if [[ "$DEPLOY_ENV" == "staging" ]]; then
  WAR_NAME="ispadmin-staging.war"
  APP_CONTEXT_PATH="/ispadmin-staging"
  MAVEN_WAR_PROFILE="staging-war"
else
  APP_CONTEXT_PATH="/ispadmin"
  MAVEN_WAR_PROFILE="prod-war"
fi

if [[ -z "$VPS_HOST" ]]; then
  echo "VPS_HOST is required in deploy.config.local" >&2
  exit 1
fi

SSH_TARGET="${VPS_USER}@${VPS_HOST}"
WAR_PATH="$PROJECT_DIR/target/$WAR_NAME"
TOMCAT_LIB_SRC="$PROJECT_DIR/target/tomcat-lib"
CATALINA_OPTS_VALUE='-Duser.timezone=America/Lima -DPYTORCH_VERSION=2.7.1 -DPYTORCH_FLAVOR=cpu -Dai.djl.pytorch.native_helper=com.dscorp.wispadmin.wispadmin.util.PytorchNativeHelper'
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

# WAR (~250 MB, already compressed): rsync -W skips delta, no -z (recompressing a zip is slower).
# Destination is DOCKER_COMPOSE_DIR — /tmp is not writable and scp cannot resume.
run_rsync() {
  if ! command -v rsync >/dev/null 2>&1; then
    echo "rsync is required to upload the WAR (brew install rsync)" >&2
    exit 1
  fi
  local rsh="ssh -p $VPS_PORT -o StrictHostKeyChecking=accept-new -o ControlMaster=no -o ControlPath=$SSH_CONTROL_PATH"
  if [[ -n "$SSH_IDENTITY_FILE" ]]; then
    rsh+=" -i $SSH_IDENTITY_FILE"
  fi
  rsync -hW --partial --progress -e "$rsh" "$@"
}

run_tests() {
  echo "Running complete backend test suite before deployment..."
  (cd "$PROJECT_DIR" && sh mvnw clean test)
  echo "All backend tests passed. Deployment may continue."
}

build_war() {
  local models_dir="$PROJECT_DIR/src/main/resources/models"
  for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
    if [[ ! -f "$models_dir/$model" ]]; then
      echo "Missing $models_dir/$model — required for facial recognition." >&2
      exit 1
    fi
  done
  echo "Building $WAR_NAME (Maven profile $MAVEN_WAR_PROFILE) for Linux x86_64..."
  local maven_args=(clean package -DskipTests -Ddjl.linux -P"$MAVEN_WAR_PROFILE")
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    mkdir -p "$PROJECT_DIR/target"
    bash "$SCRIPT_DIR/subsystems.sh" --with "$WITH_SUBSYSTEMS" --write-dir "$PROJECT_DIR/target"
    local excludes
    excludes="$(tr -d '\n' < "$PROJECT_DIR/target/subsystem-excludes.txt")"
    maven_args+=("-Dsubsystem.excludes=$excludes")
    maven_args+=("-Dsubsystem.with=$WITH_SUBSYSTEMS")
  fi
  (cd "$PROJECT_DIR" && sh mvnw "${maven_args[@]}")
  VERIFY_WAR="$WAR_PATH" bash "$SCRIPT_DIR/verify-djl-war.sh"
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    VERIFY_WAR="$WAR_PATH" VERIFY_WITH_SUBSYSTEMS="$WITH_SUBSYSTEMS" bash "$SCRIPT_DIR/verify-war.sh"
  fi
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

python3 - <<PY
from pathlib import Path

compose_path = Path("$DOCKER_COMPOSE_FILE")
text = compose_path.read_text()

if "APP_RELEASE" in text:
    print("docker-compose already contains APP_RELEASE")
else:
    lines = text.splitlines()
    out = []
    in_tomcat = False
    inserted = False
    for line in lines:
        if line.rstrip() == "  tomcat:":
            in_tomcat = True
        elif in_tomcat and line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(":"):
            in_tomcat = False
        out.append(line)
        if in_tomcat and not inserted and line.strip() == "environment:":
            out.append("      APP_RELEASE: \${APP_RELEASE:-}")
            inserted = True
    if not inserted:
        raise SystemExit("Could not insert APP_RELEASE under tomcat.environment")
    compose_path.write_text("\\n".join(out) + "\\n")
    print("Added APP_RELEASE to docker-compose.yml")
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

restore_host_wars() {
  echo "Restoring WARs from $DOCKER_COMPOSE_DIR into Tomcat webapps..."
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
HOST_DIR='$DOCKER_COMPOSE_DIR'
for war in ispadmin.war ispadmin-staging.war; do
  if [[ -f "\$HOST_DIR/\$war" ]]; then
    docker cp "\$HOST_DIR/\$war" "\$CONTAINER:\$CATALINA/webapps/\$war"
    echo "Restored \$war"
  fi
done
EOF
}

ensure_war_profile_isolation() {
  echo "Removing shared SPRING_PROFILES_ACTIVE / SPRING_DATASOURCE_URL so each WAR uses its baked profile..."
  local changed
  changed="$(run_ssh "bash -s" <<EOF
set -euo pipefail
COMPOSE='$DOCKER_COMPOSE_FILE'
ENV_FILE='$BACKEND_ENV_FILE'
python3 - <<'PY'
from pathlib import Path

drop = {"SPRING_PROFILES_ACTIVE", "SPRING_DATASOURCE_URL"}
changed = False

compose_path = Path("$DOCKER_COMPOSE_FILE")
lines = compose_path.read_text().splitlines()
out = []
in_tomcat = False
in_environment = False
for line in lines:
    if line.rstrip() == "  tomcat:":
        in_tomcat = True
        in_environment = False
        out.append(line)
        continue
    if in_tomcat and line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(":"):
        in_tomcat = False
        in_environment = False
    if in_tomcat and line.strip() == "environment:":
        in_environment = True
        out.append(line)
        continue
    if in_tomcat and in_environment:
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            key = stripped.split(":", 1)[0].split("=", 1)[0].strip()
            if key in drop:
                changed = True
                continue
    out.append(line)
if changed:
    compose_path.write_text("\\n".join(out) + "\\n")

env_path = Path("$BACKEND_ENV_FILE")
if env_path.exists():
    env_lines = env_path.read_text().splitlines()
    kept = []
    env_changed = False
    for line in env_lines:
        key = line.split("=", 1)[0].strip()
        if key in drop:
            env_changed = True
            continue
        kept.append(line)
    if env_changed:
        env_path.write_text("\\n".join(kept) + ("\\n" if kept else ""))
        changed = True

print("1" if changed else "0")
PY
EOF
)"
  changed="$(printf '%s' "$changed" | tail -n 1)"
  if [[ "$changed" == "1" ]]; then
    echo "Tomcat Spring overrides removed; recreating container..."
    run_ssh "cd '$DOCKER_COMPOSE_DIR' && docker compose up -d tomcat"
    wait_for_tomcat
    restore_host_wars
  else
    echo "Tomcat already isolates JDBC/profile per WAR"
  fi
}

prepare_prod_war_on_host_if_splitting() {
  if [[ "$DEPLOY_ENV" != "staging" ]]; then
    return 0
  fi
  local pinned
  pinned="$(run_ssh "grep -c 'SPRING_PROFILES_ACTIVE' '$DOCKER_COMPOSE_FILE' || true")"
  pinned="$(printf '%s' "$pinned" | tail -n 1)"
  if [[ "$pinned" == "0" || -z "$pinned" ]]; then
    return 0
  fi
  echo "Compose still pins SPRING_PROFILES_ACTIVE; baking ispadmin.war (prod) onto the host before the split..."
  (cd "$PROJECT_DIR" && sh mvnw package -DskipTests -Ddjl.linux -Pprod-war)
  VERIFY_WAR="$PROJECT_DIR/target/ispadmin.war" bash "$SCRIPT_DIR/verify-djl-war.sh"
  run_rsync "$PROJECT_DIR/target/ispadmin.war" "$SSH_TARGET:${DOCKER_COMPOSE_DIR%/}/ispadmin.war"
}

sync_war_to_host() {
  if [[ -f "$WAR_PATH" ]]; then
    echo "Staging $WAR_NAME on VPS host before any Tomcat recreate..."
    run_rsync "$WAR_PATH" "$SSH_TARGET:${DOCKER_COMPOSE_DIR%/}/$WAR_NAME"
  fi
}

ensure_nginx_staging() {
  echo "Ensuring nginx location /ispadmin-staging/ ..."
  local snippet
  snippet="$(cat "$SCRIPT_DIR/nginx-ispadmin-staging.location.conf")"
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONF='/etc/nginx/sites-enabled/api.gigafiberperu.cloud.conf'
if grep -q 'location /ispadmin-staging/' "\$CONF"; then
  echo "nginx already has /ispadmin-staging/"
  exit 0
fi
python3 - <<'PY'
from pathlib import Path
conf = Path("/etc/nginx/sites-enabled/api.gigafiberperu.cloud.conf")
text = conf.read_text()
snippet = """$snippet"""
marker = "    location /ispadmin/ws {"
idx = text.find(marker)
if idx == -1:
    raise SystemExit("Could not find location /ispadmin/ws in nginx conf")
end = text.find("    location / {", idx)
if end == -1:
    raise SystemExit("Could not find location / after websocket block")
conf.write_text(text[:end] + snippet.rstrip() + "\\n\\n" + text[end:])
print("Inserted /ispadmin-staging/ nginx locations")
PY
nginx -t
nginx -s reload
echo "nginx reloaded"
EOF
}

wait_for_app() {
  echo "Waiting for $WAR_NAME to deploy at $APP_CONTEXT_PATH/ ..."
  local i code
  for i in $(seq 1 90); do
    code="$(run_ssh "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080${APP_CONTEXT_PATH}/ 2>/dev/null || true")"
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
  local war_bytes remote_war
  war_bytes="$(wc -c < "$WAR_PATH" | tr -d ' ')"
  remote_war="${DOCKER_COMPOSE_DIR%/}/$WAR_NAME"
  echo "Uploading $WAR_NAME (${war_bytes} bytes) via rsync to $remote_war ..."
  run_rsync "$WAR_PATH" "$SSH_TARGET:$remote_war"
  echo "Deploying $WAR_NAME to container $DOCKER_TOMCAT_CONTAINER ..."
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
WAR='$WAR_NAME'
REMOTE_WAR='$remote_war'
EXPECTED_BYTES='$war_bytes'
CONTEXT_DIR='${WAR_NAME%.war}'

remote_bytes="\$(wc -c < "\$REMOTE_WAR" | tr -d ' ')"
if [[ "\$remote_bytes" != "\$EXPECTED_BYTES" ]]; then
  echo "Remote WAR size mismatch: expected \$EXPECTED_BYTES got \$remote_bytes" >&2
  exit 1
fi

docker exec "\$CONTAINER" sh -c "rm -rf \$CATALINA/webapps/\$CONTEXT_DIR \$CATALINA/webapps/\$WAR"
docker cp "\$REMOTE_WAR" "\$CONTAINER:\$CATALINA/webapps/\$WAR"

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
    if run_ssh "docker logs '$DOCKER_TOMCAT_CONTAINER' 2>&1 | grep -qE 'Motor facial DJL listo|Cargando modelo facial ONNX'"; then
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
  code="$(run_ssh "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080${APP_CONTEXT_PATH}/ || true")"
  echo "GET ${APP_CONTEXT_PATH}/ -> HTTP $code"
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

load_release_version() {
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/version.sh"
  echo "Release version: $RELEASE_VERSION"
  if [[ "${DEPLOY_ENV}" == "prod" && "${RELEASE_DIRTY:-0}" == "1" ]]; then
    echo "ERROR: hay cambios sin commitear; el SHA ($RELEASE_SHA) no representa el código a desplegar." >&2
    echo "Haz commit (y crea un tag nuevo si corresponde) antes de desplegar para no repetir versión." >&2
    exit 1
  fi
}

check_version_not_registered() {
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    echo "Staging: skip observability release duplicate check"
    return 0
  fi
  if [[ -z "$OBS_BASE_URL" || -z "$OBS_API_KEY" ]]; then
    echo "WARNING: OBS_BASE_URL/OBS_API_KEY sin definir; no se puede verificar duplicado de versión" >&2
    return 0
  fi
  echo "Verificando que $RELEASE_VERSION no esté ya registrada..."
  local existing
  existing="$(curl -sf "$OBS_BASE_URL/observability/releases?platform=backend" \
    -H "X-Obs-Api-Key: $OBS_API_KEY" 2>/dev/null || true)"
  if [[ -z "$existing" ]]; then
    echo "WARNING: no se pudo consultar releases; se continúa sin verificación de duplicado" >&2
    return 0
  fi
  if printf '%s' "$existing" | grep -qF "\"release\":\"$RELEASE_VERSION\""; then
    echo "ERROR: la versión $RELEASE_VERSION ya está desplegada/registrada (mismo tag+SHA)." >&2
    echo "El código no cambió. Haz un commit nuevo y/o crea un tag nuevo antes de desplegar." >&2
    exit 1
  fi
  echo "OK: $RELEASE_VERSION no estaba registrada."
}

update_release_env() {
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    echo "Staging: skip APP_RELEASE (shared Tomcat env_file belongs to prod)"
    return 0
  fi
  echo "Ensuring APP_RELEASE=$RELEASE_VERSION in $BACKEND_ENV_FILE ..."
  local mapbox_token="${MAPBOX_ACCESS_TOKEN:-}"
  local genieacs_log_curl="${DEPLOY_GENIEACS_LOG_CURL:-}"
  local release_out
  release_out="$(run_ssh "bash -s" <<EOF
set -euo pipefail
ENV_FILE='$BACKEND_ENV_FILE'
VALUE='$RELEASE_VERSION'
MAPBOX_TOKEN='$mapbox_token'
GENIEACS_LOG_CURL='$genieacs_log_curl'
touch "\$ENV_FILE"
changed=0

if grep -q '^APP_RELEASE=' "\$ENV_FILE"; then
  current="\$(grep '^APP_RELEASE=' "\$ENV_FILE" | head -1 | cut -d= -f2-)"
  if [[ "\$current" != "\$VALUE" ]]; then
    cp "\$ENV_FILE" "\${ENV_FILE}.bak.\$(date +%Y%m%d%H%M%S)"
    sed -i "s#^APP_RELEASE=.*#APP_RELEASE=\$VALUE#" "\$ENV_FILE"
    changed=1
  fi
else
  cp "\$ENV_FILE" "\${ENV_FILE}.bak.\$(date +%Y%m%d%H%M%S)"
  printf '\nAPP_RELEASE=%s\n' "\$VALUE" >> "\$ENV_FILE"
  changed=1
fi

if [[ -n "\$MAPBOX_TOKEN" ]]; then
  if grep -q '^MAPBOX_ACCESS_TOKEN=' "\$ENV_FILE"; then
    current_mapbox="\$(grep '^MAPBOX_ACCESS_TOKEN=' "\$ENV_FILE" | head -1 | cut -d= -f2-)"
    if [[ "\$current_mapbox" != "\$MAPBOX_TOKEN" ]]; then
      if [[ "\$changed" -eq 0 ]]; then
        cp "\$ENV_FILE" "\${ENV_FILE}.bak.\$(date +%Y%m%d%H%M%S)"
      fi
      sed -i "s#^MAPBOX_ACCESS_TOKEN=.*#MAPBOX_ACCESS_TOKEN=\$MAPBOX_TOKEN#" "\$ENV_FILE"
      changed=1
    fi
  else
    if [[ "\$changed" -eq 0 ]]; then
      cp "\$ENV_FILE" "\${ENV_FILE}.bak.\$(date +%Y%m%d%H%M%S)"
    fi
    printf '\nMAPBOX_ACCESS_TOKEN=%s\n' "\$MAPBOX_TOKEN" >> "\$ENV_FILE"
    changed=1
  fi
else
  if ! grep -q '^MAPBOX_ACCESS_TOKEN=' "\$ENV_FILE"; then
    echo "WARNING: MAPBOX_ACCESS_TOKEN no está en \$ENV_FILE; las rutas Smart Map usarán líneas rectas (fallback)" >&2
  fi
fi

if [[ -n "\$GENIEACS_LOG_CURL" ]]; then
  if grep -q '^GENIEACS_LOG_CURL=' "\$ENV_FILE"; then
    current_genieacs_log="\$(grep '^GENIEACS_LOG_CURL=' "\$ENV_FILE" | head -1 | cut -d= -f2-)"
    if [[ "\$current_genieacs_log" != "\$GENIEACS_LOG_CURL" ]]; then
      if [[ "\$changed" -eq 0 ]]; then
        cp "\$ENV_FILE" "\${ENV_FILE}.bak.\$(date +%Y%m%d%H%M%S)"
      fi
      sed -i "s#^GENIEACS_LOG_CURL=.*#GENIEACS_LOG_CURL=\$GENIEACS_LOG_CURL#" "\$ENV_FILE"
      changed=1
    fi
  else
    if [[ "\$changed" -eq 0 ]]; then
      cp "\$ENV_FILE" "\${ENV_FILE}.bak.\$(date +%Y%m%d%H%M%S)"
    fi
    printf '\nGENIEACS_LOG_CURL=%s\n' "\$GENIEACS_LOG_CURL" >> "\$ENV_FILE"
    changed=1
  fi
fi

if [[ "\$changed" -eq 1 ]]; then
  echo "Recreando Tomcat para cargar variables de entorno..."
  cd '$DOCKER_COMPOSE_DIR' && docker compose up -d tomcat
  echo "RESTORE_WARS"
else
  echo "Variables de entorno sin cambios"
fi
EOF
)"
  printf '%s\n' "$release_out"
  if printf '%s' "$release_out" | grep -q RESTORE_WARS; then
    wait_for_tomcat
    restore_host_wars
  fi
}

register_deploy() {
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    echo "Staging: skip observability deploy event"
    return 0
  fi
  if [[ -z "$OBS_BASE_URL" || -z "$OBS_API_KEY" ]]; then
    echo "WARNING: OBS_BASE_URL/OBS_API_KEY sin definir; se omite el registro del deploy" >&2
    return 0
  fi
  echo "Registrando deploy event en $OBS_BASE_URL/observability/releases ..."
  local payload
  payload="$(printf '{"platform":"backend","release":"%s","semver":"%s","gitSha":"%s","notes":null}' \
    "$RELEASE_VERSION" "$RELEASE_SEMVER" "$RELEASE_SHA")"
  if curl -sf -X POST "$OBS_BASE_URL/observability/releases" \
    -H "X-Obs-Api-Key: $OBS_API_KEY" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null; then
    echo "Deploy event registrado ($RELEASE_VERSION)"
  else
    echo "WARNING: no se pudo registrar el deploy event (no fatal)" >&2
  fi
}

case "$MODE" in
  setup)
    setup_djl
    echo "Setup complete. Redeploy WAR with: ./scripts/deploy.sh --war-only"
    ;;
  full)
    load_release_version
    check_version_not_registered
    run_tests
    setup_djl
    prepare_prod_war_on_host_if_splitting
    sync_war_to_host
    ensure_war_profile_isolation
    ensure_nginx_staging
    update_release_env
    deploy_war
    wait_for_app
    verify_djl_logs || true
    verify_http || true
    register_deploy
    ;;
  war-only)
    load_release_version
    check_version_not_registered
    run_tests
    init_ssh
    prepare_prod_war_on_host_if_splitting
    sync_war_to_host
    ensure_war_profile_isolation
    ensure_nginx_staging
    update_release_env
    deploy_war
    wait_for_app
    verify_http || true
    register_deploy
    ;;
  deploy)
    load_release_version
    check_version_not_registered
    run_tests
    build_war
    init_ssh
    prepare_prod_war_on_host_if_splitting
    sync_war_to_host
    ensure_war_profile_isolation
    ensure_nginx_staging
    update_release_env
    deploy_war
    wait_for_app
    verify_http || true
    register_deploy
    ;;
esac

echo "Done."
