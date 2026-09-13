#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS="$ROOT/src/main/resources/application-local-prestaging.secrets.properties"
EXAMPLE="$ROOT/src/main/resources/application-local-prestaging.secrets.properties.example"
MVN="$ROOT/mvnw"
CMD="${1:-help}"

need_secrets() {
  if [[ ! -f "$SECRETS" ]]; then
    echo "Missing $SECRETS" >&2
    echo "Copy $EXAMPLE to that path and fill passwords (or copy from application-local.properties)." >&2
    echo "WARs will not start without the overlay." >&2
    exit 1
  fi
}

listen_pids() {
  local port="$1"
  lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null | sort -u || true
}

free_listen_port() {
  local port="$1"
  case "$port" in
    8080|8082|8090) ;;
    *)
      echo "Refusing to free TCP port ${port} (only 8080, 8082, 8090)." >&2
      return 1
      ;;
  esac
  local pids
  pids="$(listen_pids "$port")"
  if [[ -z "$pids" ]]; then
    return 0
  fi
  echo "Stopping TCP listeners on ${port}: ${pids}" >&2
  local pid
  while read -r pid; do
    [[ -n "$pid" ]] || continue
    kill -TERM "$pid" 2>/dev/null || true
  done <<< "$pids"
  local waited=0
  while [[ $waited -lt 15 ]]; do
    pids="$(listen_pids "$port")"
    if [[ -z "$pids" ]]; then
      return 0
    fi
    sleep 0.2
    waited=$((waited + 1))
  done
  pids="$(listen_pids "$port")"
  if [[ -z "$pids" ]]; then
    return 0
  fi
  echo "Force-stopping TCP listeners on ${port}: ${pids}" >&2
  while read -r pid; do
    [[ -n "$pid" ]] || continue
    kill -KILL "$pid" 2>/dev/null || true
  done <<< "$pids"
  sleep 0.2
}

stop_wars() {
  free_listen_port 8080
  free_listen_port 8082
  free_listen_port 8090
}

run_core() {
  need_secrets
  free_listen_port 8082
  exec "$MVN" -DskipTests \
    -Dstart-class=com.dscorp.wispadmin.wispadmin.WispAdminApplicationKt \
    spring-boot:run \
    -Dspring-boot.run.mainClass=com.dscorp.wispadmin.wispadmin.WispAdminApplicationKt \
    -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false" \
    -Dspring-boot.run.arguments="--server.port=8082 --server.servlet.context-path=/ispadmin --spring.profiles.active=dev,local-prestaging"
}

run_gateway() {
  need_secrets
  free_listen_port 8080
  exec "$MVN" -DskipTests \
    -Dstart-class=com.dscorp.wispadmin.oltgateway.OltGatewayApplicationKt \
    spring-boot:run \
    -Dspring-boot.run.mainClass=com.dscorp.wispadmin.oltgateway.OltGatewayApplicationKt \
    -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false" \
    -Dspring-boot.run.arguments="--server.port=8080 --server.servlet.context-path=/ispadmin --spring.profiles.active=oltgateway,local-prestaging"
}

run_acs() {
  need_secrets
  free_listen_port 8090
  exec "$MVN" -DskipTests \
    -Dstart-class=com.dscorp.wispadmin.acs.AcsApplicationKt \
    spring-boot:run \
    -Dspring-boot.run.mainClass=com.dscorp.wispadmin.acs.AcsApplicationKt \
    -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false" \
    -Dspring-boot.run.arguments="--server.port=8090 --server.servlet.context-path=/ispadmin-acs --spring.profiles.active=acs,local-prestaging"
}

check_lab() {
  echo "ping OLT LAN"
  ping -c 1 -W 2 10.11.104.2 || true
  echo "GenieACS NBI tunnel 127.0.0.1:7557"
  nc -z 127.0.0.1 7557 && echo "nbi=up" || echo "nbi=down (start-genieacs-tunnel.sh; this env uses :7557 only)"
}

usage() {
  cat <<EOF
Opt-in local-prestaging (Core :8082, Gateway :8080, ACS :8090). Does not change VPS defaults.

  $0 stop
  $0 core
  $0 gateway
  $0 acs
  $0 restart core|gateway|acs
  $0 check

stop kills TCP listeners on 8080, 8082 and 8090 (SIGTERM, then SIGKILL if still listening).
core|gateway|acs (and restart <war>) free that WAR port then start: Core 8082, Gateway 8080, ACS 8090.
Does not kill listeners outside those three ports (SSH tunnels stay up). Never pkill java/mvn by name.

Requires $SECRETS (gitignored). Start each WAR in its own terminal.
EOF
}

cd "$ROOT"
case "$CMD" in
  core) run_core ;;
  gateway) run_gateway ;;
  acs) run_acs ;;
  stop) stop_wars ;;
  restart)
    case "${2:-}" in
      core) run_core ;;
      gateway) run_gateway ;;
      acs) run_acs ;;
      *) usage ;;
    esac
    ;;
  check) check_lab ;;
  *) usage ;;
esac
