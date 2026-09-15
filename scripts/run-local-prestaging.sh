#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties"
EXAMPLE="$ROOT/core/src/main/resources/application-local-prestaging.secrets.properties.example"
OLT_HOST="${OLT_HOST:-10.11.104.2}"
OLT_SSH_PORT="${OLT_SSH_PORT:-22}"
CMD="${1:-help}"

need_secrets() {
  if [[ ! -f "$SECRETS" ]]; then
    echo "Missing $SECRETS" >&2
    echo "Copy $EXAMPLE to that path and fill passwords (or copy from application-local.properties)." >&2
    echo "bootRun will not start without the overlay." >&2
    exit 1
  fi
}

ensure_redis() {
  if nc -z 127.0.0.1 6379 >/dev/null 2>&1; then
    echo "redis=up 127.0.0.1:6379" >&2
    return 0
  fi
  echo "Starting local Redis (docker compose)..." >&2
  "$ROOT/scripts/redis-local.sh"
  local waited=0
  while [[ $waited -lt 40 ]]; do
    if nc -z 127.0.0.1 6379 >/dev/null 2>&1; then
      echo "redis=up 127.0.0.1:6379" >&2
      return 0
    fi
    sleep 0.25
    waited=$((waited + 1))
  done
  echo "Redis no escucha en 127.0.0.1:6379. Arranca: ./scripts/redis-local.sh" >&2
  exit 1
}

secret_prop() {
  local key="$1"
  [[ -f "$SECRETS" ]] || return 0
  grep "^${key}=" "$SECRETS" | cut -d= -f2-
}

inform_notify() {
  need_secrets
  local sn="${1:-ZTEGDC47BFFD}"
  local key="${ACS_API_KEY:-$(secret_prop acs.api-key)}"
  key="${key:-dev-acs-key}"
  echo "POST inform-notify serial=$sn → 127.0.0.1:8082 (NBI cache → Gateway → Redis lpstg:gigafiber.events)" >&2
  curl -sS --fail --max-time 45 -X POST "http://127.0.0.1:8082/ispadmin/api/acs/v1/cpe/inform-notify" \
    -H "Content-Type: application/json" \
    -H "X-Acs-Key: $key" \
    -d "{\"serial\":\"$sn\"}"
  echo
}

# Kill ESTABLISHED TCP clients from this Mac to the lab OLT SSH port (free VTY before Gateway reconnects).
free_olt_ssh() {
  local pids
  pids="$(
    lsof -nP -iTCP -sTCP:ESTABLISHED 2>/dev/null \
      | awk -v host="$OLT_HOST" -v port="$OLT_SSH_PORT" '
          index($0, host ":" port) { print $2 }
        ' \
      | sort -u
  )"
  if [[ -z "$pids" ]]; then
    echo "No ESTABLISHED TCP to ${OLT_HOST}:${OLT_SSH_PORT}" >&2
    return 0
  fi
  echo "Killing preexisting SSH/TCP to ${OLT_HOST}:${OLT_SSH_PORT}: ${pids}" >&2
  local pid
  while read -r pid; do
    [[ -n "$pid" ]] || continue
    kill -TERM "$pid" 2>/dev/null || true
  done <<< "$pids"
  sleep 0.5
  pids="$(
    lsof -nP -iTCP -sTCP:ESTABLISHED 2>/dev/null \
      | awk -v host="$OLT_HOST" -v port="$OLT_SSH_PORT" '
          index($0, host ":" port) { print $2 }
        ' \
      | sort -u
  )"
  if [[ -z "$pids" ]]; then
    return 0
  fi
  echo "Force-killing leftover TCP to ${OLT_HOST}:${OLT_SSH_PORT}: ${pids}" >&2
  while read -r pid; do
    [[ -n "$pid" ]] || continue
    kill -KILL "$pid" 2>/dev/null || true
  done <<< "$pids"
  sleep 0.3
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
  ensure_redis
  # Stop WAR first so free_olt_ssh does not SIGTERM the Java process that owns OLT sessions.
  free_listen_port 8082
  free_olt_ssh
  exec "$ROOT/gradlew" :core:bootRun \
    -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false" \
    --args="--server.port=8082 --server.servlet.context-path=/ispadmin --spring.profiles.active=dev,local-prestaging"
}

check_lab() {
  echo "ping OLT LAN"
  ping -c 1 -W 2 10.11.104.2 || true
  echo "GenieACS NBI tunnel 127.0.0.1:7557"
  nc -z 127.0.0.1 7557 && echo "nbi=up" || echo "nbi=down (start-genieacs-tunnel.sh; this env uses :7557 only)"
  echo "Redis 127.0.0.1:6379"
  nc -z 127.0.0.1 6379 && echo "redis=up" || echo "redis=down (./scripts/redis-local.sh; start lo arranca)"
}

usage() {
  cat <<EOF
Opt-in local-prestaging (single WAR bootRun :8082 /ispadmin). Does not change VPS defaults.

  $0 stop
  $0 start
  $0 core
  $0 restart
  $0 check
  $0 free-olt-ssh
  $0 redis
  $0 inform-notify [serial]

stop kills TCP listeners on 8082 (SIGTERM, then SIGKILL if still listening).
free-olt-ssh kills ESTABLISHED TCP from this Mac to ${OLT_HOST}:${OLT_SSH_PORT} (OLT VTY).
start|core|restart ensure Redis :6379, free 8082, free-olt-ssh, then ./gradlew :core:bootRun with profiles dev,local-prestaging.
redis starts docker-compose.redis.yml if :6379 is down.
inform-notify POSTs to local ACS (same contract as GenieACS ext); Gateway XADD cpe.inform → namespace lpstg.
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
  free-olt-ssh) free_olt_ssh ;;
  redis) ensure_redis ;;
  inform-notify) inform_notify "${2:-ZTEGDC47BFFD}" ;;
  *) usage ;;
esac
