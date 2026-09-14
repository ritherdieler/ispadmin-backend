#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose -f docker-compose.redis.yml up -d
echo "Redis local en 127.0.0.1:6379 (maxmemory 128mb, AOF)."
