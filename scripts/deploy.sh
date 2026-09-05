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
ONLY_WARS=""

usage() {
  cat <<'EOF'
Usage: ./scripts/deploy.sh [--setup|--full|--war-only|--deploy] [--env prod|staging] [--with key,key] [--only key,key]

  --setup     Upload DJL libs and face models, patch Docker image/compose, rebuild Tomcat (once)
  --full      --setup then deploy WAR
  --war-only  Deploy existing target WAR only
  --deploy    Build, verify, deploy WAR (default)
  --env       prod (default): ispadmin.war → /ispadmin on tomcat9027
              staging: ispadmin-staging*.war → tomcat-staging :8081 (does not touch tomcat9027 or ispadmin.war)
  --with      Optional subsystems to keep in the staging Core WAR (observability,oltgateway,netdiag,traffic,servicehealth).
              Default staging: none. `traffic`, `oltgateway` and `acs` enable HTTP clients; those classes always ship in sibling WARs.
  --only      Staging: deploy only these WARs (core,oltgateway,traffic,acs). Wins over git mapping.

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
    --only)
      ONLY_WARS="${2:-}"
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
DOCKER_TOMCAT_STAGING_CONTAINER="${DOCKER_TOMCAT_STAGING_CONTAINER:-tomcat-staging}"
TOMCAT_STAGING_HTTP_PORT="${TOMCAT_STAGING_HTTP_PORT:-8081}"
TOMCAT_HTTP_PORT="${TOMCAT_HTTP_PORT:-8080}"
DOCKER_TOMCAT_LIB_HOST_DIR="${DOCKER_TOMCAT_LIB_HOST_DIR:-/opt/gigafiber/tomcat/lib}"
DOCKER_FACE_MODELS_HOST_DIR="${DOCKER_FACE_MODELS_HOST_DIR:-/opt/gigafiber/models}"
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
TOMCAT_COMPOSE_SERVICE="tomcat"
if [[ "$DEPLOY_ENV" == "staging" ]]; then
  WAR_NAME="ispadmin-staging.war"
  APP_CONTEXT_PATH="/ispadmin-staging"
  MAVEN_WAR_PROFILE="staging-war"
  TRAFFIC_WAR_NAME="ispadmin-staging-traffic.war"
  TRAFFIC_CONTEXT_PATH="/ispadmin-staging-traffic"
  TRAFFIC_MAVEN_PROFILE="traffic-staging-war"
  OLTGATEWAY_WAR_NAME="ispadmin-staging-oltgateway.war"
  OLTGATEWAY_CONTEXT_PATH="/ispadmin-staging-oltgateway"
  OLTGATEWAY_MAVEN_PROFILE="oltgateway-staging-war"
  ACS_WAR_NAME="ispadmin-staging-acs.war"
  ACS_CONTEXT_PATH="/ispadmin-staging-acs"
  ACS_MAVEN_PROFILE="acs-staging-war"
  DOCKER_TOMCAT_CONTAINER="$DOCKER_TOMCAT_STAGING_CONTAINER"
  TOMCAT_HTTP_PORT="$TOMCAT_STAGING_HTTP_PORT"
  TOMCAT_COMPOSE_SERVICE="tomcat-staging"
else
  APP_CONTEXT_PATH="/ispadmin"
  MAVEN_WAR_PROFILE="prod-war"
  TRAFFIC_WAR_NAME=""
  TRAFFIC_CONTEXT_PATH=""
  TRAFFIC_MAVEN_PROFILE=""
  OLTGATEWAY_WAR_NAME=""
  OLTGATEWAY_CONTEXT_PATH=""
  OLTGATEWAY_MAVEN_PROFILE=""
  ACS_WAR_NAME=""
  ACS_CONTEXT_PATH=""
  ACS_MAVEN_PROFILE=""
fi

if [[ "$DEPLOY_ENV" == "staging" ]]; then
  if [[ -n "$ONLY_WARS" ]]; then
    SELECTED_WARS="$(bash "$SCRIPT_DIR/deploy-select-wars.sh" --only "$ONLY_WARS")"
  elif [[ "$MODE" == "setup" ]]; then
    SELECTED_WARS="acs,core,oltgateway,traffic"
  else
    SELECTED_WARS="$(cd "$PROJECT_DIR" && bash "$SCRIPT_DIR/deploy-select-wars.sh")"
  fi
else
  if [[ -n "$ONLY_WARS" ]]; then
    echo "Ignoring --only on prod (core WAR only)"
  fi
  SELECTED_WARS="core"
fi
echo "Selected WARs: $SELECTED_WARS (env=$DEPLOY_ENV container=$DOCKER_TOMCAT_CONTAINER port=$TOMCAT_HTTP_PORT)"

war_selected() {
  local key="$1"
  [[ ",${SELECTED_WARS}," == *",${key},"* ]]
}

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
  rsync -htW --partial --progress -e "$rsh" "$@"
}

run_tests() {
  echo "Running complete backend test suite before deployment..."
  (cd "$PROJECT_DIR" && sh mvnw clean test)
  echo "All backend tests passed. Deployment may continue."
}

build_war() {
  if war_selected core; then
    local models_dir="$PROJECT_DIR/src/main/resources/models"
    for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
      if [[ ! -f "$models_dir/$model" ]]; then
        echo "Missing $models_dir/$model — required for facial recognition." >&2
        exit 1
      fi
    done
    echo "Building $WAR_NAME (Maven profile $MAVEN_WAR_PROFILE) for Linux x86_64..."
    local maven_args=(package -DskipTests -Ddjl.linux -P"$MAVEN_WAR_PROFILE")
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
  else
    echo "Skipping core WAR package (not in $SELECTED_WARS)"
  fi
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    if war_selected traffic; then
      build_traffic_war
    fi
    if war_selected oltgateway; then
      build_oltgateway_war
    fi
    if war_selected acs; then
      build_acs_war
    fi
  fi
}

build_traffic_war() {
  if [[ -z "${TRAFFIC_MAVEN_PROFILE:-}" ]]; then
    return 0
  fi
  echo "Building $TRAFFIC_WAR_NAME (Maven profile $TRAFFIC_MAVEN_PROFILE)..."
  (cd "$PROJECT_DIR" && sh mvnw package -DskipTests -Ddjl.linux -P"$TRAFFIC_MAVEN_PROFILE")
  local traffic_war="$PROJECT_DIR/target/$TRAFFIC_WAR_NAME"
  if [[ ! -f "$traffic_war" ]]; then
    echo "Missing $traffic_war" >&2
    exit 1
  fi
}

build_oltgateway_war() {
  if [[ -z "${OLTGATEWAY_MAVEN_PROFILE:-}" ]]; then
    return 0
  fi
  echo "Building $OLTGATEWAY_WAR_NAME (Maven profile $OLTGATEWAY_MAVEN_PROFILE)..."
  (cd "$PROJECT_DIR" && sh mvnw package -DskipTests -Ddjl.linux -P"$OLTGATEWAY_MAVEN_PROFILE")
  local gateway_war="$PROJECT_DIR/target/$OLTGATEWAY_WAR_NAME"
  if [[ ! -f "$gateway_war" ]]; then
    echo "Missing $gateway_war" >&2
    exit 1
  fi
}

build_acs_war() {
  if [[ -z "${ACS_MAVEN_PROFILE:-}" ]]; then
    return 0
  fi
  echo "Building $ACS_WAR_NAME (Maven profile $ACS_MAVEN_PROFILE)..."
  (cd "$PROJECT_DIR" && sh mvnw package -DskipTests -Ddjl.linux -P"$ACS_MAVEN_PROFILE")
  local acs_war="$PROJECT_DIR/target/$ACS_WAR_NAME"
  if [[ ! -f "$acs_war" ]]; then
    echo "Missing $acs_war" >&2
    exit 1
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

ensure_face_models_volume() {
  echo "Ensuring $TOMCAT_COMPOSE_SERVICE mounts $DOCKER_FACE_MODELS_HOST_DIR ..."
  run_ssh "bash -s" <<EOF
set -euo pipefail
python3 - <<PY
from pathlib import Path

compose_path = Path("$DOCKER_COMPOSE_FILE")
text = compose_path.read_text()
mount = "$DOCKER_FACE_MODELS_HOST_DIR:/opt/gigafiber/models:ro"
service_header = "  $TOMCAT_COMPOSE_SERVICE:"
if not compose_path.is_file():
    raise SystemExit("docker-compose not found: $DOCKER_COMPOSE_FILE")
if service_header not in text:
    raise SystemExit("compose service not found: $TOMCAT_COMPOSE_SERVICE")
lines = text.splitlines()
out = []
in_tomcat = False
in_volumes = False
inserted = False
for line in lines:
    if line.rstrip() == service_header:
        in_tomcat = True
        in_volumes = False
    elif in_tomcat and line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(":"):
        if not inserted:
            out.append("    volumes:")
            out.append(f"      - {mount}")
            inserted = True
        in_tomcat = False
        in_volumes = False
    if in_tomcat and line.strip() == "volumes:":
        in_volumes = True
    if in_tomcat and mount in line:
        inserted = True
    out.append(line)
    if in_tomcat and in_volumes and not inserted and line.startswith("      - "):
        out.append(f"      - {mount}")
        inserted = True
if in_tomcat and not inserted:
    out.append("    volumes:")
    out.append(f"      - {mount}")
    inserted = True
if not inserted:
    raise SystemExit("Could not insert face models volume under $TOMCAT_COMPOSE_SERVICE")
if "\\n".join(out) == "\\n".join(lines):
    print("docker-compose already mounts face models on $TOMCAT_COMPOSE_SERVICE")
else:
    compose_path.write_text("\\n".join(out) + "\\n")
    print("Added face models volume to $TOMCAT_COMPOSE_SERVICE")
PY
EOF
}

upload_face_models() {
  if ! war_selected core; then
    echo "Skipping face models (core WAR not selected)"
    return 0
  fi
  local src="$PROJECT_DIR/src/main/resources/models"
  local dest="$DOCKER_FACE_MODELS_HOST_DIR"
  for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
    if [[ ! -f "$src/$model" ]]; then
      echo "Missing $src/$model — required on VPS for facial login." >&2
      exit 1
    fi
  done
  echo "Uploading face models to $dest ..."
  run_ssh "mkdir -p '$dest'"
  for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
    run_rsync "$src/$model" "$SSH_TARGET:$dest/$model"
  done
  ensure_face_models_volume
  if run_ssh "docker inspect '$DOCKER_TOMCAT_CONTAINER' >/dev/null 2>&1"; then
    local mounts
    mounts="$(run_ssh "docker inspect -f '{{range .Mounts}}{{println .Destination}}{{end}}' '$DOCKER_TOMCAT_CONTAINER'")"
    if grep -qx '/opt/gigafiber/models' <<< "$mounts"; then
      echo "Face models volume already mounted in $DOCKER_TOMCAT_CONTAINER"
    else
      echo "Copying face models into $DOCKER_TOMCAT_CONTAINER ..."
      run_ssh "docker exec '$DOCKER_TOMCAT_CONTAINER' mkdir -p /opt/gigafiber/models"
      for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
        run_ssh "docker cp '$dest/$model' '$DOCKER_TOMCAT_CONTAINER:/opt/gigafiber/models/$model'"
      done
    fi
  fi
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
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    echo "Staging: rebuild image if needed, start tomcat-staging only (not tomcat9027)..."
    ensure_tomcat_staging
    run_ssh "cd '$DOCKER_COMPOSE_DIR' && docker compose build tomcat && docker compose up -d tomcat-staging"
    return 0
  fi
  echo "Rebuilding Tomcat container..."
  run_ssh "cd '$DOCKER_COMPOSE_DIR' && docker compose build tomcat && docker compose up -d tomcat"
}

wait_for_tomcat() {
  echo "Waiting for Tomcat to start on $TOMCAT_HTTP_PORT..."
  local i
  for i in $(seq 1 60); do
    if run_ssh "curl -sf -o /dev/null http://127.0.0.1:${TOMCAT_HTTP_PORT}/ 2>/dev/null"; then
      echo "Tomcat is responding on port $TOMCAT_HTTP_PORT"
      return 0
    fi
    sleep 5
  done
  echo "Tomcat did not become ready in time" >&2
  return 1
}

restore_host_wars() {
  echo "Restoring WARs from $DOCKER_COMPOSE_DIR into $DOCKER_TOMCAT_CONTAINER webapps..."
  local wars
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    wars="ispadmin-staging.war ispadmin-staging-traffic.war ispadmin-staging-oltgateway.war ispadmin-staging-acs.war"
  else
    wars="ispadmin.war ispadmin-traffic.war ispadmin-oltgateway.war ispadmin-acs.war"
  fi
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
HOST_DIR='$DOCKER_COMPOSE_DIR'
for war in $wars; do
  if [[ -f "\$HOST_DIR/\$war" ]]; then
    docker cp "\$HOST_DIR/\$war" "\$CONTAINER:\$CATALINA/webapps/\$war"
    echo "Restored \$war"
  fi
done
EOF
}

ensure_war_profile_isolation() {
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    echo "Staging uses tomcat-staging; skipping prod compose isolation"
    return 0
  fi
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
  return 0
}

sync_war_to_host() {
  if war_selected core && [[ -f "$WAR_PATH" ]]; then
    echo "Staging $WAR_NAME on VPS host..."
    run_rsync "$WAR_PATH" "$SSH_TARGET:${DOCKER_COMPOSE_DIR%/}/$WAR_NAME"
  fi
  if war_selected traffic && [[ -n "${TRAFFIC_WAR_NAME:-}" && -f "$PROJECT_DIR/target/$TRAFFIC_WAR_NAME" ]]; then
    echo "Staging $TRAFFIC_WAR_NAME on VPS host..."
    run_rsync "$PROJECT_DIR/target/$TRAFFIC_WAR_NAME" "$SSH_TARGET:${DOCKER_COMPOSE_DIR%/}/$TRAFFIC_WAR_NAME"
  fi
  if war_selected oltgateway && [[ -n "${OLTGATEWAY_WAR_NAME:-}" && -f "$PROJECT_DIR/target/$OLTGATEWAY_WAR_NAME" ]]; then
    echo "Staging $OLTGATEWAY_WAR_NAME on VPS host..."
    run_rsync "$PROJECT_DIR/target/$OLTGATEWAY_WAR_NAME" "$SSH_TARGET:${DOCKER_COMPOSE_DIR%/}/$OLTGATEWAY_WAR_NAME"
  fi
  if war_selected acs && [[ -n "${ACS_WAR_NAME:-}" && -f "$PROJECT_DIR/target/$ACS_WAR_NAME" ]]; then
    echo "Staging $ACS_WAR_NAME on VPS host..."
    run_rsync "$PROJECT_DIR/target/$ACS_WAR_NAME" "$SSH_TARGET:${DOCKER_COMPOSE_DIR%/}/$ACS_WAR_NAME"
  fi
}

ensure_tomcat_staging() {
  if [[ "$DEPLOY_ENV" != "staging" ]]; then
    return 0
  fi
  echo "Ensuring compose service tomcat-staging (host port $TOMCAT_STAGING_HTTP_PORT)..."
  run_scp "$SCRIPT_DIR/ensure-tomcat-staging-compose.py" "$SSH_TARGET:/tmp/ensure-tomcat-staging-compose.py"
  run_ssh "python3 /tmp/ensure-tomcat-staging-compose.py '$DOCKER_COMPOSE_FILE'"
  run_ssh "cd '$DOCKER_COMPOSE_DIR' && docker compose up -d tomcat-staging"
}

ensure_nginx_staging() {
  if [[ "$DEPLOY_ENV" != "staging" ]]; then
    return 0
  fi
  echo "Ensuring nginx /ispadmin-staging* → gigafiber_backend_staging :$TOMCAT_STAGING_HTTP_PORT ..."
  run_scp "$SCRIPT_DIR/rewrite-nginx-staging-upstream.py" "$SSH_TARGET:/tmp/rewrite-nginx-staging-upstream.py"
  run_scp "$SCRIPT_DIR/nginx-ispadmin-staging.location.conf" "$SSH_TARGET:/tmp/nginx-ispadmin-staging.location.conf"
  run_ssh "python3 /tmp/rewrite-nginx-staging-upstream.py /etc/nginx/sites-enabled/api.gigafiberperu.cloud.conf /tmp/nginx-ispadmin-staging.location.conf && nginx -t && nginx -s reload && echo nginx reloaded"
}

wait_health() {
  local path="$1"
  local i code
  echo "Waiting for http://127.0.0.1:${TOMCAT_HTTP_PORT}${path} ..."
  for i in $(seq 1 90); do
    code="$(run_ssh "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${TOMCAT_HTTP_PORT}${path} 2>/dev/null || true")"
    if [[ "$code" == "200" || "$code" == "302" ]]; then
      echo "Responding (HTTP $code) ${path}"
      return 0
    fi
    sleep 5
  done
  echo "Did not become ready in time ${path} (last HTTP $code)" >&2
  return 1
}

wait_for_app() {
  if war_selected core; then
    wait_health "${APP_CONTEXT_PATH}/"
  fi
  if war_selected traffic; then
    wait_health "${TRAFFIC_CONTEXT_PATH}/actuator/health"
  fi
  if war_selected oltgateway; then
    wait_health "${OLTGATEWAY_CONTEXT_PATH}/actuator/health"
  fi
  if war_selected acs; then
    wait_health "${ACS_CONTEXT_PATH}/actuator/health"
  fi
}

deploy_war() {
  if ! war_selected core; then
    echo "Skipping core WAR deploy (not in $SELECTED_WARS)"
    return 0
  fi
  if [[ ! -f "$WAR_PATH" ]]; then
    echo "Missing $WAR_PATH" >&2
    exit 1
  fi
  local war_bytes remote_war
  war_bytes="$(wc -c < "$WAR_PATH" | tr -d ' ')"
  remote_war="${DOCKER_COMPOSE_DIR%/}/$WAR_NAME"
  echo "Deploying $WAR_NAME (${war_bytes} bytes) from host $remote_war to $DOCKER_TOMCAT_CONTAINER ..."
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

deploy_traffic_war() {
  if ! war_selected traffic; then
    return 0
  fi
  if [[ -z "${TRAFFIC_WAR_NAME:-}" ]]; then
    return 0
  fi
  local war_path="$PROJECT_DIR/target/$TRAFFIC_WAR_NAME"
  if [[ ! -f "$war_path" ]]; then
    echo "Missing $war_path" >&2
    exit 1
  fi
  local war_bytes remote_war
  war_bytes="$(wc -c < "$war_path" | tr -d ' ')"
  remote_war="${DOCKER_COMPOSE_DIR%/}/$TRAFFIC_WAR_NAME"
  echo "Deploying $TRAFFIC_WAR_NAME (${war_bytes} bytes) from host $remote_war to $DOCKER_TOMCAT_CONTAINER ..."
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
WAR='$TRAFFIC_WAR_NAME'
REMOTE_WAR='$remote_war'
EXPECTED_BYTES='$war_bytes'
CONTEXT_DIR='${TRAFFIC_WAR_NAME%.war}'

remote_bytes="\$(wc -c < "\$REMOTE_WAR" | tr -d ' ')"
if [[ "\$remote_bytes" != "\$EXPECTED_BYTES" ]]; then
  echo "Remote traffic WAR size mismatch: expected \$EXPECTED_BYTES got \$remote_bytes" >&2
  exit 1
fi

docker exec "\$CONTAINER" sh -c "rm -rf \$CATALINA/webapps/\$CONTEXT_DIR \$CATALINA/webapps/\$WAR"
docker cp "\$REMOTE_WAR" "\$CONTAINER:\$CATALINA/webapps/\$WAR"
EOF
}

deploy_oltgateway_war() {
  if ! war_selected oltgateway; then
    return 0
  fi
  if [[ -z "${OLTGATEWAY_WAR_NAME:-}" ]]; then
    return 0
  fi
  local war_path="$PROJECT_DIR/target/$OLTGATEWAY_WAR_NAME"
  if [[ ! -f "$war_path" ]]; then
    echo "Missing $war_path" >&2
    exit 1
  fi
  local war_bytes remote_war
  war_bytes="$(wc -c < "$war_path" | tr -d ' ')"
  remote_war="${DOCKER_COMPOSE_DIR%/}/$OLTGATEWAY_WAR_NAME"
  echo "Deploying $OLTGATEWAY_WAR_NAME (${war_bytes} bytes) from host $remote_war to $DOCKER_TOMCAT_CONTAINER ..."
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
WAR='$OLTGATEWAY_WAR_NAME'
REMOTE_WAR='$remote_war'
EXPECTED_BYTES='$war_bytes'
CONTEXT_DIR='${OLTGATEWAY_WAR_NAME%.war}'

remote_bytes="\$(wc -c < "\$REMOTE_WAR" | tr -d ' ')"
if [[ "\$remote_bytes" != "\$EXPECTED_BYTES" ]]; then
  echo "Remote oltgateway WAR size mismatch: expected \$EXPECTED_BYTES got \$remote_bytes" >&2
  exit 1
fi

docker exec "\$CONTAINER" sh -c "rm -rf \$CATALINA/webapps/\$CONTEXT_DIR \$CATALINA/webapps/\$WAR"
docker cp "\$REMOTE_WAR" "\$CONTAINER:\$CATALINA/webapps/\$WAR"
EOF
}

deploy_acs_war() {
  if ! war_selected acs; then
    return 0
  fi
  if [[ -z "${ACS_WAR_NAME:-}" ]]; then
    return 0
  fi
  local war_path="$PROJECT_DIR/target/$ACS_WAR_NAME"
  if [[ ! -f "$war_path" ]]; then
    echo "Missing $war_path" >&2
    exit 1
  fi
  local war_bytes remote_war
  war_bytes="$(wc -c < "$war_path" | tr -d ' ')"
  remote_war="${DOCKER_COMPOSE_DIR%/}/$ACS_WAR_NAME"
  echo "Deploying $ACS_WAR_NAME (${war_bytes} bytes) from host $remote_war to $DOCKER_TOMCAT_CONTAINER ..."
  run_ssh "bash -s" <<EOF
set -euo pipefail
CONTAINER='$DOCKER_TOMCAT_CONTAINER'
CATALINA='$CATALINA_HOME'
WAR='$ACS_WAR_NAME'
REMOTE_WAR='$remote_war'
EXPECTED_BYTES='$war_bytes'
CONTEXT_DIR='${ACS_WAR_NAME%.war}'

remote_bytes="\$(wc -c < "\$REMOTE_WAR" | tr -d ' ')"
if [[ "\$remote_bytes" != "\$EXPECTED_BYTES" ]]; then
  echo "Remote ACS WAR size mismatch: expected \$EXPECTED_BYTES got \$remote_bytes" >&2
  exit 1
fi

docker exec "\$CONTAINER" sh -c "rm -rf \$CATALINA/webapps/\$CONTEXT_DIR \$CATALINA/webapps/\$WAR"
docker cp "\$REMOTE_WAR" "\$CONTAINER:\$CATALINA/webapps/\$WAR"
EOF
}

verify_djl_logs() {
  if ! war_selected core; then
    return 0
  fi
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
  if ! war_selected core; then
    return 0
  fi
  local code
  code="$(run_ssh "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${TOMCAT_HTTP_PORT}${APP_CONTEXT_PATH}/ || true")"
  echo "GET ${APP_CONTEXT_PATH}/ -> HTTP $code"
  [[ "$code" == "200" || "$code" == "302" ]]
}

setup_djl() {
  init_ssh
  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    build_war
  fi
  upload_tomcat_lib
  if [[ "$DEPLOY_ENV" == "staging" ]]; then
    ensure_tomcat_staging
    upload_face_models
    echo "Staging setup: not patching or recreating prod tomcat"
    rebuild_tomcat_container
    wait_for_tomcat
    verify_djl_logs || true
    return 0
  fi
  upload_face_models
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
    deploy_traffic_war
    deploy_oltgateway_war
    deploy_acs_war
    if [[ "$DEPLOY_ENV" == "staging" ]]; then
      restore_host_wars
    fi
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
    ensure_tomcat_staging
    prepare_prod_war_on_host_if_splitting
    sync_war_to_host
    ensure_war_profile_isolation
    ensure_nginx_staging
    update_release_env
    deploy_war
    deploy_traffic_war
    deploy_oltgateway_war
    deploy_acs_war
    if [[ "$DEPLOY_ENV" == "staging" ]]; then
      restore_host_wars
    fi
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
    ensure_tomcat_staging
    upload_face_models
    prepare_prod_war_on_host_if_splitting
    sync_war_to_host
    ensure_war_profile_isolation
    ensure_nginx_staging
    update_release_env
    deploy_war
    deploy_traffic_war
    deploy_oltgateway_war
    deploy_acs_war
    if [[ "$DEPLOY_ENV" == "staging" ]]; then
      restore_host_wars
    fi
    wait_for_app
    verify_http || true
    register_deploy
    ;;
esac

echo "Done."
