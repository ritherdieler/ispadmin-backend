#!/usr/bin/env bash
set -euo pipefail

HOST="${ROUTEROS_MK2_HOST:-38.224.231.4}"
USER="${ROUTEROS_MK2_USER:?ROUTEROS_MK2_USER required}"
PASSWORD="${ROUTEROS_MK2_PASSWORD:?ROUTEROS_MK2_PASSWORD required}"
STORE_PASS="${ROUTEROS_MK2_TRUSTSTORE_PASSWORD:-changeit}"
ALIAS="${ROUTEROS_MK2_TRUSTSTORE_ALIAS:-mk2-netdiag-ca}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JKS="$ROOT/src/main/resources/routeros-mk-truststore.jks"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

export SSHPASS="$PASSWORD"
sshpass -e ssh -o StrictHostKeyChecking=no "${USER}@${HOST}" \
  '/certificate export-certificate file-name=netdiag-ca.crt netdiag-ca'
sshpass -e scp -O -o StrictHostKeyChecking=no "${USER}@${HOST}:netdiag-ca.crt.crt" "$TMP/netdiag-ca.crt"

if [[ ! -f "$JKS" ]]; then
  keytool -importcert \
    -alias "$ALIAS" \
    -file "$TMP/netdiag-ca.crt" \
    -keystore "$JKS" \
    -storepass "$STORE_PASS" \
    -noprompt
else
  keytool -delete -alias "$ALIAS" -keystore "$JKS" -storepass "$STORE_PASS" -noprompt 2>/dev/null || true
  keytool -importcert \
    -alias "$ALIAS" \
    -file "$TMP/netdiag-ca.crt" \
    -keystore "$JKS" \
    -storepass "$STORE_PASS" \
    -noprompt
fi

echo "Truststore updated: $JKS (alias $ALIAS)"
keytool -list -keystore "$JKS" -storepass "$STORE_PASS"
