#!/usr/bin/env bash
# setup-nginx-ssl.sh
# Instala Nginx, despliega configuración modular y obtiene certificado SSL con Certbot.
# Idempotente: se puede ejecutar varias veces sin efectos negativos.
#
# Uso: bash scripts/setup-nginx-ssl.sh
# Requiere: ejecutar en el VPS como root (o con sudo), con el dominio ya resolviendo a la IP pública.
#
# Orden de ejecución seguro:
#   1. Instalar Nginx
#   2. Copiar snippets y vhost API
#   3. Deshabilitar vhost default
#   4. nginx -t && systemctl start/reload
#   5. Certbot (reto HTTP-01)
#   6. certbot renew --dry-run
#   DESPUÉS de verificar HTTPS:
#   7. Restringir Tomcat a 127.0.0.1:8080 en docker-compose
#   8. Restringir MySQL a 127.0.0.1:3306 en docker-compose
set -euo pipefail

DOMAIN="api.gigafiberperu.cloud"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NGINX_CONF_DIR="$SCRIPT_DIR/nginx"
NGINX_AVAILABLE="/etc/nginx/sites-available"
NGINX_ENABLED="/etc/nginx/sites-enabled"
NGINX_SNIPPETS="/etc/nginx/snippets"
DOCKER_COMPOSE_FILE="/opt/gigafiber/docker-compose.yml"

info()  { echo "[INFO]  $*"; }
ok()    { echo "[OK]    $*"; }
warn()  { echo "[WARN]  $*"; }
err()   { echo "[ERROR] $*" >&2; exit 1; }

require_root() {
    if [[ $EUID -ne 0 ]]; then
        err "Este script debe ejecutarse como root. Usa: sudo bash $0"
    fi
}

# ─── Paso 1: Instalar Nginx ───────────────────────────────────────────────────
install_nginx() {
    if command -v nginx &>/dev/null; then
        ok "Nginx ya está instalado: $(nginx -v 2>&1)"
        return
    fi
    info "Instalando Nginx..."
    apt-get update -qq
    apt-get install -y nginx
    ok "Nginx instalado."
}

# ─── Paso 2: Copiar snippets ──────────────────────────────────────────────────
deploy_snippets() {
    info "Copiando snippets SSL/proxy a $NGINX_SNIPPETS..."
    mkdir -p "$NGINX_SNIPPETS"
    cp -f "$NGINX_CONF_DIR/snippets/proxy-backend.conf"    "$NGINX_SNIPPETS/"
    cp -f "$NGINX_CONF_DIR/snippets/websocket-backend.conf" "$NGINX_SNIPPETS/"
    cp -f "$NGINX_CONF_DIR/snippets/ssl-params.conf"        "$NGINX_SNIPPETS/"
    ok "Snippets copiados."
}

# ─── Paso 3: Desplegar vhost API ──────────────────────────────────────────────
deploy_api_vhost() {
    info "Desplegando vhost $DOMAIN..."
    cp -f "$NGINX_CONF_DIR/api.gigafiberperu.cloud.conf" "$NGINX_AVAILABLE/$DOMAIN.conf"

    if [[ ! -L "$NGINX_ENABLED/$DOMAIN.conf" ]]; then
        ln -s "$NGINX_AVAILABLE/$DOMAIN.conf" "$NGINX_ENABLED/$DOMAIN.conf"
        info "Vhost habilitado en sites-enabled."
    else
        ok "Vhost ya estaba habilitado."
    fi

    # Copiar plantilla backoffice como referencia (no activar)
    cp -f "$NGINX_CONF_DIR/backoffice.gigafiberperu.cloud.conf.example" \
          "$NGINX_AVAILABLE/backoffice.gigafiberperu.cloud.conf.example"
    info "Plantilla backoffice copiada a $NGINX_AVAILABLE (deshabilitada)."

    # Copiar plantilla observability como referencia (no activar)
    cp -f "$NGINX_CONF_DIR/observability.gigafiberperu.cloud.conf.example" \
          "$NGINX_AVAILABLE/observability.gigafiberperu.cloud.conf.example"
    info "Plantilla observability copiada a $NGINX_AVAILABLE (deshabilitada)."
}

# ─── Paso 4: Deshabilitar vhost default ──────────────────────────────────────
disable_default_vhost() {
    if [[ -L "$NGINX_ENABLED/default" ]]; then
        rm -f "$NGINX_ENABLED/default"
        ok "Vhost default deshabilitado."
    else
        ok "Vhost default ya estaba deshabilitado."
    fi
}

# ─── Paso 5: Verificar y arrancar Nginx ──────────────────────────────────────
start_nginx() {
    info "Verificando configuración Nginx..."
    nginx -t
    systemctl enable nginx
    if systemctl is-active --quiet nginx; then
        systemctl reload nginx
        ok "Nginx recargado."
    else
        systemctl start nginx
        ok "Nginx iniciado."
    fi
}

# ─── Paso 6: Certbot ─────────────────────────────────────────────────────────
install_certbot() {
    if command -v certbot &>/dev/null; then
        ok "Certbot ya está instalado: $(certbot --version 2>&1)"
        return
    fi
    info "Instalando Certbot via snap..."
    snap install --classic certbot
    ln -sf /snap/bin/certbot /usr/local/bin/certbot
    ok "Certbot instalado."
}

obtain_certificate() {
    if [[ -d "/etc/letsencrypt/live/$DOMAIN" ]]; then
        ok "Certificado para $DOMAIN ya existe. Verificando renovación..."
    else
        info "Obteniendo certificado SSL para $DOMAIN (reto HTTP-01)..."
        certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos \
            --email admin@gigafiberperu.cloud --redirect
        ok "Certificado obtenido para $DOMAIN."
    fi

    info "Verificando renovación automática..."
    certbot renew --dry-run
    ok "Renovación automática confirmada."
}

# ─── Paso 7: Restringir puertos en docker-compose (MANUAL — leer antes) ──────
print_port_restriction_instructions() {
    echo ""
    echo "══════════════════════════════════════════════════════════════════════"
    echo "  SIGUIENTE PASO MANUAL (ejecutar solo después de verificar HTTPS):"
    echo ""
    echo "  1. Verificar: curl -I https://$DOMAIN/ispadmin/"
    echo ""
    echo "  2. Editar $DOCKER_COMPOSE_FILE:"
    echo ""
    echo "     Tomcat — cambiar:"
    echo "       ports: [\"8080:8080\"]"
    echo "     por:"
    echo "       ports: [\"127.0.0.1:8080:8080\"]"
    echo ""
    echo "     MySQL — cambiar:"
    echo "       ports: [\"3306:3306\"]"
    echo "     por:"
    echo "       ports: [\"127.0.0.1:3306:3306\"]"
    echo ""
    echo "  3. Reiniciar servicios:"
    echo "       cd /opt/gigafiber && docker compose up -d"
    echo ""
    echo "  4. Confirmar que HTTPS sigue respondiendo y 8080 ya no es accesible:"
    echo "       curl -I https://$DOMAIN/ispadmin/"
    echo "       curl -m 3 http://212.85.13.47:8080/  # debe fallar"
    echo "══════════════════════════════════════════════════════════════════════"
    echo ""
}

# ─── Main ─────────────────────────────────────────────────────────────────────
require_root
install_nginx
deploy_snippets
deploy_api_vhost
disable_default_vhost
start_nginx
install_certbot
obtain_certificate
print_port_restriction_instructions
