#!/usr/bin/env bash
set -euo pipefail

HOST="${ROUTEROS_MK1_HOST:-38.224.231.2}"
USER="${ROUTEROS_MK1_USER:?ROUTEROS_MK1_USER required}"
PASSWORD="${ROUTEROS_MK1_PASSWORD:?ROUTEROS_MK1_PASSWORD required}"
STORE_PASS="${ROUTEROS_MK1_TRUSTSTORE_PASSWORD:-changeit}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JKS="$ROOT/src/main/resources/routeros-mk-truststore.jks"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

export SSHPASS="$PASSWORD"
sshpass -e ssh -o StrictHostKeyChecking=no "${USER}@${HOST}" \
  '/certificate export-certificate file-name=netdiag-ca.crt netdiag-ca'
sshpass -e scp -O -o StrictHostKeyChecking=no "${USER}@${HOST}:netdiag-ca.crt" "$TMP/netdiag-ca.crt"

rm -f "$JKS"
keytool -importcert \
  -alias mk1-netdiag-ca \
  -file "$TMP/netdiag-ca.crt" \
  -keystore "$JKS" \
  -storepass "$STORE_PASS" \
  -noprompt

echo "Truststore written: $JKS"
keytool -list -keystore "$JKS" -storepass "$STORE_PASS" | head -8
