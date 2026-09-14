#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.meilisearch.yml"
MEILI_URL="${MEILI_HOST:-http://localhost:7700}"
MEILI_KEY="${MEILI_MASTER_KEY:-masterKeyDevSoloLocal}"

usage() {
  cat <<'EOF'
Usage: ./scripts/meili-local.sh <up|down|status|logs|ui>

  up      Start Meilisearch (Docker)
  down    Stop and remove container (keeps volume)
  status  Health + indexes
  logs    Follow container logs
  ui      Print mini-dashboard URL (open in browser)
EOF
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required" >&2
    exit 1
  fi
}

cmd_up() {
  require_docker
  (cd "$PROJECT_DIR" && docker compose -f "$COMPOSE_FILE" up -d)
  echo "Waiting for Meilisearch..."
  local i
  for i in $(seq 1 30); do
    if curl -sf "$MEILI_URL/health" >/dev/null 2>&1; then
      echo "Meilisearch ready at $MEILI_URL"
      echo "Mini-dashboard: $MEILI_URL"
      echo "Master key: $MEILI_KEY"
      return 0
    fi
    sleep 1
  done
  echo "Meilisearch did not become healthy in time" >&2
  exit 1
}

cmd_down() {
  require_docker
  (cd "$PROJECT_DIR" && docker compose -f "$COMPOSE_FILE" down)
}

cmd_status() {
  echo "GET $MEILI_URL/health"
  curl -sf "$MEILI_URL/health" | python3 -m json.tool 2>/dev/null || curl -sf "$MEILI_URL/health"
  echo
  echo "GET $MEILI_URL/indexes"
  curl -sf "$MEILI_URL/indexes" -H "Authorization: Bearer $MEILI_KEY" | python3 -m json.tool 2>/dev/null \
    || curl -sf "$MEILI_URL/indexes" -H "Authorization: Bearer $MEILI_KEY"
  echo
  echo "GET $MEILI_URL/stats"
  curl -sf "$MEILI_URL/stats" -H "Authorization: Bearer $MEILI_KEY" | python3 -m json.tool 2>/dev/null \
    || curl -sf "$MEILI_URL/stats" -H "Authorization: Bearer $MEILI_KEY"
  echo
}

cmd_logs() {
  require_docker
  (cd "$PROJECT_DIR" && docker compose -f "$COMPOSE_FILE" logs -f)
}

cmd_ui() {
  echo "Mini-dashboard (MEILI_ENV=development): $MEILI_URL"
  echo "API key for the UI if prompted: $MEILI_KEY"
  if command -v open >/dev/null 2>&1; then
    open "$MEILI_URL"
  fi
}

case "${1:-}" in
  up) cmd_up ;;
  down) cmd_down ;;
  status) cmd_status ;;
  logs) cmd_logs ;;
  ui) cmd_ui ;;
  -h|--help|help|"") usage; exit 0 ;;
  *) echo "Unknown command: $1" >&2; usage; exit 1 ;;
esac
