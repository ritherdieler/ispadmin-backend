#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties"
EXAMPLE="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties.example"
CMD="${1:-help}"

need_secrets() {
  if [[ ! -f "$SECRETS" ]]; then
    echo "Missing $SECRETS" >&2
    echo "Copy $EXAMPLE to that path and fill passwords (or copy from application-local.properties)." >&2
    echo "bootRun will not start without the overlay." >&2
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
    8082) ;;
    *)
      echo "Refusing to free TCP port ${port} (only 8082)." >&2
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

stop_app() {
  free_listen_port 8082
}

run_app() {
  need_secrets
  free_listen_port 8082
  exec "$ROOT/gradlew" :core:bootRun \
    -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false" \
    --args="--server.port=8082 --server.servlet.context-path=/ispadmin --spring.profiles.active=dev,local-prestaging"
}

check_lab() {
  echo "ping OLT LAN"
  ping -c 1 -W 2 10.11.104.2 || true
  echo "GenieACS NBI tunnel 127.0.0.1:7557"
  nc -z 127.0.0.1 7557 && echo "nbi=up" || echo "nbi=down (start-genieacs-tunnel.sh; this env uses :7557 only)"
}

usage() {
  cat <<EOF
Opt-in local-prestaging (single WAR bootRun :8082 /ispadmin). Does not change VPS defaults.

  $0 stop
  $0 start
  $0 core
  $0 restart
  $0 check

stop kills TCP listeners on 8082 (SIGTERM, then SIGKILL if still listening).
start|core|restart free 8082 then ./gradlew :core:bootRun with profiles dev,local-prestaging.
Does not kill listeners outside 8082 (SSH tunnels stay up). Never pkill java/gradle by name.

Requires $SECRETS (gitignored).
EOF
}

cd "$ROOT"
case "$CMD" in
  start|core) run_app ;;
  stop) stop_app ;;
  restart) run_app ;;
  check) check_lab ;;
  *) usage ;;
esac
